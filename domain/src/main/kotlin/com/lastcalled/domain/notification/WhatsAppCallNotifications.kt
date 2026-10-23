package com.lastcalled.domain.notification

import com.lastcalled.domain.logic.CallSourceDetector
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallType

/**
 * The minimum a notification listener extracts from a WhatsApp *call* notification.
 * Built only after the package and category checks pass; message notifications are never read.
 */
data class CallNotificationSnapshot(
    val key: String,
    val packageName: String,
    val category: String?,
    /** Notification.EXTRA_CALL_TYPE when the app uses CallStyle (Android 12+), else null. */
    val callStyleType: Int?,
    val hasFullScreenIntent: Boolean,
    val title: String?,
    val text: String?,
    val postTimeMillis: Long,
)

enum class CallPhase { RINGING, ACTIVE }

data class CallNotificationSignal(
    val key: String,
    val phase: CallPhase,
    val counterpart: String?,
    val callType: CallType,
    val timeMillis: Long,
)

/** A finished WhatsApp call observed through notifications. Duration is approximate. */
data class NotificationCallEvent(
    val counterpart: String?,
    val startedAtMillis: Long,
    val durationSeconds: Long,
    val direction: CallDirection,
    val callType: CallType,
)

/**
 * Interprets WhatsApp call notifications. Best effort by design: WhatsApp can change its
 * notification format at any time, and nothing here is a documented WhatsApp interface.
 */
object WhatsAppCallNotificationParser {
    const val CATEGORY_CALL = "call"

    // Mirrors android.app.Notification.CALL_TYPE_* (API 31).
    const val CALL_STYLE_INCOMING = 1
    const val CALL_STYLE_ONGOING = 2
    const val CALL_STYLE_SCREENING = 3

    /** Cheap pre-check done before reading any notification content. */
    fun isCandidate(packageName: String, category: String?): Boolean =
        packageName in CallSourceDetector.WHATSAPP_PACKAGES && category == CATEGORY_CALL

    fun parse(s: CallNotificationSnapshot): CallNotificationSignal? {
        if (!isCandidate(s.packageName, s.category)) return null
        val phase = when (s.callStyleType) {
            CALL_STYLE_INCOMING, CALL_STYLE_SCREENING -> CallPhase.RINGING
            CALL_STYLE_ONGOING -> CallPhase.ACTIVE
            // Without CallStyle metadata, a full-screen intent is how Android presents a ringing call.
            else -> if (s.hasFullScreenIntent) CallPhase.RINGING else CallPhase.ACTIVE
        }
        return CallNotificationSignal(
            key = s.key,
            phase = phase,
            counterpart = s.title?.trim()?.takeIf { it.isNotEmpty() },
            callType = callTypeFromText(s.text),
            timeMillis = s.postTimeMillis,
        )
    }

    /**
     * Only explicit wording counts. WhatsApp labels calls "voice call" / "video call" in English;
     * other languages yield UNKNOWN rather than a guess.
     */
    fun callTypeFromText(text: String?): CallType {
        val t = text?.lowercase() ?: return CallType.UNKNOWN
        return when {
            "video" in t -> CallType.VIDEO
            "voice" in t || "audio" in t -> CallType.VOICE
            else -> CallType.UNKNOWN
        }
    }
}

/**
 * Turns the stream of posted/removed call notifications into finished call events.
 *
 * - RINGING then removed without ACTIVE → incoming call that was not answered (MISSED; a declined
 *   call looks the same from outside, so it is reported as MISSED).
 * - RINGING then ACTIVE → INCOMING. WhatsApp may replace the ringing notification with a new one
 *   (different key) when the call is answered; an ACTIVE notification for the same counterpart while
 *   a RINGING one is pending is treated as the same incoming call.
 * - ACTIVE without a preceding RINGING → OUTGOING. Duration then includes the time spent dialling,
 *   because the notification does not say when the other side answered.
 *
 * State lives in memory only; a call in progress when the process dies is not recorded.
 */
class CallNotificationTracker {
    private class State(
        var ringingSince: Long?,
        var activeSince: Long?,
        var counterpart: String?,
        var callType: CallType,
    )

    private val calls = HashMap<String, State>()

    @Synchronized
    fun onPosted(signal: CallNotificationSignal) {
        val state = calls.getOrPut(signal.key) { State(null, null, null, CallType.UNKNOWN) }
        signal.counterpart?.let { state.counterpart = it }
        if (signal.callType != CallType.UNKNOWN) state.callType = signal.callType
        when (signal.phase) {
            CallPhase.RINGING ->
                if (state.ringingSince == null && state.activeSince == null) state.ringingSince = signal.timeMillis
            CallPhase.ACTIVE -> if (state.activeSince == null) {
                state.activeSince = signal.timeMillis
                if (state.ringingSince == null) adoptPendingRinging(signal.key, state)
            }
        }
    }

    private fun adoptPendingRinging(activeKey: String, active: State) {
        val match = calls.entries.firstOrNull { (key, other) ->
            key != activeKey && other.activeSince == null && other.ringingSince != null &&
                other.counterpart == active.counterpart
        } ?: return
        active.ringingSince = match.value.ringingSince
        if (active.callType == CallType.UNKNOWN) active.callType = match.value.callType
        calls.remove(match.key)
    }

    @Synchronized
    fun onRemoved(key: String, removedAtMillis: Long): NotificationCallEvent? {
        val state = calls.remove(key) ?: return null
        val ringing = state.ringingSince
        val active = state.activeSince
            ?: return ringing?.let { NotificationCallEvent(state.counterpart, it, 0, CallDirection.MISSED, state.callType) }
        val direction = if (ringing != null) CallDirection.INCOMING else CallDirection.OUTGOING
        val duration = ((removedAtMillis - active) / 1000).coerceAtLeast(0)
        return NotificationCallEvent(state.counterpart, ringing ?: active, duration, direction, state.callType)
    }

    @Synchronized
    fun clear() = calls.clear()
}

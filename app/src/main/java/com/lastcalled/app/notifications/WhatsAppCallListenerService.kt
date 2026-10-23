package com.lastcalled.app.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lastcalled.app.appContainer
import com.lastcalled.domain.notification.CallNotificationSnapshot
import com.lastcalled.domain.notification.CallNotificationTracker
import com.lastcalled.domain.notification.WhatsAppCallNotificationParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Optional, opt-in, best-effort detection of *future* WhatsApp calls.
 *
 * Android only binds this service after the user grants notification access in system settings,
 * and it does nothing unless the in-app setting is also on. It ignores every notification that is
 * not posted by WhatsApp in the "call" category, without reading its content. For call
 * notifications it reads only the title (caller name or number), the CallStyle call type, and the
 * short status text (to tell voice from video). Nothing else is stored.
 */
class WhatsAppCallListenerService : NotificationListenerService() {

    private val tracker = CallNotificationTracker()

    @Volatile
    private var enabled = false
    private var settingsJob: Job? = null

    override fun onListenerConnected() {
        val container = applicationContext.appContainer
        settingsJob?.cancel()
        settingsJob = container.appScope.launch {
            container.settingsRepository.settings
                .map { it.notificationDetectionEnabled }
                .distinctUntilChanged()
                .collect { on ->
                    enabled = on
                    if (!on) {
                        tracker.clear()
                        requestUnbind()
                    }
                }
        }
    }

    override fun onListenerDisconnected() {
        settingsJob?.cancel()
        settingsJob = null
        enabled = false
        tracker.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!enabled) return
        val notification = sbn.notification ?: return
        if (!WhatsAppCallNotificationParser.isCandidate(sbn.packageName, notification.category)) return
        val signal = WhatsAppCallNotificationParser.parse(snapshot(sbn, notification)) ?: return
        tracker.onPosted(signal)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!enabled) return
        if (!WhatsAppCallNotificationParser.isCandidate(sbn.packageName, sbn.notification?.category)) return
        val event = tracker.onRemoved(sbn.key, System.currentTimeMillis()) ?: return
        val container = applicationContext.appContainer
        container.appScope.launch { container.recordNotificationCall(event) }
    }

    private fun snapshot(sbn: StatusBarNotification, n: Notification): CallNotificationSnapshot {
        val extras = n.extras
        val callType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && extras.containsKey(Notification.EXTRA_CALL_TYPE)) {
            extras.getInt(Notification.EXTRA_CALL_TYPE)
        } else {
            null
        }
        return CallNotificationSnapshot(
            key = sbn.key,
            packageName = sbn.packageName,
            category = n.category,
            callStyleType = callType,
            hasFullScreenIntent = n.fullScreenIntent != null,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            postTimeMillis = sbn.postTime,
        )
    }

    companion object {
        fun component(context: Context) = ComponentName(context, WhatsAppCallListenerService::class.java)

        /** Ask the system to (re)bind after the user turns the in-app setting on. */
        fun requestRebind(context: Context) {
            runCatching { NotificationListenerService.requestRebind(component(context)) }
        }
    }
}

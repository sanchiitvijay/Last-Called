package com.lastcalled.domain.logic

import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.logic.SystemCallLogContract as C

/**
 * Determines the source of a call from the PhoneAccount the system logged it against.
 *
 * Only explicit attribution counts:
 *  - The platform telephony stack (package "com.android.phone") → PHONE.
 *  - WhatsApp's own packages → WHATSAPP.
 *  - Everything else (other VoIP apps, missing metadata) → UNKNOWN. A VoIP call is never
 *    assumed to be WhatsApp.
 */
object CallSourceDetector {
    val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    val TELEPHONY_PACKAGES = setOf("com.android.phone")

    fun packageOf(phoneAccountComponent: String?): String? =
        phoneAccountComponent?.substringBefore('/')?.trim()?.takeIf { it.isNotEmpty() }

    fun detect(phoneAccountComponent: String?): CallSource =
        when (packageOf(phoneAccountComponent)) {
            null -> CallSource.UNKNOWN
            in TELEPHONY_PACKAGES -> CallSource.PHONE
            in WHATSAPP_PACKAGES -> CallSource.WHATSAPP
            else -> CallSource.UNKNOWN
        }
}

object CallDirectionMapper {
    /**
     * Maps CallLog.Calls.TYPE.
     *  - BLOCKED is reported as REJECTED: the call reached the device and was refused.
     *  - ANSWERED_EXTERNALLY is INCOMING: it was answered, just on another device.
     *  - VOICEMAIL and unknown values are UNKNOWN.
     */
    fun map(type: Int?): CallDirection = when (type) {
        C.OUTGOING_TYPE -> CallDirection.OUTGOING
        C.INCOMING_TYPE, C.ANSWERED_EXTERNALLY_TYPE -> CallDirection.INCOMING
        C.MISSED_TYPE -> CallDirection.MISSED
        C.REJECTED_TYPE, C.BLOCKED_TYPE -> CallDirection.REJECTED
        else -> CallDirection.UNKNOWN
    }
}

object CallTypeDetector {
    /**
     * VIDEO only when the system set FEATURES_VIDEO. A call is reported as VOICE only when it was
     * logged by Telecom for a known source (where the feature bits are populated by the platform);
     * otherwise the absence of the flag proves nothing, so the type is UNKNOWN.
     */
    fun detect(features: Int?, source: CallSource): CallType = when {
        features == null -> CallType.UNKNOWN
        features and C.FEATURES_VIDEO != 0 -> CallType.VIDEO
        source == CallSource.UNKNOWN -> CallType.UNKNOWN
        else -> CallType.VOICE
    }
}

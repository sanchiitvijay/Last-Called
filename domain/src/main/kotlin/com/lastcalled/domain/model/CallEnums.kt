package com.lastcalled.domain.model

/** Which app or network carried the call. */
enum class CallSource {
    /** Regular cellular / carrier call handled by the platform telephony stack. */
    PHONE,

    /** WhatsApp (or WhatsApp Business), only when the system explicitly attributes the call to it. */
    WHATSAPP,

    /** Anything that cannot be attributed reliably, including other VoIP apps. */
    UNKNOWN,
}

/** Audio vs video. VIDEO is only set when the system says so. */
enum class CallType { VOICE, VIDEO, UNKNOWN }

enum class CallDirection { OUTGOING, INCOMING, MISSED, REJECTED, UNKNOWN }

/** Where a record came from. Never fabricated; each origin is a real system signal. */
enum class RecordOrigin {
    /** Read from Android's CallLog provider. */
    SYSTEM_CALL_LOG,

    /** Observed live from a WhatsApp call notification (optional, best effort, opt-in). */
    WHATSAPP_NOTIFICATION,
}

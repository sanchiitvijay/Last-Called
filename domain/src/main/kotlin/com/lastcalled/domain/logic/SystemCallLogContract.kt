package com.lastcalled.domain.logic

/**
 * Mirror of the public, stable integer constants of android.provider.CallLog.Calls that the
 * mapping logic needs. Duplicated here so the rules stay plain Kotlin and unit-testable; an
 * instrumented test asserts they equal the framework values.
 */
object SystemCallLogContract {
    const val INCOMING_TYPE = 1
    const val OUTGOING_TYPE = 2
    const val MISSED_TYPE = 3
    const val VOICEMAIL_TYPE = 4
    const val REJECTED_TYPE = 5
    const val BLOCKED_TYPE = 6
    const val ANSWERED_EXTERNALLY_TYPE = 7

    const val FEATURES_VIDEO = 1

    const val PRESENTATION_ALLOWED = 1
}

/** One row of the system call log, already pulled out of the Cursor. */
data class RawCallLogEntry(
    val systemId: Long,
    val number: String?,
    val dateMillis: Long,
    val durationSeconds: Long,
    val type: Int?,
    val features: Int?,
    /** Flattened ComponentName of the PhoneAccount (CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME). */
    val phoneAccountComponent: String?,
    val presentation: Int?,
    /** E.164 number supplied by the platform, if any. */
    val platformE164: String?,
)

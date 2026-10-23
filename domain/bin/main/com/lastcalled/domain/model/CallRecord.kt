package com.lastcalled.domain.model

/** A call as stored by the app. Timestamps are epoch milliseconds (UTC, timezone independent). */
data class CallRecord(
    val id: Long = 0,
    val personKey: PersonKey,
    val contactKey: String?,
    val phoneNumber: String?,
    val normalizedNumber: String?,
    /** Name shown in a WhatsApp notification; only set for notification-derived records. */
    val displayNameHint: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val direction: CallDirection,
    val source: CallSource,
    val callType: CallType,
    val origin: RecordOrigin,
    /** CallLog.Calls._ID for records imported from the system call log; null otherwise. */
    val systemCallLogId: Long?,
    /** Package that owns the PhoneAccount the system logged the call against, if known. */
    val sourcePackage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Key used to recognise a system call log row we already imported. */
data class SystemCallKey(val systemCallLogId: Long, val timestamp: Long)

/** The handful of fields needed to describe a person's latest call. */
data class CallSnapshot(
    val timestamp: Long,
    val direction: CallDirection,
    val source: CallSource,
    val callType: CallType,
    val durationSeconds: Long,
)

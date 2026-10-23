package com.lastcalled.domain.logic

import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.RecordOrigin
import com.lastcalled.domain.model.SystemCallKey
import kotlin.math.abs

object CallDeduplicator {
    /** Notification timing is approximate, so a notification event and a CallLog row this close are the same call. */
    const val SAME_CALL_TOLERANCE_MS = 2 * 60 * 1000L

    /**
     * Removes call log rows already stored and duplicates within the batch itself.
     * Identity is (CallLog _ID, date): the ID alone could be reused if the provider is reset.
     */
    fun newSystemRecords(batch: List<CallRecord>, existing: Set<SystemCallKey>): List<CallRecord> {
        val seen = HashSet<SystemCallKey>(existing)
        return batch.filter { record ->
            val id = record.systemCallLogId ?: return@filter true
            seen.add(SystemCallKey(id, record.timestamp))
        }
    }

    /**
     * True when a WhatsApp notification event and a system call log row describe the same call.
     * Only WhatsApp records are ever merged, and only across the two origins.
     */
    fun isSameCall(a: CallRecord, b: CallRecord): Boolean {
        if (a.origin == b.origin) return false
        if (a.source != b.source) return false
        if (a.personKey != b.personKey) return false
        if (abs(a.timestamp - b.timestamp) > SAME_CALL_TOLERANCE_MS) return false
        return directionsCompatible(a.direction, b.direction)
    }

    private fun directionsCompatible(x: CallDirection, y: CallDirection): Boolean {
        if (x == y || x == CallDirection.UNKNOWN || y == CallDirection.UNKNOWN) return true
        val unanswered = setOf(CallDirection.MISSED, CallDirection.REJECTED)
        return x in unanswered && y in unanswered
    }

    /** Notification records that a newly imported system row supersedes (the system row is authoritative). */
    fun supersededNotificationRecords(
        systemRecords: List<CallRecord>,
        notificationRecords: List<CallRecord>,
    ): List<CallRecord> = notificationRecords.filter { n ->
        n.origin == RecordOrigin.WHATSAPP_NOTIFICATION && systemRecords.any { isSameCall(it, n) }
    }
}

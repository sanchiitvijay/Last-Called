package com.lastcalled.domain.logic

import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallRecord

/**
 * Reference implementation of the "last called" rule. The database computes the same thing in SQL
 * for performance; tests verify both agree.
 */
object LastCallCalculator {
    fun latestOutgoing(records: Iterable<CallRecord>): CallRecord? =
        records.filter { it.direction == CallDirection.OUTGOING }.maxByOrNull { it.timestamp }

    fun latestMatching(records: Iterable<CallRecord>, filter: CallFilter): CallRecord? =
        records.filter(filter::matches).maxByOrNull { it.timestamp }
}

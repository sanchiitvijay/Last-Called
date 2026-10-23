package com.lastcalled.app.data.local

import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType

/** Aggregated row for the people list; one per personKey. */
data class PersonSummaryRow(
    val personKey: String,
    val contactName: String?,
    val photoUri: String?,
    val phoneNumber: String?,
    val nameHint: String?,
    val totalCalls: Int,
    val lastTimestamp: Long?,
    val lastDirection: CallDirection?,
    val lastSource: CallSource?,
    val lastCallType: CallType?,
    val lastDuration: Long?,
)

data class RematchRow(
    val id: Long,
    val phoneNumber: String?,
    val normalizedNumber: String?,
    val displayNameHint: String?,
    val personKey: String,
    val contactKey: String?,
)

data class SystemKeyRow(val systemCallLogId: Long, val timestamp: Long)

data class PersonNumberRow(val phoneNumber: String, val calls: Int)

data class CallStats(val total: Int, val whatsApp: Int, val outgoing: Int)

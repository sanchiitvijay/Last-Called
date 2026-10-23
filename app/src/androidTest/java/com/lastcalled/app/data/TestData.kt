package com.lastcalled.app.data

import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.RecordOrigin

object TestData {
    const val NOW = 1_791_000_000_000L
    const val DAY = 86_400_000L
    private var id = 1000L

    fun call(
        person: PersonKey = PersonKey.Contact("rahul"),
        daysAgo: Double = 1.0,
        direction: CallDirection = CallDirection.OUTGOING,
        source: CallSource = CallSource.PHONE,
        type: CallType = CallType.VOICE,
        origin: RecordOrigin = RecordOrigin.SYSTEM_CALL_LOG,
        number: String? = "+919876543210",
        duration: Long = 60,
    ) = CallRecord(
        personKey = person,
        contactKey = (person as? PersonKey.Contact)?.lookupKey,
        phoneNumber = number,
        normalizedNumber = number,
        displayNameHint = null,
        timestamp = NOW - (daysAgo * DAY).toLong(),
        durationSeconds = duration,
        direction = direction,
        source = source,
        callType = type,
        origin = origin,
        systemCallLogId = if (origin == RecordOrigin.SYSTEM_CALL_LOG) id++ else null,
        sourcePackage = null,
        createdAt = NOW,
        updatedAt = NOW,
    )
}

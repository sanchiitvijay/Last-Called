package com.lastcalled.app.data.local

import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.DevicePhoneNumber
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.CallSnapshot
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.PersonSummary
import com.lastcalled.domain.model.TrackedPerson

fun CallRecord.toEntity() = CallRecordEntity(
    id = id,
    personKey = personKey.value,
    contactKey = contactKey,
    phoneNumber = phoneNumber,
    normalizedNumber = normalizedNumber,
    displayNameHint = displayNameHint,
    timestamp = timestamp,
    durationSeconds = durationSeconds,
    direction = direction,
    source = source,
    callType = callType,
    origin = origin,
    systemCallLogId = systemCallLogId,
    sourcePackage = sourcePackage,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CallRecordEntity.toDomain() = CallRecord(
    id = id,
    personKey = PersonKey.parse(personKey) ?: PersonKey.Hidden,
    contactKey = contactKey,
    phoneNumber = phoneNumber,
    normalizedNumber = normalizedNumber,
    displayNameHint = displayNameHint,
    timestamp = timestamp,
    durationSeconds = durationSeconds,
    direction = direction,
    source = source,
    callType = callType,
    origin = origin,
    systemCallLogId = systemCallLogId,
    sourcePackage = sourcePackage,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PersonSummaryRow.toDomain(): PersonSummary? {
    val key = PersonKey.parse(personKey) ?: return null
    val last = if (lastTimestamp != null && lastDirection != null && lastSource != null && lastCallType != null) {
        CallSnapshot(lastTimestamp, lastDirection, lastSource, lastCallType, lastDuration ?: 0)
    } else {
        null
    }
    return PersonSummary(
        key = key,
        contactName = contactName,
        phoneNumber = phoneNumber,
        nameHint = nameHint,
        photoUri = photoUri,
        totalCalls = totalCalls,
        lastMatchingCall = last,
    )
}

fun DeviceContact.toEntities(now: Long): Pair<ContactEntity, List<ContactPhoneNumberEntity>> =
    ContactEntity(lookupKey, deviceContactId, displayName, photoThumbnailUri, now) to
        phoneNumbers.distinctBy { it.number }.map { ContactPhoneNumberEntity(lookupKey, it.number, it.platformE164, it.label) }

fun ContactEntity.toDevice(numbers: List<ContactPhoneNumberEntity>) = DeviceContact(
    lookupKey = lookupKey,
    deviceContactId = deviceContactId,
    displayName = displayName,
    photoThumbnailUri = photoThumbnailUri,
    phoneNumbers = numbers.map { DevicePhoneNumber(it.number, it.e164, it.label) },
)

fun TrackedPerson.toEntity() = TrackedPersonEntity(personKey.value, displayName, phoneNumber, normalizedNumber, addedAt)

fun TrackedPersonEntity.toDomain(): TrackedPerson? =
    PersonKey.parse(personKey)?.let { TrackedPerson(it, displayName, phoneNumber, normalizedNumber, addedAt) }

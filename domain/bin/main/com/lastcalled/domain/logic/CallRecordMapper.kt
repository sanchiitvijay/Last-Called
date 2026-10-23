package com.lastcalled.domain.logic

import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.RecordOrigin

/**
 * Converts a raw system call log row into a [CallRecord]:
 * normalize number → resolve contact → determine source → direction → type.
 */
class CallRecordMapper(private val matcher: ContactMatcher) {

    fun map(entry: RawCallLogEntry, now: Long): CallRecord {
        val withheld = entry.presentation != null &&
            entry.presentation != SystemCallLogContract.PRESENTATION_ALLOWED
        val number = if (withheld) null else PhoneNumberNormalizer.normalize(entry.number, entry.platformE164)
        val resolution = PersonResolver.resolve(number, nameHint = null, matcher = matcher)
        val source = CallSourceDetector.detect(entry.phoneAccountComponent)
        return CallRecord(
            personKey = resolution.personKey,
            contactKey = resolution.contactKey,
            phoneNumber = number?.raw,
            normalizedNumber = number?.canonical,
            displayNameHint = null,
            timestamp = entry.dateMillis,
            durationSeconds = entry.durationSeconds.coerceAtLeast(0),
            direction = CallDirectionMapper.map(entry.type),
            source = source,
            callType = CallTypeDetector.detect(entry.features, source),
            origin = RecordOrigin.SYSTEM_CALL_LOG,
            systemCallLogId = entry.systemId,
            sourcePackage = CallSourceDetector.packageOf(entry.phoneAccountComponent),
            createdAt = now,
            updatedAt = now,
        )
    }
}

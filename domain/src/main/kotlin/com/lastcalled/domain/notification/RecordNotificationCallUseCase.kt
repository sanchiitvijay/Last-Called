package com.lastcalled.domain.notification

import com.lastcalled.domain.logic.CallDeduplicator
import com.lastcalled.domain.logic.ContactMatcher
import com.lastcalled.domain.logic.PersonResolver
import com.lastcalled.domain.logic.PhoneNumberNormalizer
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.RecordOrigin
import com.lastcalled.domain.sync.CallStore
import com.lastcalled.domain.sync.ContactsSource
import com.lastcalled.domain.sync.SourceResult

/**
 * Stores a WhatsApp call observed through its notification, unless the system call log already
 * holds the same call. The notification gives a display name (or number), not a contact ID, so the
 * contact is matched by exact name, or by number when WhatsApp showed a number.
 */
class RecordNotificationCallUseCase(
    private val contacts: ContactsSource,
    private val store: CallStore,
    private val clock: () -> Long,
) {
    /** @return true if a record was stored. */
    suspend operator fun invoke(event: NotificationCallEvent): Boolean {
        val contactList = (contacts.readContacts() as? SourceResult.Available)?.data ?: store.storedContacts()
        val matcher = ContactMatcher(contactList)

        val counterpart = event.counterpart
        val number = counterpart
            ?.takeIf { PhoneNumberNormalizer.looksLikePhoneNumber(it) }
            ?.let { PhoneNumberNormalizer.normalize(it) }
        val nameHint = if (number == null) counterpart else null
        val resolution = PersonResolver.resolve(number, nameHint, matcher)

        val now = clock()
        val record = CallRecord(
            personKey = resolution.personKey,
            contactKey = resolution.contactKey,
            phoneNumber = number?.raw,
            normalizedNumber = number?.canonical,
            displayNameHint = nameHint,
            timestamp = event.startedAtMillis,
            durationSeconds = event.durationSeconds,
            direction = event.direction,
            source = CallSource.WHATSAPP,
            callType = event.callType,
            origin = RecordOrigin.WHATSAPP_NOTIFICATION,
            systemCallLogId = null,
            sourcePackage = null,
            createdAt = now,
            updatedAt = now,
        )

        val window = CallDeduplicator.SAME_CALL_TOLERANCE_MS
        val existing = store.whatsAppSystemRecordsBetween(record.timestamp - window, record.timestamp + window) +
            store.notificationRecordsBetween(record.timestamp - window, record.timestamp + window)
        val duplicate = existing.any {
            CallDeduplicator.isSameCall(it, record) ||
                (it.origin == record.origin && it.personKey == record.personKey && it.timestamp == record.timestamp)
        }
        if (duplicate) return false

        val contact = resolution.contactKey?.let { key -> contactList.firstOrNull { it.lookupKey == key } }
        store.insertRecord(record, contact)
        return true
    }
}

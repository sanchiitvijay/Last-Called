package com.lastcalled.domain.sync

import com.lastcalled.domain.logic.CallDeduplicator
import com.lastcalled.domain.logic.CallRecordMapper
import com.lastcalled.domain.logic.ContactMatcher
import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.PersonResolver
import com.lastcalled.domain.logic.PhoneNumberNormalizer
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.PersonKey

data class SyncResult(
    val callLogAvailable: Boolean,
    val contactsAvailable: Boolean,
    val callLogRowsRead: Int,
    val inserted: Int,
    val alreadyImported: Int,
    val remapped: Int,
    val notificationRecordsMerged: Int,
    val whatsAppRowsRead: Int,
    val voipRowsRequested: Boolean,
    val failure: String?,
)

/**
 * The import pipeline:
 *
 * read system call log → normalize numbers → resolve contacts → determine source / direction / type
 * → deduplicate → store → re-resolve existing records (contacts may have been added, renamed or
 * deleted) → store. "Last called" values are then derived by queries over the stored records.
 *
 * Never deletes imported history: rows the system has since dropped (Android caps its call log)
 * stay in the app's database. The only deletions are notification-derived WhatsApp records that a
 * system call log row now covers.
 */
class SyncCallHistoryUseCase(
    private val callLog: SystemCallLogSource,
    private val contacts: ContactsSource,
    private val store: CallStore,
    private val clock: () -> Long,
) {
    suspend operator fun invoke(): SyncResult {
        val now = clock()

        val contactsResult = contacts.readContacts()
        val freshContacts: List<DeviceContact>? = (contactsResult as? SourceResult.Available)?.data
        // Without contacts access, keep matching against the last known snapshot rather than
        // forgetting every name the user already saw.
        val matcher = ContactMatcher(freshContacts ?: store.storedContacts())

        val callLogResult = callLog.readCallLog()
        val snapshot = (callLogResult as? SourceResult.Available)?.data
        val entries = snapshot?.entries.orEmpty()

        val mapper = CallRecordMapper(matcher)
        val mapped = entries.map { mapper.map(it, now) }
        val newRecords = CallDeduplicator.newSystemRecords(mapped, store.existingSystemKeys())

        // A system WhatsApp row supersedes a notification-derived record of the same call.
        val newWhatsApp = newRecords.filter { it.source == CallSource.WHATSAPP }
        val superseded = if (newWhatsApp.isEmpty()) {
            emptyList()
        } else {
            val from = newWhatsApp.minOf { it.timestamp } - CallDeduplicator.SAME_CALL_TOLERANCE_MS
            val to = newWhatsApp.maxOf { it.timestamp } + CallDeduplicator.SAME_CALL_TOLERANCE_MS
            CallDeduplicator.supersededNotificationRecords(newWhatsApp, store.notificationRecordsBetween(from, to))
        }
        val supersededIds = superseded.map { it.id }.toSet()

        // Re-resolve existing records only when we have a fresh contacts snapshot.
        val candidates = if (freshContacts != null) {
            store.rematchCandidates().filter { it.id !in supersededIds }
        } else {
            emptyList()
        }
        val updates = candidates.mapNotNull { c ->
            val platformE164 = c.normalizedNumber?.takeIf { it.startsWith("+") }
            val number = PhoneNumberNormalizer.normalize(c.phoneNumber, platformE164)
            val r = PersonResolver.resolve(number, c.displayNameHint, matcher)
            if (r.personKey != c.personKey || r.contactKey != c.contactKey) {
                PersonUpdate(c.id, r.personKey, r.contactKey)
            } else {
                null
            }
        }

        // Watch-list entries follow their person: re-resolve by number (or name) like call records.
        val trackedUpdates = if (freshContacts != null) {
            store.trackedPeople().mapNotNull { t ->
                val number = PhoneNumberNormalizer.normalize(t.phoneNumber, t.normalizedNumber?.takeIf { it.startsWith("+") })
                val nameHint = (t.personKey as? PersonKey.Name)?.displayName
                if (number == null && nameHint == null) return@mapNotNull null
                val r = PersonResolver.resolve(number, nameHint, matcher)
                val name = r.contactKey?.let { key -> freshContacts.firstOrNull { it.lookupKey == key }?.displayName } ?: t.displayName
                if (r.personKey != t.personKey || name != t.displayName) {
                    TrackedUpdate(t.personKey, t.copy(personKey = r.personKey, displayName = name))
                } else {
                    null
                }
            }
        } else {
            emptyList()
        }
        val trackedContactKeys = if (freshContacts != null) {
            val updatedKeys = trackedUpdates.associate { it.oldKey to it.updated.personKey }
            store.trackedPeople().mapNotNull { t -> ((updatedKeys[t.personKey] ?: t.personKey) as? PersonKey.Contact)?.lookupKey }
        } else {
            emptyList()
        }

        // Store only contacts that some call (or watch-list entry) belongs to: the app never copies
        // the whole address book.
        val contactsToStore = freshContacts?.let { all ->
            val updated = updates.associateBy { it.recordId }
            val referenced = buildSet {
                newRecords.mapNotNullTo(this) { it.contactKey }
                addAll(trackedContactKeys)
                candidates.forEach { c ->
                    val key = if (c.id in updated) updated.getValue(c.id).contactKey else c.contactKey
                    if (key != null) add(key)
                }
            }
            all.filter { it.lookupKey in referenced }
        }

        store.applySync(
            SyncPlan(
                newRecords = newRecords,
                personUpdates = updates,
                contacts = contactsToStore,
                recordIdsToDelete = supersededIds.toList(),
                trackedUpdates = trackedUpdates,
                syncedAt = now,
                voipRowsRequested = snapshot?.voipRowsRequested ?: false,
            ),
        )

        return SyncResult(
            callLogAvailable = snapshot != null,
            contactsAvailable = freshContacts != null,
            callLogRowsRead = entries.size,
            inserted = newRecords.size,
            alreadyImported = mapped.size - newRecords.size,
            remapped = updates.size,
            notificationRecordsMerged = supersededIds.size,
            whatsAppRowsRead = mapped.count { it.source == CallSource.WHATSAPP },
            voipRowsRequested = snapshot?.voipRowsRequested ?: false,
            failure = (callLogResult as? SourceResult.Failed)?.reason
                ?: (contactsResult as? SourceResult.Failed)?.reason,
        )
    }
}

package com.lastcalled.domain

import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.RecordOrigin
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.SystemCallKey
import com.lastcalled.domain.model.TrackedPerson
import com.lastcalled.domain.sync.CallStore
import com.lastcalled.domain.sync.RematchCandidate
import com.lastcalled.domain.sync.SyncPlan

/** Test double with the same semantics as the Room store (unique system keys, contacts replaced). */
class InMemoryCallStore : CallStore {
    val records = mutableListOf<CallRecord>()
    var contacts = listOf<DeviceContact>()
    val tracked = linkedMapOf<PersonKey, TrackedPerson>()
    private var nextId = 1L

    override suspend fun existingSystemKeys() =
        records.mapNotNull { r -> r.systemCallLogId?.let { SystemCallKey(it, r.timestamp) } }.toSet()

    override suspend fun rematchCandidates() =
        records.map { RematchCandidate(it.id, it.phoneNumber, it.normalizedNumber, it.displayNameHint, it.personKey, it.contactKey) }

    override suspend fun storedContacts() = contacts

    override suspend fun notificationRecordsBetween(fromMillis: Long, toMillis: Long) =
        records.filter { it.origin == RecordOrigin.WHATSAPP_NOTIFICATION && it.timestamp in fromMillis..toMillis }

    override suspend fun whatsAppSystemRecordsBetween(fromMillis: Long, toMillis: Long) =
        records.filter { it.origin == RecordOrigin.SYSTEM_CALL_LOG && it.source == CallSource.WHATSAPP && it.timestamp in fromMillis..toMillis }

    override suspend fun applySync(plan: SyncPlan) {
        plan.contacts?.let { contacts = it }
        records.removeAll { it.id in plan.recordIdsToDelete }
        val keys = existingSystemKeys().toMutableSet()
        plan.newRecords.forEach { r ->
            val key = r.systemCallLogId?.let { SystemCallKey(it, r.timestamp) }
            if (key == null || keys.add(key)) records += r.copy(id = nextId++)
        }
        plan.trackedUpdates.forEach { u ->
            tracked.remove(u.oldKey)
            tracked[u.updated.personKey] = u.updated
        }
        plan.personUpdates.forEach { u ->
            val i = records.indexOfFirst { it.id == u.recordId }
            records[i] = records[i].copy(personKey = u.personKey, contactKey = u.contactKey)
        }
    }

    override suspend fun insertRecord(record: CallRecord, contact: DeviceContact?) {
        contact?.let { c -> contacts = contacts.filter { it.lookupKey != c.lookupKey } + c }
        records += record.copy(id = nextId++)
    }

    override suspend fun trackedPeople() = tracked.values.toList()

    override suspend fun upsertTracked(person: TrackedPerson, contact: DeviceContact?) {
        contact?.let { c -> contacts = contacts.filter { it.lookupKey != c.lookupKey } + c }
        tracked[person.personKey] = person
    }

    override suspend fun removeTracked(key: PersonKey) {
        tracked.remove(key)
    }
}

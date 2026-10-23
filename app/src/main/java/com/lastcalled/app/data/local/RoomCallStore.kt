package com.lastcalled.app.data.local

import androidx.room.withTransaction
import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.RecordOrigin
import com.lastcalled.domain.model.SystemCallKey
import com.lastcalled.domain.model.TrackedPerson
import com.lastcalled.domain.sync.CallStore
import com.lastcalled.domain.sync.RematchCandidate
import com.lastcalled.domain.sync.SyncPlan

/** Room-backed implementation of the domain's [CallStore]. */
class RoomCallStore(private val db: AppDatabase) : CallStore {
    private val dao = db.callDao()
    private val settings = db.settingsDao()
    private val tracked = db.trackedDao()

    override suspend fun existingSystemKeys(): Set<SystemCallKey> =
        dao.systemKeys().mapTo(HashSet()) { SystemCallKey(it.systemCallLogId, it.timestamp) }

    override suspend fun rematchCandidates(): List<RematchCandidate> = dao.rematchRows().mapNotNull { row ->
        val key = PersonKey.parse(row.personKey) ?: return@mapNotNull null
        RematchCandidate(row.id, row.phoneNumber, row.normalizedNumber, row.displayNameHint, key, row.contactKey)
    }

    override suspend fun storedContacts(): List<DeviceContact> {
        val numbers = dao.allContactNumbers().groupBy { it.lookupKey }
        return dao.allContacts().map { it.toDevice(numbers[it.lookupKey].orEmpty()) }
    }

    override suspend fun notificationRecordsBetween(fromMillis: Long, toMillis: Long): List<CallRecord> =
        dao.recordsBetween(RecordOrigin.WHATSAPP_NOTIFICATION.name, CallSource.WHATSAPP.name, fromMillis, toMillis)
            .map { it.toDomain() }

    override suspend fun whatsAppSystemRecordsBetween(fromMillis: Long, toMillis: Long): List<CallRecord> =
        dao.recordsBetween(RecordOrigin.SYSTEM_CALL_LOG.name, CallSource.WHATSAPP.name, fromMillis, toMillis)
            .map { it.toDomain() }

    override suspend fun applySync(plan: SyncPlan) {
        db.withTransaction {
            plan.contacts?.let { contacts ->
                val entities = contacts.map { it.toEntities(plan.syncedAt) }
                dao.replaceContacts(entities.map { it.first }, entities.flatMap { it.second })
            }
            if (plan.recordIdsToDelete.isNotEmpty()) {
                plan.recordIdsToDelete.chunked(500).forEach { dao.deleteByIds(it) }
            }
            plan.newRecords.chunked(500).forEach { chunk -> dao.insertAll(chunk.map { it.toEntity() }) }
            plan.personUpdates.forEach { dao.updatePerson(it.recordId, it.personKey.value, it.contactKey, plan.syncedAt) }
            plan.trackedUpdates.forEach {
                tracked.delete(it.oldKey.value)
                tracked.upsert(it.updated.toEntity())
            }
            val current = settings.get() ?: AppSettingsEntity()
            settings.upsert(current.copy(lastSyncAt = plan.syncedAt, lastSyncRequestedVoip = plan.voipRowsRequested))
        }
    }

    override suspend fun insertRecord(record: CallRecord, contact: DeviceContact?) {
        db.withTransaction {
            contact?.let {
                val (entity, numbers) = it.toEntities(record.createdAt)
                dao.upsertContact(entity, numbers)
            }
            dao.insertAll(listOf(record.toEntity()))
        }
    }

    override suspend fun trackedPeople(): List<TrackedPerson> = tracked.all().mapNotNull { it.toDomain() }

    override suspend fun upsertTracked(person: TrackedPerson, contact: DeviceContact?) {
        db.withTransaction {
            contact?.let {
                val (entity, numbers) = it.toEntities(person.addedAt)
                dao.upsertContact(entity, numbers)
            }
            tracked.upsert(person.toEntity())
        }
    }

    override suspend fun removeTracked(key: PersonKey) = tracked.delete(key.value)
}

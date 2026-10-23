package com.lastcalled.domain.sync

import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.RawCallLogEntry
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.SystemCallKey
import com.lastcalled.domain.model.TrackedPerson

/** Result of reading an Android system data source. */
sealed interface SourceResult<out T> {
    data class Available<T>(val data: T) : SourceResult<T>
    data object PermissionMissing : SourceResult<Nothing>

    /** The provider threw or returned nothing usable. [reason] never contains personal data. */
    data class Failed(val reason: String) : SourceResult<Nothing>
}

data class CallLogSnapshot(
    val entries: List<RawCallLogEntry>,
    /** True if the query asked the platform to include VoIP rows (Android 16.1+ unified call history). */
    val voipRowsRequested: Boolean,
)

interface SystemCallLogSource {
    suspend fun readCallLog(): SourceResult<CallLogSnapshot>
}

interface ContactsSource {
    /** Contacts that have at least one phone number. */
    suspend fun readContacts(): SourceResult<List<DeviceContact>>
}

/** Fields needed to re-resolve which person an existing record belongs to. */
data class RematchCandidate(
    val id: Long,
    val phoneNumber: String?,
    val normalizedNumber: String?,
    val displayNameHint: String?,
    val personKey: PersonKey,
    val contactKey: String?,
)

data class PersonUpdate(val recordId: Long, val personKey: PersonKey, val contactKey: String?)

/** A watch-list entry whose person changed (contact added, renamed or deleted). */
data class TrackedUpdate(val oldKey: PersonKey, val updated: TrackedPerson)

/** Everything one sync writes, applied atomically by [CallStore.applySync]. */
data class SyncPlan(
    val newRecords: List<CallRecord>,
    val personUpdates: List<PersonUpdate>,
    /** Contacts to keep stored (replacing the stored set), or null to leave stored contacts untouched. */
    val contacts: List<DeviceContact>?,
    val recordIdsToDelete: List<Long>,
    val trackedUpdates: List<TrackedUpdate> = emptyList(),
    val syncedAt: Long,
    val voipRowsRequested: Boolean,
)

/** Local persistence used by the sync pipeline. Implemented with Room in the app module. */
interface CallStore {
    suspend fun existingSystemKeys(): Set<SystemCallKey>
    suspend fun rematchCandidates(): List<RematchCandidate>
    suspend fun storedContacts(): List<DeviceContact>
    suspend fun notificationRecordsBetween(fromMillis: Long, toMillis: Long): List<CallRecord>
    suspend fun whatsAppSystemRecordsBetween(fromMillis: Long, toMillis: Long): List<CallRecord>
    suspend fun applySync(plan: SyncPlan)
    /** Inserts one record, storing [contact] too when the record was matched to it. */
    suspend fun insertRecord(record: CallRecord, contact: DeviceContact?)

    suspend fun trackedPeople(): List<TrackedPerson>

    /** Adds or replaces a watch-list entry, storing [contact] so the name can be shown before any call. */
    suspend fun upsertTracked(person: TrackedPerson, contact: DeviceContact?)
    suspend fun removeTracked(key: PersonKey)
}

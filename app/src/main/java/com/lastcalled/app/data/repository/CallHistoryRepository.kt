package com.lastcalled.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.lastcalled.app.data.local.AppDatabase
import com.lastcalled.app.data.local.CallStats
import com.lastcalled.app.data.local.toDomain
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.PersonSummary
import com.lastcalled.domain.model.TrackedPerson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class LabeledNumber(val number: String, val label: String?)

data class PersonDetails(
    val key: PersonKey,
    /** Current contact name; null for unknown numbers. */
    val contactName: String?,
    /** Name seen in a WhatsApp notification, for records without a number. */
    val nameHint: String?,
    val photoUri: String?,
    val numbers: List<LabeledNumber>,
    val latestOutgoing: CallRecord?,
    val latestAny: CallRecord?,
)

/** Read side of the app: everything the UI shows comes from these Room queries. */
class CallHistoryRepository(private val db: AppDatabase) {
    private val dao = db.callDao()

    fun observePeople(filter: CallFilter): Flow<List<PersonSummary>> {
        val p = FilterParams(filter)
        return dao.observePersonSummaries(
            p.anySource, p.sources, p.anyDirection, p.directions, p.anyType, p.types,
        ).map { rows -> rows.mapNotNull { it.toDomain() } }
    }

    fun observeStats(): Flow<CallStats> = dao.observeStats()

    /** The user's "keep in touch" list, oldest addition first. */
    fun observeTracked(): Flow<List<TrackedPerson>> =
        db.trackedDao().observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    fun observePerson(key: PersonKey): Flow<PersonDetails> {
        val k = key.value
        val contactFlow = if (key is PersonKey.Contact) dao.observeContact(key.lookupKey) else flowOf(null)
        val contactNumbersFlow =
            if (key is PersonKey.Contact) dao.observeContactNumbers(key.lookupKey) else flowOf(emptyList())
        val numbersFlow = combine(contactNumbersFlow, dao.observeNumbersForPerson(k)) { contactNumbers, callNumbers ->
            if (contactNumbers.isNotEmpty()) {
                contactNumbers.map { LabeledNumber(it.number, it.label) }
            } else {
                callNumbers.map { LabeledNumber(it.phoneNumber, null) }
            }
        }
        return combine(
            contactFlow,
            numbersFlow,
            dao.observeLatestOutgoing(k),
            dao.observeLatestAny(k),
            dao.observeNameHint(k),
        ) { contact, numbers, outgoing, any, hint ->
            PersonDetails(
                key = key,
                contactName = contact?.displayName,
                nameHint = hint,
                photoUri = contact?.photoThumbnailUri,
                numbers = numbers,
                latestOutgoing = outgoing?.toDomain(),
                latestAny = any?.toDomain(),
            )
        }
    }

    fun pagedHistory(key: PersonKey, filter: CallFilter): Flow<PagingData<CallRecord>> {
        val p = FilterParams(filter)
        return Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) {
            dao.pagedHistory(key.value, p.anySource, p.sources, p.anyDirection, p.directions, p.anyType, p.types)
        }.flow.map { page -> page.map { it.toDomain() } }
    }

    /** Deletes the watch list (used by "Clear app data"). */
    suspend fun clearTracked() = db.trackedDao().deleteAll()

    /** Deletes imported call records and stored contacts. Settings and the watch list are kept. */
    suspend fun resetImportedRecords() {
        db.withTransaction {
            dao.deleteAllRecords()
            dao.deleteAllContacts()
        }
    }

    private class FilterParams(filter: CallFilter) {
        val anySource = filter.sources.isEmpty()
        val sources = filter.sources.map { it.name }
        val anyDirection = filter.directions.isEmpty()
        val directions = filter.directions.map { it.name }
        val anyType = filter.types.isEmpty()
        val types = filter.types.map { it.name }
    }
}

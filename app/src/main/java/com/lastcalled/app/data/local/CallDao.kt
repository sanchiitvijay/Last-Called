package com.lastcalled.app.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Filter parameters follow one pattern: `(:anyX OR column IN (:xs))`. When `anyX` is true the
 * dimension is unrestricted. Enum values are passed by name, matching [Converters].
 */
@Dao
interface CallDao {

    /**
     * One row per person: total calls, plus the latest call matching the filter. SQLite returns the
     * other columns of the row holding MAX(timestamp) ("bare columns" with a single MAX aggregate).
     */
    @Query(
        """
        SELECT p.personKey AS personKey,
               c.displayName AS contactName,
               c.photoThumbnailUri AS photoUri,
               p.phoneNumber AS phoneNumber,
               p.displayNameHint AS nameHint,
               p.totalCalls AS totalCalls,
               m.lastTimestamp AS lastTimestamp,
               m.direction AS lastDirection,
               m.source AS lastSource,
               m.callType AS lastCallType,
               m.durationSeconds AS lastDuration
        FROM (
            SELECT personKey, contactKey, phoneNumber, displayNameHint, MAX(timestamp) AS latest, COUNT(*) AS totalCalls
            FROM call_records GROUP BY personKey
        ) p
        LEFT JOIN (
            SELECT personKey, MAX(timestamp) AS lastTimestamp, direction, source, callType, durationSeconds
            FROM call_records
            WHERE (:anySource OR source IN (:sources))
              AND (:anyDirection OR direction IN (:directions))
              AND (:anyType OR callType IN (:types))
            GROUP BY personKey
        ) m ON m.personKey = p.personKey
        LEFT JOIN contacts c ON c.lookupKey = p.contactKey
        """,
    )
    fun observePersonSummaries(
        anySource: Boolean, sources: List<String>,
        anyDirection: Boolean, directions: List<String>,
        anyType: Boolean, types: List<String>,
    ): Flow<List<PersonSummaryRow>>

    @Query(
        """
        SELECT * FROM call_records
        WHERE personKey = :personKey
          AND (:anySource OR source IN (:sources))
          AND (:anyDirection OR direction IN (:directions))
          AND (:anyType OR callType IN (:types))
        ORDER BY timestamp DESC, id DESC
        """,
    )
    fun pagedHistory(
        personKey: String,
        anySource: Boolean, sources: List<String>,
        anyDirection: Boolean, directions: List<String>,
        anyType: Boolean, types: List<String>,
    ): PagingSource<Int, CallRecordEntity>

    @Query(
        """
        SELECT * FROM call_records
        WHERE personKey = :personKey AND direction = 'OUTGOING'
        ORDER BY timestamp DESC, id DESC LIMIT 1
        """,
    )
    fun observeLatestOutgoing(personKey: String): Flow<CallRecordEntity?>

    @Query("SELECT * FROM call_records WHERE personKey = :personKey ORDER BY timestamp DESC, id DESC LIMIT 1")
    fun observeLatestAny(personKey: String): Flow<CallRecordEntity?>

    @Query(
        """
        SELECT phoneNumber, COUNT(*) AS calls FROM call_records
        WHERE personKey = :personKey AND phoneNumber IS NOT NULL
        GROUP BY normalizedNumber ORDER BY calls DESC
        """,
    )
    fun observeNumbersForPerson(personKey: String): Flow<List<PersonNumberRow>>

    @Query("SELECT displayNameHint FROM call_records WHERE personKey = :personKey AND displayNameHint IS NOT NULL LIMIT 1")
    fun observeNameHint(personKey: String): Flow<String?>

    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN source = 'WHATSAPP' THEN 1 ELSE 0 END), 0) AS whatsApp,
               COALESCE(SUM(CASE WHEN direction = 'OUTGOING' THEN 1 ELSE 0 END), 0) AS outgoing
        FROM call_records
        """,
    )
    fun observeStats(): Flow<CallStats>

    @Query("SELECT systemCallLogId, timestamp FROM call_records WHERE systemCallLogId IS NOT NULL")
    suspend fun systemKeys(): List<SystemKeyRow>

    @Query("SELECT id, phoneNumber, normalizedNumber, displayNameHint, personKey, contactKey FROM call_records")
    suspend fun rematchRows(): List<RematchRow>

    @Query("SELECT * FROM call_records WHERE origin = :origin AND source = :source AND timestamp BETWEEN :from AND :to")
    suspend fun recordsBetween(origin: String, source: String, from: Long, to: Long): List<CallRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<CallRecordEntity>): List<Long>

    @Query("UPDATE call_records SET personKey = :personKey, contactKey = :contactKey, updatedAt = :now WHERE id = :id")
    suspend fun updatePerson(id: Long, personKey: String, contactKey: String?, now: Long)

    @Query("DELETE FROM call_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM call_records")
    suspend fun deleteAllRecords()

    // Contacts

    @Query("SELECT * FROM contacts WHERE lookupKey = :lookupKey")
    fun observeContact(lookupKey: String): Flow<ContactEntity?>

    @Query("SELECT * FROM contact_phone_numbers WHERE lookupKey = :lookupKey")
    fun observeContactNumbers(lookupKey: String): Flow<List<ContactPhoneNumberEntity>>

    @Query("SELECT * FROM contacts")
    suspend fun allContacts(): List<ContactEntity>

    @Query("SELECT * FROM contact_phone_numbers")
    suspend fun allContactNumbers(): List<ContactPhoneNumberEntity>

    @Upsert
    suspend fun upsertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contact_phone_numbers WHERE lookupKey IN (:lookupKeys)")
    suspend fun deleteNumbersFor(lookupKeys: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNumbers(numbers: List<ContactPhoneNumberEntity>)

    @Query("DELETE FROM contacts WHERE lookupKey NOT IN (:keep)")
    suspend fun deleteContactsExcept(keep: List<String>)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()

    /** Replaces the stored contact snapshot with [contacts]. */
    @Transaction
    suspend fun replaceContacts(contacts: List<ContactEntity>, numbers: List<ContactPhoneNumberEntity>) {
        val keys = contacts.map { it.lookupKey }
        if (keys.isEmpty()) deleteAllContacts() else deleteContactsExcept(keys)
        upsertContacts(contacts)
        keys.chunked(500).forEach { deleteNumbersFor(it) }
        insertNumbers(numbers)
    }

    @Transaction
    suspend fun upsertContact(contact: ContactEntity, numbers: List<ContactPhoneNumberEntity>) {
        upsertContacts(listOf(contact))
        deleteNumbersFor(listOf(contact.lookupKey))
        insertNumbers(numbers)
    }
}

@Dao
interface TrackedDao {
    @Query("SELECT * FROM tracked_people ORDER BY addedAt")
    fun observeAll(): Flow<List<TrackedPersonEntity>>

    @Query("SELECT * FROM tracked_people ORDER BY addedAt")
    suspend fun all(): List<TrackedPersonEntity>

    @Upsert
    suspend fun upsert(person: TrackedPersonEntity)

    @Query("DELETE FROM tracked_people WHERE personKey = :personKey")
    suspend fun delete(personKey: String)

    @Query("DELETE FROM tracked_people")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun get(): AppSettingsEntity?

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)

    @Query("DELETE FROM app_settings")
    suspend fun clear()
}

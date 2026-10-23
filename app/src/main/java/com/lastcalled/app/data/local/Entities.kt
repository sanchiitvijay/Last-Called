package com.lastcalled.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.RecordOrigin

/**
 * A device contact that at least one stored call belongs to. Refreshed on every sync, so renamed
 * contacts show their current name. The app never copies contacts that have no calls.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    /** ContactsContract.Contacts.LOOKUP_KEY: the stable reference to a contact. */
    @PrimaryKey val lookupKey: String,
    val deviceContactId: Long,
    val displayName: String,
    val photoThumbnailUri: String?,
    val updatedAt: Long,
)

@Entity(
    tableName = "contact_phone_numbers",
    primaryKeys = ["lookupKey", "number"],
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["lookupKey"],
            childColumns = ["lookupKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ContactPhoneNumberEntity(
    val lookupKey: String,
    val number: String,
    val e164: String?,
    val label: String?,
)

@Entity(
    tableName = "call_records",
    indices = [
        Index(value = ["personKey", "timestamp"]),
        Index(value = ["timestamp"]),
        Index(value = ["contactKey"]),
        // Imported system rows are unique by (CallLog _ID, date). NULLs (notification records) do not collide.
        Index(value = ["systemCallLogId", "timestamp"], unique = true),
    ],
)
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Grouping key, see [com.lastcalled.domain.model.PersonKey]. */
    val personKey: String,
    /** Lookup key of the matched contact (logical reference to [ContactEntity]); null if unknown. */
    val contactKey: String?,
    val phoneNumber: String?,
    val normalizedNumber: String?,
    val displayNameHint: String?,
    /** Epoch milliseconds, UTC. */
    val timestamp: Long,
    val durationSeconds: Long,
    val direction: CallDirection,
    val source: CallSource,
    val callType: CallType,
    val origin: RecordOrigin,
    val systemCallLogId: Long?,
    val sourcePackage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * The user's "keep in touch" list. Only these people appear in the "People not called recently"
 * dashboard. [personKey] uses the same format as [CallRecordEntity.personKey]; the number lets the
 * entry follow the person when contacts change.
 */
@Entity(tableName = "tracked_people")
data class TrackedPersonEntity(
    @PrimaryKey val personKey: String,
    val displayName: String?,
    val phoneNumber: String?,
    val normalizedNumber: String?,
    val addedAt: Long,
)

/** Single-row table of user preferences and sync bookkeeping. */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val onboardingCompleted: Boolean = false,
    val defaultThresholdDays: Int = 7,
    val notificationDetectionEnabled: Boolean = false,
    /** Whether the runtime permission dialog was ever shown; distinguishes "never asked" from "permanently denied". */
    val callLogPermissionRequested: Boolean = false,
    val contactsPermissionRequested: Boolean = false,
    val lastSyncAt: Long? = null,
    val lastSyncRequestedVoip: Boolean = false,
    @ColumnInfo(defaultValue = "RECENTLY_CALLED") val sortOrder: String = "RECENTLY_CALLED",
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

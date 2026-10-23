package com.lastcalled.app.data.repository

import com.lastcalled.app.data.local.AppSettingsEntity
import com.lastcalled.app.data.local.SettingsDao
import com.lastcalled.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppSettings(
    val onboardingCompleted: Boolean,
    val defaultThresholdDays: Int,
    val notificationDetectionEnabled: Boolean,
    val callLogPermissionRequested: Boolean,
    val contactsPermissionRequested: Boolean,
    val lastSyncAt: Long?,
    val lastSyncRequestedVoip: Boolean,
    val sortOrder: SortOrder,
)

/** Preferences live in Room (app-private), next to the data they describe. */
class SettingsRepository(private val dao: SettingsDao) {
    private val mutex = Mutex()

    val settings: Flow<AppSettings> = dao.observe().map { (it ?: AppSettingsEntity()).toModel() }

    suspend fun current(): AppSettings = (dao.get() ?: AppSettingsEntity()).toModel()

    suspend fun setOnboardingCompleted(done: Boolean) = edit { it.copy(onboardingCompleted = done) }
    suspend fun setDefaultThreshold(days: Int) = edit { it.copy(defaultThresholdDays = days) }
    suspend fun setNotificationDetection(enabled: Boolean) = edit { it.copy(notificationDetectionEnabled = enabled) }
    suspend fun markCallLogRequested() = edit { it.copy(callLogPermissionRequested = true) }
    suspend fun markContactsRequested() = edit { it.copy(contactsPermissionRequested = true) }
    suspend fun setSortOrder(order: SortOrder) = edit { it.copy(sortOrder = order.name) }

    /** Restores defaults (used by "Clear app data"). */
    suspend fun reset() = mutex.withLock { dao.clear() }

    private suspend fun edit(change: (AppSettingsEntity) -> AppSettingsEntity) = mutex.withLock {
        dao.upsert(change(dao.get() ?: AppSettingsEntity()))
    }

    private fun AppSettingsEntity.toModel() = AppSettings(
        onboardingCompleted = onboardingCompleted,
        defaultThresholdDays = defaultThresholdDays,
        notificationDetectionEnabled = notificationDetectionEnabled,
        callLogPermissionRequested = callLogPermissionRequested,
        contactsPermissionRequested = contactsPermissionRequested,
        lastSyncAt = lastSyncAt,
        lastSyncRequestedVoip = lastSyncRequestedVoip,
        sortOrder = SortOrder.entries.firstOrNull { it.name == sortOrder } ?: SortOrder.RECENTLY_CALLED,
    )
}

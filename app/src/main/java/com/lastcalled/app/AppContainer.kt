package com.lastcalled.app

import android.content.Context
import com.lastcalled.app.data.local.AppDatabase
import com.lastcalled.app.data.local.RoomCallStore
import com.lastcalled.app.data.repository.CallHistoryRepository
import com.lastcalled.app.data.repository.SettingsRepository
import com.lastcalled.app.data.repository.SyncManager
import com.lastcalled.app.data.system.AndroidCallLogSource
import com.lastcalled.app.data.system.AndroidContactsSource
import com.lastcalled.app.data.system.DeviceCountry
import com.lastcalled.app.data.system.PermissionChecker
import com.lastcalled.domain.notification.RecordNotificationCallUseCase
import com.lastcalled.domain.sync.SyncCallHistoryUseCase
import com.lastcalled.domain.sync.WatchListUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency wiring. The graph is small, so a DI framework would add more indirection than
 * it removes.
 */
class AppContainer(context: Context, val database: AppDatabase = AppDatabase.create(context)) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: () -> Long = System::currentTimeMillis

    val permissions = PermissionChecker(context)
    private val country = DeviceCountry(context)
    private val callLogSource = AndroidCallLogSource(context.contentResolver, permissions, country)
    val contactsSource = AndroidContactsSource(context.contentResolver, context.resources, permissions, country)
    private val store = RoomCallStore(database)

    val settingsRepository = SettingsRepository(database.settingsDao())
    val callHistoryRepository = CallHistoryRepository(database)

    val syncManager = SyncManager(
        SyncCallHistoryUseCase(callLogSource, contactsSource, store, clock),
        appScope,
        clock,
    )

    val watchList = WatchListUseCase(contactsSource, store, clock)

    val recordNotificationCall = RecordNotificationCallUseCase(contactsSource, store, clock)
}

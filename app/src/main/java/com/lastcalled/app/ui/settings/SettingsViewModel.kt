package com.lastcalled.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastcalled.app.AppContainer
import com.lastcalled.app.data.local.CallStats
import com.lastcalled.app.data.repository.AppSettings
import com.lastcalled.app.data.repository.SyncStatus
import com.lastcalled.app.notifications.WhatsAppCallListenerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings? = null,
    val stats: CallStats = CallStats(0, 0, 0),
    val sync: SyncStatus = SyncStatus.Idle,
)

class SettingsViewModel(app: Application, private val container: AppContainer) : AndroidViewModel(app) {
    val state: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settings,
        container.callHistoryRepository.observeStats(),
        container.syncManager.status,
    ) { s, stats, sync -> SettingsUiState(s, stats, sync) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refresh() = container.syncManager.refresh()

    fun setThreshold(days: Int) = viewModelScope.launch { container.settingsRepository.setDefaultThreshold(days) }

    fun setNotificationDetection(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.setNotificationDetection(enabled)
        if (enabled) WhatsAppCallListenerService.requestRebind(getApplication())
    }

    /** Deletes this app's stored calls; the next refresh re-imports what the system still has. */
    fun resetImportedRecords() = viewModelScope.launch {
        container.callHistoryRepository.resetImportedRecords()
        container.syncManager.forgetLastSync()
    }

    /** Deletes everything this app stored, including the watch list and settings, and returns to onboarding. */
    fun clearAppData() = viewModelScope.launch {
        container.callHistoryRepository.resetImportedRecords()
        container.callHistoryRepository.clearTracked()
        container.settingsRepository.reset()
        container.syncManager.forgetLastSync()
    }
}

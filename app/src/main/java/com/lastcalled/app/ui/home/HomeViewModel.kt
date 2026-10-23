package com.lastcalled.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastcalled.app.AppContainer
import com.lastcalled.app.data.local.CallStats
import com.lastcalled.app.data.repository.SyncStatus
import com.lastcalled.app.ui.common.clockTicks
import com.lastcalled.domain.logic.PeopleSelector
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.PersonSummary
import com.lastcalled.domain.model.SortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val now: Long = 0,
    val filter: CallFilter = CallFilter.OUTGOING,
    val sortOrder: SortOrder = SortOrder.RECENTLY_CALLED,
    val thresholdDays: Int = 7,
    val includeUnknown: Boolean = false,
    /** People on the user's watch list not called within [thresholdDays], longest gap first. */
    val notCalledRecently: List<PersonSummary> = emptyList(),
    val watchListSize: Int = 0,
    val trackedKeys: Set<PersonKey> = emptySet(),
    val people: List<PersonSummary> = emptyList(),
    val hiddenUnknownCount: Int = 0,
    val stats: CallStats = CallStats(0, 0, 0),
    val sync: SyncStatus = SyncStatus.Idle,
    val lastSyncAt: Long? = null,
)

private data class Controls(
    val filter: CallFilter = CallFilter.OUTGOING,
    val threshold: Int? = null,
    val includeUnknown: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.callHistoryRepository
    private val settingsRepo = container.settingsRepository
    private val controls = MutableStateFlow(Controls())

    private val people = controls.map { it.filter }.flatMapLatest { repo.observePeople(it) }

    /**
     * The dashboard only covers people the user added to their watch list, and always measures the
     * last call *you* made, independent of list filters.
     */
    private val watchList = combine(repo.observePeople(CallFilter.OUTGOING), repo.observeTracked()) { outgoing, tracked ->
        PeopleSelector.watchList(tracked, outgoing)
    }

    val state: StateFlow<HomeUiState> = combine(
        combine(controls, settingsRepo.settings, ::Pair),
        people,
        watchList,
        combine(repo.observeStats(), container.syncManager.status, ::Pair),
        clockTicks(container.clock),
    ) { (c, settings), all, watched, (stats, sync), now ->
        val threshold = c.threshold ?: settings.defaultThresholdDays
        // With the default filter everyone with history is listed ("Never called" included). With
        // extra filters (e.g. WhatsApp + Outgoing) only people with a matching call are shown.
        val matching = if (c.filter == CallFilter.OUTGOING) all else all.filter { it.lastMatchingCall != null }
        val visible = if (c.includeUnknown) matching else matching.filter { it.isContact }
        HomeUiState(
            loading = false,
            now = now,
            filter = c.filter,
            sortOrder = settings.sortOrder,
            thresholdDays = threshold,
            includeUnknown = c.includeUnknown,
            notCalledRecently = PeopleSelector.notCalledRecently(watched, threshold, now),
            watchListSize = watched.size,
            trackedKeys = watched.mapTo(HashSet()) { it.key },
            people = PeopleSelector.sort(visible, settings.sortOrder),
            hiddenUnknownCount = matching.size - visible.size,
            stats = stats,
            sync = sync,
            lastSyncAt = settings.lastSyncAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFilter(filter: CallFilter) = controls.value.let { controls.value = it.copy(filter = filter) }
    fun setThreshold(days: Int) = controls.value.let { controls.value = it.copy(threshold = days) }
    fun setIncludeUnknown(include: Boolean) = controls.value.let { controls.value = it.copy(includeUnknown = include) }
    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settingsRepo.setSortOrder(order) }
    }

    fun refresh() = container.syncManager.refresh()

    companion object {
        val THRESHOLDS = listOf(3, 7, 14, 30)
    }
}

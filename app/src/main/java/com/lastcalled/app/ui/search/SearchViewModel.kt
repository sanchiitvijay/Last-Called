package com.lastcalled.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastcalled.app.AppContainer
import com.lastcalled.app.ui.common.clockTicks
import com.lastcalled.domain.logic.PeopleSelector
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.PersonSummary
import com.lastcalled.domain.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(val query: String = "", val results: List<PersonSummary> = emptyList(), val now: Long = 0)

/** Local search over people with call history, including unknown numbers. Nothing leaves the device. */
class SearchViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<SearchUiState> = combine(
        query,
        container.callHistoryRepository.observePeople(CallFilter.OUTGOING),
        clockTicks(container.clock),
    ) { q, people, now ->
        val results = if (q.isBlank()) emptyList() else PeopleSelector.sort(PeopleSelector.search(people, q), SortOrder.ALPHABETICAL)
        SearchUiState(q, results, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(q: String) {
        query.value = q
    }
}

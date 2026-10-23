package com.lastcalled.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lastcalled.app.AppContainer
import com.lastcalled.app.data.repository.PersonDetails
import com.lastcalled.app.ui.common.clockTicks
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.PersonKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonDetailsUiState(
    val loading: Boolean = true,
    val details: PersonDetails? = null,
    val now: Long = 0,
    val filter: CallFilter = CallFilter.ALL,
    val isTracked: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailsViewModel(private val container: AppContainer, private val key: PersonKey) : ViewModel() {
    private val repo = container.callHistoryRepository
    private val filter = MutableStateFlow(CallFilter.ALL)

    val state: StateFlow<PersonDetailsUiState> = combine(
        repo.observePerson(key),
        clockTicks(container.clock),
        filter,
        repo.observeTracked(),
    ) { details, now, f, tracked -> PersonDetailsUiState(false, details, now, f, tracked.any { it.personKey == key }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailsUiState())

    /** Paged so very long histories never load into memory at once. */
    val history: Flow<PagingData<CallRecord>> =
        filter.flatMapLatest { repo.pagedHistory(key, it) }.cachedIn(viewModelScope)

    fun setFilter(f: CallFilter) {
        filter.value = f
    }

    /** Adds this person to, or removes them from, the "keep in touch" list. */
    fun setTracked(tracked: Boolean) = viewModelScope.launch {
        if (!tracked) {
            container.watchList.remove(key)
            return@launch
        }
        val d = state.value.details
        container.watchList.addExisting(key, d?.contactName ?: d?.nameHint, d?.numbers?.firstOrNull()?.number)
    }
}

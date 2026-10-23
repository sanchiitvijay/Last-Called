package com.lastcalled.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastcalled.app.AppContainer
import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.PeopleSelector
import com.lastcalled.domain.logic.PhoneNumberNormalizer
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.PersonSummary
import com.lastcalled.domain.sync.SourceResult
import com.lastcalled.domain.sync.WatchListUseCase
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row in the picker: someone with call history, or a device contact without any calls yet. */
data class WatchCandidate(
    val key: PersonKey,
    val title: String,
    val subtitle: String?,
    val photoUri: String?,
    val tracked: Boolean,
    internal val contact: DeviceContact? = null,
    internal val phoneNumber: String? = null,
)

data class WatchListUiState(
    val query: String = "",
    val onList: List<WatchCandidate> = emptyList(),
    /** Suggestions (empty query) or search results. */
    val candidates: List<WatchCandidate> = emptyList(),
    /** Set when the query looks like a phone number that can be added directly. */
    val addableNumber: String? = null,
    val invalidNumber: Boolean = false,
    val contactsAvailable: Boolean = true,
)

class WatchListViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val invalidNumber = MutableStateFlow(false)
    private val deviceContacts = MutableStateFlow<List<DeviceContact>?>(null)

    init {
        // Read once, locally, so contacts without call history can be added too.
        viewModelScope.launch {
            deviceContacts.value = (container.contactsSource.readContacts() as? SourceResult.Available)?.data
        }
    }

    val state: StateFlow<WatchListUiState> = combine(
        query,
        container.callHistoryRepository.observePeople(CallFilter.OUTGOING),
        container.callHistoryRepository.observeTracked(),
        deviceContacts,
        invalidNumber,
    ) { q, people, tracked, contacts, invalid ->
        val trackedKeys = tracked.mapTo(HashSet()) { it.personKey }
        val onList = PeopleSelector.watchList(tracked, people).map { it.toCandidate(tracked = true) }

        val fromHistory = people.filter { it.key !is PersonKey.Hidden }
        val candidates = if (q.isBlank()) {
            fromHistory.filter { it.isContact && it.key !in trackedKeys }
                .sortedByDescending { it.totalCalls }
                .take(SUGGESTION_COUNT)
                .map { it.toCandidate(tracked = false) }
        } else {
            val historyMatches = PeopleSelector.search(fromHistory, q).map { it.toCandidate(it.key in trackedKeys) }
            val shown = historyMatches.mapTo(HashSet()) { it.key }
            val contactMatches = contacts.orEmpty()
                .filter { fold(it.displayName).contains(fold(q)) || it.phoneNumbers.any { n -> digitsMatch(n.number, q) } }
                .filter { PersonKey.Contact(it.lookupKey) !in shown }
                .map { c ->
                    WatchCandidate(
                        key = PersonKey.Contact(c.lookupKey),
                        title = c.displayName,
                        subtitle = c.phoneNumbers.firstOrNull()?.number,
                        photoUri = c.photoThumbnailUri,
                        tracked = PersonKey.Contact(c.lookupKey) in trackedKeys,
                        contact = c,
                    )
                }
            (historyMatches + contactMatches).sortedBy { it.title.lowercase() }
        }
        WatchListUiState(
            query = q,
            onList = onList,
            candidates = candidates,
            addableNumber = q.trim().takeIf { PhoneNumberNormalizer.looksLikePhoneNumber(it) },
            invalidNumber = invalid,
            contactsAvailable = contacts != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchListUiState())

    fun setQuery(q: String) {
        query.value = q
        invalidNumber.value = false
    }

    fun add(candidate: WatchCandidate) = viewModelScope.launch {
        val contact = candidate.contact
        if (contact != null) {
            container.watchList.addContact(contact)
        } else {
            container.watchList.addExisting(candidate.key, candidate.title, candidate.phoneNumber)
        }
    }

    fun remove(key: PersonKey) = viewModelScope.launch { container.watchList.remove(key) }

    fun addNumber() = viewModelScope.launch {
        when (container.watchList.addNumber(query.value)) {
            is WatchListUseCase.AddResult.Added -> query.value = ""
            WatchListUseCase.AddResult.InvalidNumber -> invalidNumber.value = true
        }
    }

    private fun PersonSummary.toCandidate(tracked: Boolean) = WatchCandidate(
        key = key,
        title = contactName ?: phoneNumber ?: nameHint ?: "",
        subtitle = if (contactName != null) phoneNumber else null,
        photoUri = photoUri,
        tracked = tracked,
        phoneNumber = phoneNumber,
    )

    private companion object {
        const val SUGGESTION_COUNT = 20

        fun fold(text: String) =
            Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

        fun digitsMatch(number: String, query: String): Boolean {
            val q = query.filter { it.isDigit() }
            return q.length >= 3 && number.filter { it.isDigit() }.contains(q)
        }
    }
}

package com.lastcalled.app.ui

import com.lastcalled.app.data.local.CallStats
import com.lastcalled.app.data.repository.SyncStatus
import com.lastcalled.app.ui.home.HomeActions
import com.lastcalled.app.ui.home.HomeUiState
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallSnapshot
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.PersonSummary

object UiFixtures {
    const val NOW = 1_791_000_000_000L
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L

    fun person(key: String, name: String?, agoMillis: Long?, source: CallSource = CallSource.PHONE, type: CallType = CallType.VOICE, number: String? = null) =
        PersonSummary(
            key = if (name != null) PersonKey.Contact(key) else PersonKey.Number(number!!),
            contactName = name,
            phoneNumber = number,
            nameHint = null,
            photoUri = null,
            totalCalls = 3,
            lastMatchingCall = agoMillis?.let { CallSnapshot(NOW - it, CallDirection.OUTGOING, source, type, 60) },
        )

    val rahul = person("rahul", "Rahul Sharma", 2 * DAY, CallSource.WHATSAPP, CallType.VOICE)
    val ankit = person("ankit", "Ankit", 6 * HOUR)
    val priya = person("priya", "Priya", 15 * DAY, CallSource.WHATSAPP, CallType.VIDEO)
    val mehul = person("mehul", "Mehul", null)

    fun state(
        people: List<PersonSummary> = listOf(ankit, rahul, priya, mehul),
        stats: CallStats = CallStats(12, 4, 8),
        filter: CallFilter = CallFilter.OUTGOING,
        notCalled: List<PersonSummary> = listOf(mehul, priya),
        hiddenUnknown: Int = 0,
        watchListSize: Int = 4,
    ) = HomeUiState(
        loading = false,
        now = NOW,
        filter = filter,
        thresholdDays = 7,
        notCalledRecently = notCalled,
        watchListSize = watchListSize,
        trackedKeys = notCalled.mapTo(HashSet()) { it.key },
        people = people,
        hiddenUnknownCount = hiddenUnknown,
        stats = stats,
        sync = SyncStatus.Idle,
        lastSyncAt = NOW - 60_000,
    )

    class Recorder {
        val events = mutableListOf<String>()
        var lastFilter: CallFilter? = null

        fun actions() = HomeActions(
            onPersonClick = { events += "open:${it.value}" },
            onSearch = { events += "search" },
            onSettings = { events += "settings" },
            onRefresh = { events += "refresh" },
            onFilterChange = { lastFilter = it },
            onSortChange = { events += "sort:${it.name}" },
            onThresholdChange = { events += "threshold:$it" },
            onIncludeUnknownChange = { events += "unknown:$it" },
            onRequestCallLog = { events += "request_call_log" },
            onRequestContacts = { events += "request_contacts" },
            onOpenAppSettings = { events += "app_settings" },
            onManageWatchList = { events += "watchlist" },
        )
    }
}

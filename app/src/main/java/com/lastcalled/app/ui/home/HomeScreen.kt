package com.lastcalled.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastcalled.app.R
import com.lastcalled.app.data.repository.SyncStatus
import com.lastcalled.app.ui.common.EmptyState
import com.lastcalled.app.ui.common.FilterBar
import com.lastcalled.app.ui.common.NoticeCard
import com.lastcalled.app.ui.common.PermissionStatus
import com.lastcalled.app.ui.common.PersonAvatar
import com.lastcalled.app.ui.common.callKindLabel
import com.lastcalled.app.ui.common.daysCompact
import com.lastcalled.app.ui.common.personTitle
import com.lastcalled.app.ui.common.timeAgo
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.PersonSummary
import com.lastcalled.domain.model.SortOrder

class HomeActions(
    val onPersonClick: (PersonKey) -> Unit,
    val onSearch: () -> Unit,
    val onSettings: () -> Unit,
    val onRefresh: () -> Unit,
    val onFilterChange: (CallFilter) -> Unit,
    val onSortChange: (SortOrder) -> Unit,
    val onThresholdChange: (Int) -> Unit,
    val onIncludeUnknownChange: (Boolean) -> Unit,
    val onRequestCallLog: () -> Unit,
    val onRequestContacts: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onManageWatchList: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    callLogPermission: PermissionStatus,
    contactsPermission: PermissionStatus,
    actions: HomeActions,
    selectedKey: PersonKey? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = actions.onSearch, modifier = Modifier.testTag("action_search")) {
                        Icon(Icons.Filled.Search, stringResource(R.string.action_search))
                    }
                    if (state.sync == SyncStatus.Running) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(
                            onClick = actions.onRefresh,
                            enabled = callLogPermission == PermissionStatus.GRANTED,
                            modifier = Modifier.testTag("action_refresh"),
                        ) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                        }
                    }
                    IconButton(onClick = actions.onSettings, modifier = Modifier.testTag("action_settings")) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("home_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (state.sync == SyncStatus.Running) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            item { SyncLine(state) }
            if (callLogPermission != PermissionStatus.GRANTED && state.stats.total > 0) {
                item {
                    PermissionNotice(
                        stringResource(R.string.banner_call_log_missing),
                        callLogPermission, actions.onRequestCallLog, actions.onOpenAppSettings,
                    )
                }
            }
            if (contactsPermission != PermissionStatus.GRANTED && state.stats.total > 0) {
                item {
                    PermissionNotice(
                        stringResource(R.string.banner_contacts_missing),
                        contactsPermission, actions.onRequestContacts, actions.onOpenAppSettings,
                    )
                }
            }

            if (state.stats.total == 0) {
                item { NoHistoryState(state, callLogPermission, actions) }
                return@LazyColumn
            }

            item { Dashboard(state, actions) }

            item { EveryoneHeader(state.sortOrder, actions.onSortChange) }
            item { FilterBar(state.filter, actions.onFilterChange, Modifier.padding(bottom = 4.dp)) }
            item {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(
                        selected = state.includeUnknown,
                        onClick = { actions.onIncludeUnknownChange(!state.includeUnknown) },
                        label = { Text(stringResource(R.string.filter_unknown_numbers)) },
                        modifier = Modifier.testTag("filter_unknown"),
                    )
                }
            }
            if (state.filter.isOutgoingOnly && state.stats.outgoing == 0) {
                item { EmptyState(stringResource(R.string.empty_no_outgoing)) }
            }
            if (state.people.isEmpty()) {
                item { EmptyPeopleState(state, actions) }
            }
            items(state.people, key = { it.key.value }) { person ->
                PersonRow(person, state.filter, state.now, selected = person.key == selectedKey, tracked = person.key in state.trackedKeys) {
                    actions.onPersonClick(person.key)
                }
            }
        }
    }
}

@Composable
private fun SyncLine(state: HomeUiState) {
    val text = when (val s = state.sync) {
        SyncStatus.Running -> stringResource(R.string.syncing)
        is SyncStatus.Finished -> when {
            s.result.failure != null && !s.result.callLogAvailable -> stringResource(R.string.sync_failed, s.result.failure!!)
            s.result.inserted > 0 -> pluralStringResource(R.plurals.sync_new_calls, s.result.inserted, s.result.inserted)
            else -> state.lastSyncAt?.let { stringResource(R.string.last_synced, timeAgo(it, state.now).lowercase()) }
        }
        SyncStatus.Idle -> state.lastSyncAt?.let { stringResource(R.string.last_synced, timeAgo(it, state.now).lowercase()) }
    } ?: return
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("sync_status"),
    )
}

@Composable
private fun PermissionNotice(text: String, status: PermissionStatus, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val permanentlyDenied = status == PermissionStatus.PERMANENTLY_DENIED
    NoticeCard(
        text = text,
        actionLabel = stringResource(if (permanentlyDenied) R.string.action_open_settings else R.string.action_allow),
        onAction = if (permanentlyDenied) onOpenSettings else onRequest,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun NoHistoryState(state: HomeUiState, callLogPermission: PermissionStatus, actions: HomeActions) {
    when {
        callLogPermission != PermissionStatus.GRANTED -> EmptyState(
            title = stringResource(R.string.empty_grant_call_log),
            body = stringResource(R.string.onboarding_call_log_body),
            modifier = Modifier.testTag("empty_grant_call_log"),
        ) {
            if (callLogPermission == PermissionStatus.PERMANENTLY_DENIED) {
                Button(onClick = actions.onOpenAppSettings) { Text(stringResource(R.string.action_open_settings)) }
            } else {
                Button(onClick = actions.onRequestCallLog) { Text(stringResource(R.string.action_allow)) }
            }
        }
        state.sync == SyncStatus.Running || state.loading -> Box(
            Modifier.fillMaxWidth().padding(48.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        else -> EmptyState(
            title = stringResource(R.string.empty_no_history),
            body = stringResource(R.string.empty_no_history_body),
            modifier = Modifier.testTag("empty_no_history"),
        )
    }
}

@Composable
private fun EmptyPeopleState(state: HomeUiState, actions: HomeActions) {
    when {
        CallSource.WHATSAPP in state.filter.sources && state.stats.whatsApp == 0 -> EmptyState(
            title = stringResource(R.string.empty_whatsapp_unavailable),
            body = stringResource(R.string.empty_whatsapp_unavailable_body),
            modifier = Modifier.testTag("empty_whatsapp"),
        )
        !state.includeUnknown && state.hiddenUnknownCount > 0 -> EmptyState(
            title = stringResource(R.string.empty_no_contacts),
            modifier = Modifier.testTag("empty_no_contacts"),
        ) {
            TextButton(onClick = { actions.onIncludeUnknownChange(true) }) {
                Text(stringResource(R.string.filter_unknown_numbers))
            }
        }
        else -> EmptyState(stringResource(R.string.empty_no_match_filter), modifier = Modifier.testTag("empty_filter"))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(state: HomeUiState, actions: HomeActions) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(16.dp).testTag("dashboard")) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.not_called_recently_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (state.watchListSize == 0) {
                Text(
                    stringResource(R.string.watchlist_empty_dashboard),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("watchlist_empty"),
                )
                Button(
                    onClick = actions.onManageWatchList,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("watchlist_add_people"),
                ) { Text(stringResource(R.string.watchlist_add_people)) }
                return@Column
            }
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeViewModel.THRESHOLDS.forEach { days ->
                    FilterChip(
                        selected = state.thresholdDays == days,
                        onClick = { actions.onThresholdChange(days) },
                        label = { Text(pluralStringResource(R.plurals.not_called_in_days, days, days)) },
                        modifier = Modifier.testTag("threshold_$days"),
                    )
                }
            }
            if (state.notCalledRecently.isEmpty()) {
                Text(
                    stringResource(R.string.everyone_caught_up),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val shown = if (expanded) state.notCalledRecently else state.notCalledRecently.take(5)
            shown.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { actions.onPersonClick(p.key) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("dashboard_row"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(p.label, p.photoUri, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        personTitle(p.key, p.contactName, p.phoneNumber, p.nameHint),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        p.lastMatchingCall?.let { daysCompact(it.timestamp, state.now) } ?: stringResource(R.string.never_called),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                if (!expanded && state.notCalledRecently.size > 5) {
                    TextButton(onClick = { expanded = true }) {
                        Text(stringResource(R.string.action_see_all) + " (${state.notCalledRecently.size})")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = actions.onManageWatchList, modifier = Modifier.testTag("watchlist_manage")) {
                    Text(stringResource(R.string.watchlist_manage))
                }
            }
        }
    }
}

@Composable
private fun EveryoneHeader(sortOrder: SortOrder, onSortChange: (SortOrder) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.all_people), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }, modifier = Modifier.testTag("sort_button")) {
                Text(stringResource(sortOrder.label()))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.sort_label))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(stringResource(order.label())) },
                        onClick = {
                            open = false
                            onSortChange(order)
                        },
                        modifier = Modifier.testTag("sort_${order.name}"),
                    )
                }
            }
        }
    }
}

private fun SortOrder.label() = when (this) {
    SortOrder.RECENTLY_CALLED -> R.string.sort_recent
    SortOrder.LONGEST_SINCE_CALLED -> R.string.sort_longest
    SortOrder.ALPHABETICAL -> R.string.sort_alpha
}

@Composable
fun PersonRow(
    person: PersonSummary,
    filter: CallFilter,
    now: Long,
    selected: Boolean = false,
    tracked: Boolean = false,
    onClick: () -> Unit,
) {
    val title = personTitle(person.key, person.contactName, person.phoneNumber, person.nameHint)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .testTag("person_row"),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(person.contactName ?: person.nameHint, person.photoUri)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tracked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = stringResource(R.string.watchlist_added),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val last = person.lastMatchingCall
                Text(
                    when {
                        last == null && filter.isOutgoingOnly -> stringResource(R.string.never_called)
                        last == null -> stringResource(R.string.no_matching_calls)
                        filter.isOutgoingOnly -> stringResource(R.string.last_called, timeAgo(last.timestamp, now).lowercase())
                        else -> stringResource(R.string.last_matching_call, timeAgo(last.timestamp, now).lowercase())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val details = buildList {
                    if (last != null) add(stringResource(callKindLabel(last.source, last.callType)))
                    if (!person.isContact) add(stringResource(R.string.not_in_contacts))
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 76.dp))
    }
}

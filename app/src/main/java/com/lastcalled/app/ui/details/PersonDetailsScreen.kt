package com.lastcalled.app.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.lastcalled.app.R
import com.lastcalled.app.data.repository.PersonDetails
import com.lastcalled.app.ui.common.DateFormats
import com.lastcalled.app.ui.common.EmptyState
import com.lastcalled.app.ui.common.FilterBar
import com.lastcalled.app.ui.common.PersonAvatar
import com.lastcalled.app.ui.common.callKindLabel
import com.lastcalled.app.ui.common.label
import com.lastcalled.app.ui.common.personTitle
import com.lastcalled.app.ui.common.timeAgo
import com.lastcalled.domain.logic.formatDuration
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.RecordOrigin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailsScreen(
    state: PersonDetailsUiState,
    history: LazyPagingItems<CallRecord>,
    onFilterChange: (CallFilter) -> Unit,
    onBack: (() -> Unit)?,
    onTrackedChange: (Boolean) -> Unit = {},
) {
    val details = state.details
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    // Hidden numbers can't be identified again, so they can't be tracked.
                    if (details != null && details.key !is PersonKey.Hidden) {
                        IconButton(onClick = { onTrackedChange(!state.isTracked) }, modifier = Modifier.testTag("action_track")) {
                            Icon(
                                Icons.Filled.Star,
                                stringResource(if (state.isTracked) R.string.watchlist_remove else R.string.watchlist_add),
                                tint = if (state.isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("action_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (details == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("details_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { Header(details) }
            item { LastCalledCard(details, state.now) }
            item {
                Text(
                    stringResource(R.string.details_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            item { FilterBar(state.filter, onFilterChange, Modifier.padding(bottom = 8.dp)) }
            if (history.loadState.refresh is LoadState.NotLoading && history.itemCount == 0) {
                item {
                    EmptyState(
                        if (details.latestAny == null) stringResource(R.string.details_not_found)
                        else stringResource(R.string.empty_no_match_filter),
                    )
                }
            }
            items(history.itemCount, key = history.itemKey { it.id }) { index ->
                history[index]?.let { HistoryRow(it) }
            }
        }
    }
}

@Composable
private fun Header(details: PersonDetails) {
    val title = personTitle(details.key, details.contactName, details.numbers.firstOrNull()?.number, details.nameHint)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PersonAvatar(details.contactName ?: details.nameHint, details.photoUri, size = 88.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("details_name"))
        when {
            details.key is PersonKey.Name -> Subtle(stringResource(R.string.from_whatsapp_notification))
            details.key !is PersonKey.Contact -> Subtle(stringResource(R.string.not_in_contacts))
        }
        details.numbers.forEach { n ->
            if (n.number != title) {
                Subtle(listOfNotNull(n.number, n.label).joinToString(" · "))
            }
        }
    }
}

@Composable
private fun Subtle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LastCalledCard(details: PersonDetails, now: Long) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp).testTag("last_called_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.details_last_called), style = MaterialTheme.typography.labelLarge)
            val last = details.latestOutgoing
            if (last == null) {
                Text(
                    stringResource(R.string.empty_no_outgoing),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("last_called_value"),
                )
                details.latestAny?.let {
                    Text(
                        stringResource(R.string.details_last_incoming, timeAgo(it.timestamp, now).lowercase()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@Column
            }
            Text(
                timeAgo(last.timestamp, now),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("last_called_value"),
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(last.source.label()), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(callTypeLine(last)), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.date_at_time, DateFormats.longDate(last.timestamp), DateFormats.time(last.timestamp)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.duration_label, formatDuration(last.durationSeconds)),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (last.origin == RecordOrigin.WHATSAPP_NOTIFICATION) {
                Text(stringResource(R.string.origin_notification), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun callTypeLine(record: CallRecord): Int = when (record.callType) {
    com.lastcalled.domain.model.CallType.VOICE -> R.string.type_voice_call
    com.lastcalled.domain.model.CallType.VIDEO -> R.string.type_video_call
    com.lastcalled.domain.model.CallType.UNKNOWN -> R.string.type_unknown
}

@Composable
private fun HistoryRow(record: CallRecord) {
    Column(Modifier.fillMaxWidth().testTag("history_row")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(112.dp)) {
                Text(DateFormats.mediumDate(record.timestamp), style = MaterialTheme.typography.bodyMedium)
                Text(
                    DateFormats.time(record.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(record.direction.label()), style = MaterialTheme.typography.bodyLarge)
                val kind = stringResource(callKindLabel(record.source, record.callType))
                val approx = if (record.origin == RecordOrigin.WHATSAPP_NOTIFICATION) " ≈" else ""
                Text(
                    kind + approx,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.source == CallSource.WHATSAPP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatDuration(record.durationSeconds), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

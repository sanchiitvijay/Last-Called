package com.lastcalled.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType

/**
 * Combinable filter chips. Selecting nothing in a group means "all" for that group, so e.g.
 * WhatsApp + Outgoing shows only WhatsApp calls you made.
 */
@Composable
fun FilterBar(filter: CallFilter, onChange: (CallFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(CallSource.PHONE, CallSource.WHATSAPP).forEach { s ->
            Chip(stringResource(s.label()), s in filter.sources, "filter_source_${s.name}") {
                onChange(filter.copy(sources = filter.sources.toggle(s)))
            }
        }
        VerticalDivider(Modifier.padding(vertical = 8.dp))
        listOf(CallDirection.OUTGOING, CallDirection.INCOMING, CallDirection.MISSED).forEach { d ->
            Chip(stringResource(d.label()), d in filter.directions, "filter_direction_${d.name}") {
                onChange(filter.copy(directions = filter.directions.toggle(d)))
            }
        }
        VerticalDivider(Modifier.padding(vertical = 8.dp))
        listOf(CallType.VOICE, CallType.VIDEO).forEach { t ->
            Chip(stringResource(t.label()), t in filter.types, "filter_type_${t.name}") {
                onChange(filter.copy(types = filter.types.toggle(t)))
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.testTag(tag))
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

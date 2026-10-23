package com.lastcalled.app.ui.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lastcalled.app.R
import com.lastcalled.app.ui.common.PersonAvatar
import com.lastcalled.domain.model.PersonKey

/** Choose who appears in "People not called recently": search contacts, history, or type a number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchListScreen(
    state: WatchListUiState,
    onQueryChange: (String) -> Unit,
    onAdd: (WatchCandidate) -> Unit,
    onRemove: (PersonKey) -> Unit,
    onAddNumber: () -> Unit,
    onBack: () -> Unit,
) {
    // The field owns its text so fast typing is never overwritten by a lagging state emission.
    var text by rememberSaveable { mutableStateOf(state.query) }
    LaunchedEffect(state.query) { if (state.query.isEmpty()) text = "" }
    val setText = { q: String -> text = q; onQueryChange(q) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watchlist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("watchlist")) {
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = setText,
                    placeholder = { Text(stringResource(R.string.watchlist_search_hint)) },
                    singleLine = true,
                    isError = state.invalidNumber,
                    supportingText = if (state.invalidNumber) {
                        { Text(stringResource(R.string.watchlist_invalid_number)) }
                    } else {
                        null
                    },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { setText("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.action_clear_search)) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (state.addableNumber != null) onAddNumber() }),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("watchlist_search"),
                )
            }
            state.addableNumber?.let { number ->
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.watchlist_add_number, number)) },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onAddNumber).testTag("watchlist_add_number"),
                    )
                }
            }

            if (state.query.isBlank()) {
                item { Header(stringResource(R.string.watchlist_on_list)) }
                if (state.onList.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.watchlist_empty_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(state.onList, key = { "on-" + it.key.value }) { CandidateRow(it, onAdd, onRemove) }
                if (state.candidates.isNotEmpty()) item { Header(stringResource(R.string.watchlist_suggestions)) }
            } else if (state.candidates.isNotEmpty()) {
                item { Header(stringResource(R.string.watchlist_results)) }
            }
            items(state.candidates, key = { "c-" + it.key.value }) { CandidateRow(it, onAdd, onRemove) }
            if (!state.contactsAvailable) {
                item {
                    Text(
                        stringResource(R.string.watchlist_contacts_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun CandidateRow(c: WatchCandidate, onAdd: (WatchCandidate) -> Unit, onRemove: (PersonKey) -> Unit) {
    ListItem(
        headlineContent = { Text(c.title) },
        supportingContent = c.subtitle?.let { { Text(it) } },
        leadingContent = { PersonAvatar(c.title, c.photoUri, size = 40.dp) },
        trailingContent = {
            if (c.tracked) {
                IconButton(onClick = { onRemove(c.key) }, modifier = Modifier.testTag("watchlist_remove")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.watchlist_remove))
                }
            } else {
                IconButton(onClick = { onAdd(c) }, modifier = Modifier.testTag("watchlist_add")) {
                    Icon(Icons.Filled.Add, stringResource(R.string.watchlist_add))
                }
            }
        },
        modifier = Modifier.testTag("watchlist_row"),
    )
}

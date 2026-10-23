package com.lastcalled.app.ui.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.lastcalled.app.R
import com.lastcalled.app.ui.common.EmptyState
import com.lastcalled.app.ui.home.PersonRow
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.PersonKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onPersonClick: (PersonKey) -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // The field owns its text so fast typing is never overwritten by a lagging state emission.
    var text by rememberSaveable { mutableStateOf(state.query) }
    val setText = { q: String -> text = q; onQueryChange(q) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                title = {
                    TextField(
                        value = text,
                        onValueChange = setText,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        trailingIcon = {
                            if (text.isNotEmpty()) {
                                IconButton(onClick = { setText("") }) {
                                    Icon(Icons.Filled.Clear, stringResource(R.string.action_clear_search))
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search_field"),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("search_results")) {
            if (state.query.isNotBlank() && state.results.isEmpty()) {
                item { EmptyState(stringResource(R.string.empty_search, state.query), modifier = Modifier.testTag("search_empty")) }
            }
            items(state.results, key = { it.key.value }) { person ->
                PersonRow(person, CallFilter.OUTGOING, state.now) { onPersonClick(person.key) }
            }
        }
    }
}

package com.lastcalled.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastcalled.app.R
import com.lastcalled.app.data.repository.SyncStatus
import com.lastcalled.app.ui.common.DateFormats
import com.lastcalled.app.ui.home.HomeViewModel

class SettingsActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onManagePermissions: () -> Unit,
    val onNotificationDetectionChange: (Boolean) -> Unit,
    val onOpenNotificationAccess: () -> Unit,
    val onThresholdChange: (Int) -> Unit,
    val onResetRecords: () -> Unit,
    val onClearData: () -> Unit,
)

private enum class Dialog { RESET, CLEAR, SOURCES, PRIVACY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: SettingsUiState, hasNotificationAccess: Boolean, hasCallLog: Boolean, actions: SettingsActions) {
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val settings = state.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.settings_section_data))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_refresh)) },
                supportingContent = {
                    Column {
                        Text(
                            if (state.sync == SyncStatus.Running) stringResource(R.string.syncing)
                            else settings?.lastSyncAt?.let {
                                stringResource(R.string.settings_last_sync, stringResource(R.string.date_at_time, DateFormats.mediumDate(it), DateFormats.time(it)))
                            } ?: stringResource(R.string.settings_never_synced),
                        )
                        Text(stringResource(R.string.settings_stats, state.stats.total, state.stats.whatsApp))
                        if (settings?.lastSyncAt != null) {
                            Text(
                                stringResource(
                                    if (settings.lastSyncRequestedVoip) R.string.settings_voip_requested else R.string.settings_voip_not_requested,
                                ),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .clickable(enabled = hasCallLog && state.sync != SyncStatus.Running, onClick = actions.onRefresh)
                    .testTag("settings_refresh"),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_manage_permissions)) },
                modifier = Modifier.clickable(onClick = actions.onManagePermissions).testTag("settings_permissions"),
            )

            Section(stringResource(R.string.settings_section_whatsapp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notification_detection)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.settings_notification_detection_desc))
                        if (settings?.notificationDetectionEnabled == true && !hasNotificationAccess) {
                            Text(
                                stringResource(R.string.settings_notification_access_needed),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable(onClick = actions.onOpenNotificationAccess),
                            )
                        }
                    }
                },
                trailingContent = {
                    Switch(
                        checked = settings?.notificationDetectionEnabled == true,
                        onCheckedChange = actions.onNotificationDetectionChange,
                        modifier = Modifier.testTag("settings_notification_switch"),
                    )
                },
            )

            Section(stringResource(R.string.settings_section_display))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_default_threshold)) },
                supportingContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeViewModel.THRESHOLDS.forEach { days ->
                            FilterChip(
                                selected = settings?.defaultThresholdDays == days,
                                onClick = { actions.onThresholdChange(days) },
                                label = { Text(pluralStringResource(R.plurals.threshold_chip, days, days)) },
                            )
                        }
                    }
                },
            )

            Section(stringResource(R.string.settings_section_reset))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reset_records)) },
                supportingContent = { Text(stringResource(R.string.settings_reset_records_desc)) },
                modifier = Modifier.clickable { dialog = Dialog.RESET }.testTag("settings_reset"),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_data), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(stringResource(R.string.settings_clear_data_desc)) },
                modifier = Modifier.clickable { dialog = Dialog.CLEAR }.testTag("settings_clear"),
            )
            Text(
                stringResource(R.string.settings_reset_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Section(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_sources)) },
                modifier = Modifier.clickable { dialog = Dialog.SOURCES }.testTag("settings_sources"),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                modifier = Modifier.clickable { dialog = Dialog.PRIVACY },
            )
        }
    }

    when (dialog) {
        Dialog.RESET -> ConfirmDialog(
            title = stringResource(R.string.settings_reset_records),
            body = stringResource(R.string.settings_reset_note) + "\n\n" + stringResource(R.string.settings_reset_warning),
            onConfirm = actions.onResetRecords,
            onDismiss = { dialog = null },
        )
        Dialog.CLEAR -> ConfirmDialog(
            title = stringResource(R.string.settings_clear_data),
            body = stringResource(R.string.settings_reset_note) + "\n\n" + stringResource(R.string.settings_reset_warning),
            onConfirm = actions.onClearData,
            onDismiss = { dialog = null },
        )
        Dialog.SOURCES -> InfoDialog(stringResource(R.string.settings_about_sources), stringResource(R.string.about_sources_body)) { dialog = null }
        Dialog.PRIVACY -> InfoDialog(stringResource(R.string.settings_privacy), stringResource(R.string.privacy_body)) { dialog = null }
        null -> Unit
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }, modifier = Modifier.testTag("confirm_delete")) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

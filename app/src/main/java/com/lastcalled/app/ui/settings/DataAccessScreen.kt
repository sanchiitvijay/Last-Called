package com.lastcalled.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastcalled.app.R
import com.lastcalled.app.ui.common.PermissionStatus

/** One place to see and change every kind of data access the app has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAccessScreen(
    callLog: PermissionStatus,
    contacts: PermissionStatus,
    notificationAccess: Boolean,
    onRequestCallLog: () -> Unit,
    onRequestContacts: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_access_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            PermissionRow(R.string.perm_call_log_name, R.string.perm_call_log_desc, callLog, onRequestCallLog, onOpenAppSettings, "access_call_log")
            HorizontalDivider()
            PermissionRow(R.string.perm_contacts_name, R.string.perm_contacts_desc, contacts, onRequestContacts, onOpenAppSettings, "access_contacts")
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.perm_notifications_name)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.perm_notifications_desc))
                        Text(
                            stringResource(if (notificationAccess) R.string.permission_granted else R.string.permission_not_requested),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.perm_notifications_restricted_hint), style = MaterialTheme.typography.bodySmall)
                    }
                },
                trailingContent = {
                    TextButton(onClick = onOpenNotificationAccess) { Text(stringResource(R.string.action_open_settings)) }
                },
                modifier = Modifier.testTag("access_notifications"),
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.onboarding_whatsapp_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun PermissionRow(
    name: Int,
    desc: Int,
    status: PermissionStatus,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    tag: String,
) {
    ListItem(
        headlineContent = { Text(stringResource(name)) },
        supportingContent = {
            Column {
                Text(stringResource(desc))
                Text(
                    stringResource(
                        when (status) {
                            PermissionStatus.GRANTED -> R.string.permission_granted
                            PermissionStatus.NOT_REQUESTED -> R.string.permission_not_requested
                            PermissionStatus.DENIED -> R.string.permission_denied
                            PermissionStatus.PERMANENTLY_DENIED -> R.string.permission_permanently_denied
                        },
                    ),
                    color = if (status == PermissionStatus.GRANTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("${tag}_status"),
                )
            }
        },
        trailingContent = {
            when (status) {
                // Revoking is only possible in system settings; offer the shortcut.
                PermissionStatus.GRANTED, PermissionStatus.PERMANENTLY_DENIED ->
                    TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.action_open_settings)) }
                else -> TextButton(onClick = onRequest, modifier = Modifier.testTag("${tag}_allow")) {
                    Text(stringResource(R.string.action_allow))
                }
            }
        },
        modifier = Modifier.testTag(tag),
    )
}

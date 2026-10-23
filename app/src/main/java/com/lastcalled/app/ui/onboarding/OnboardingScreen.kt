package com.lastcalled.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastcalled.app.R
import com.lastcalled.app.ui.common.PermissionStatus

enum class OnboardingStep { WELCOME, CALL_LOG, CONTACTS, WHATSAPP }

/**
 * Permission onboarding: explain → request call log → explain → request contacts → explain the
 * WhatsApp limitation honestly. Every step can be skipped; the app degrades gracefully.
 */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    callLogStatus: PermissionStatus,
    contactsStatus: PermissionStatus,
    onNext: () -> Unit,
    onRequestCallLog: () -> Unit,
    onRequestContacts: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { (step.ordinal + 1f) / OnboardingStep.entries.size },
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            )
            Spacer(Modifier.height(48.dp))
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (step) {
                    OnboardingStep.WELCOME -> {
                        StepText(Icons.Filled.Lock, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body, "onboarding_welcome")
                        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("onboarding_next")) {
                            Text(stringResource(R.string.action_continue))
                        }
                    }
                    OnboardingStep.CALL_LOG -> PermissionStep(
                        icon = Icons.Filled.Call,
                        title = R.string.onboarding_call_log_title,
                        body = R.string.onboarding_call_log_body,
                        tag = "onboarding_call_log",
                        status = callLogStatus,
                        onRequest = onRequestCallLog,
                        onOpenAppSettings = onOpenAppSettings,
                        onNext = onNext,
                    )
                    OnboardingStep.CONTACTS -> PermissionStep(
                        icon = Icons.Filled.Person,
                        title = R.string.onboarding_contacts_title,
                        body = R.string.onboarding_contacts_body,
                        tag = "onboarding_contacts",
                        status = contactsStatus,
                        onRequest = onRequestContacts,
                        onOpenAppSettings = onOpenAppSettings,
                        onNext = onNext,
                    )
                    OnboardingStep.WHATSAPP -> {
                        StepText(Icons.Filled.Info, R.string.onboarding_whatsapp_title, R.string.onboarding_whatsapp_body, "onboarding_whatsapp")
                        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().testTag("onboarding_finish")) {
                            Text(stringResource(R.string.action_done))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepText(icon: ImageVector, title: Int, body: Int, tag: String) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(40.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag(tag))
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: Int,
    body: Int,
    tag: String,
    status: PermissionStatus,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onNext: () -> Unit,
) {
    StepText(icon, title, body, tag)
    Text(
        stringResource(
            when (status) {
                PermissionStatus.GRANTED -> R.string.permission_granted
                PermissionStatus.NOT_REQUESTED -> R.string.permission_not_requested
                PermissionStatus.DENIED -> R.string.permission_denied
                PermissionStatus.PERMANENTLY_DENIED -> R.string.permission_permanently_denied
            },
        ),
        style = MaterialTheme.typography.labelLarge,
        color = if (status == PermissionStatus.GRANTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("permission_status"),
    )
    when (status) {
        PermissionStatus.GRANTED -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("onboarding_next")) {
            Text(stringResource(R.string.action_continue))
        }
        PermissionStatus.PERMANENTLY_DENIED -> {
            Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth().testTag("onboarding_open_settings")) {
                Text(stringResource(R.string.action_open_settings))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNext, modifier = Modifier.testTag("onboarding_skip")) { Text(stringResource(R.string.action_skip)) }
            }
        }
        else -> {
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().testTag("onboarding_allow")) {
                Text(stringResource(R.string.action_allow))
            }
            OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("onboarding_skip")) {
                Text(stringResource(R.string.action_not_now))
            }
        }
    }
}

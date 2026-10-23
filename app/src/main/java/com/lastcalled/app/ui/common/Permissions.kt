package com.lastcalled.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lastcalled.app.notifications.WhatsAppCallListenerService

/**
 * All runtime-permission handling goes through here.
 *
 * Android does not report "permanently denied" directly. It is inferred: the permission is not
 * granted, we have asked before, and the system no longer wants a rationale shown. In that state
 * the dialog would not appear, so the UI offers the app's system settings instead.
 */
enum class PermissionStatus { GRANTED, NOT_REQUESTED, DENIED, PERMANENTLY_DENIED }

@Stable
class RuntimePermissionState internal constructor(
    status: PermissionStatus,
    private val launch: () -> Unit,
) {
    var status by mutableStateOf(status)
        internal set

    val isGranted get() = status == PermissionStatus.GRANTED

    fun request() = launch()
}

fun permissionStatus(activity: Activity?, context: Context, permission: String, requestedBefore: Boolean): PermissionStatus = when {
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
    !requestedBefore -> PermissionStatus.NOT_REQUESTED
    activity?.shouldShowRequestPermissionRationale(permission) == true -> PermissionStatus.DENIED
    else -> PermissionStatus.PERMANENTLY_DENIED
}

/**
 * @param requestedBefore persisted flag: has the dialog ever been shown?
 * @param onRequested persist that the dialog has now been shown.
 * @param onResult called with the user's answer.
 */
@Composable
fun rememberRuntimePermission(
    permission: String,
    requestedBefore: Boolean,
    onRequested: () -> Unit,
    onResult: (Boolean) -> Unit = {},
): RuntimePermissionState {
    val context = LocalContext.current
    val activity = context.findActivity()
    val currentRequestedBefore by rememberUpdatedState(requestedBefore)
    val currentOnResult by rememberUpdatedState(onResult)
    val holder = remember { arrayOfNulls<RuntimePermissionState>(1) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        holder[0]?.status = permissionStatus(activity, context, permission, requestedBefore = true)
        currentOnResult(granted)
    }
    val state = remember(permission) {
        RuntimePermissionState(permissionStatus(activity, context, permission, requestedBefore)) {
            onRequested()
            launcher.launch(permission)
        }
    }
    holder[0] = state

    // Re-check on every resume: the user may have granted or revoked access in system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.status = permissionStatus(activity, context, permission, currentRequestedBefore)
    }
    return state
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

object SystemSettings {
    fun openAppDetails(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openNotificationListenerSettings(context: Context) {
        val component = WhatsAppCallListenerService.component(context).flattenToString()
        val attempts = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
                )
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        for (intent in attempts) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
        openAppDetails(context)
    }
}

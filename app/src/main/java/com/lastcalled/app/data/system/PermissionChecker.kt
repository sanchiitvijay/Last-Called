package com.lastcalled.app.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Single place that answers "do we currently hold permission X?". */
class PermissionChecker(private val context: Context) {
    fun hasCallLog(): Boolean = granted(Manifest.permission.READ_CALL_LOG)
    fun hasContacts(): Boolean = granted(Manifest.permission.READ_CONTACTS)

    /** Notification access is granted in system settings, not through a runtime permission dialog. */
    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

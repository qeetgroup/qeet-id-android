package com.qeetgroup.qeetid.system

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qeetgroup.qeetid.approvals.push.SignInNotifications

/** Whether sign-in requests can show as notifications, and how to ask. */
object NotificationAccess {
    /** Notifications are allowed for the app and the sign-in request channel is not turned off. */
    fun enabled(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(SignInNotifications.CHANNEL_ID) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Android 13 and later ask at run time. */
    val needsPermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS

    fun permissionGranted(context: Context): Boolean =
        !needsPermission || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Android will not ask again: only Settings can turn them on. */
    fun refusedForGood(
        activity: Activity?,
        asked: Boolean,
    ): Boolean =
        activity != null && asked && !permissionGranted(activity) && !activity.shouldShowRequestPermissionRationale(PERMISSION)

    /** The app's notification settings. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

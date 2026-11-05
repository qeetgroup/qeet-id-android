package com.qeetgroup.qeetid.approvals.push

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.qeetgroup.qeetid.MainActivity
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.system.NotificationAccess

/**
 * The sign-in request notification. The app writes it itself — the server sends only the request's id — on the
 * high-importance `push-mfa` channel, with the Q-key as its status-bar icon. It has no Approve button: approving needs
 * the number from the sign-in screen and the person's fingerprint or PIN, so a tap opens the request in the app.
 */
object SignInNotifications {
    const val CHANNEL_ID = "push-mfa"

    /** The intent action a tap delivers, with [EXTRA_REQUEST_ID]. */
    const val ACTION_OPEN_REQUEST = "com.qeetgroup.qeetid.action.OPEN_SIGN_IN_REQUEST"
    const val EXTRA_REQUEST_ID = "request_id"

    /** A request can be answered for two minutes; the notification goes with it. */
    private const val TIMEOUT_MILLIS = 120_000L

    fun ensureChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = context.getString(R.string.notification_channel_description)
                    enableVibration(true)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Shows the request [requestId]; nothing if notifications are off (the pending check still finds it). */
    @SuppressLint("MissingPermission") // Checked by NotificationAccess.enabled.
    fun show(
        context: Context,
        requestId: String,
    ) {
        ensureChannel(context)
        if (!NotificationAccess.enabled(context)) return
        val open =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_REQUEST)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap =
            PendingIntent.getActivity(
                context,
                requestId.hashCode(),
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_qeet_id)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_body))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_body)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(TIMEOUT_MILLIS)
                .setContentIntent(tap)
                .build()
        NotificationManagerCompat.from(context).notify(TAG, requestId.hashCode(), notification)
    }

    /** Removes the request's notification once it is open in the app. */
    fun cancel(
        context: Context,
        requestId: String,
    ) {
        NotificationManagerCompat.from(context).cancel(TAG, requestId.hashCode())
    }

    private const val TAG = "sign-in-request"
}

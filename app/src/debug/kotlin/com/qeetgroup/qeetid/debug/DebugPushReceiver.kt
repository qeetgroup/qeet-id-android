package com.qeetgroup.qeetid.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qeetgroup.qeetid.approvals.push.PushMessages

/**
 * Debug builds only: feeds a push message's data into the same handler Firebase's service uses, so a sign-in request
 * can be delivered without a Firebase project. Only the shell (adb) can send it: the receiver requires
 * `android.permission.DUMP`, which apps cannot hold.
 *
 * ```
 * adb shell am broadcast -n com.qeetgroup.qeetid/.debug.DebugPushReceiver \
 *     --es type sign_in_request --es challenge_id <id> --es action sign_in
 * ```
 */
class DebugPushReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val extras = intent.extras ?: return
        val data = extras.keySet().mapNotNull { key -> extras.getString(key)?.let { key to it } }.toMap()
        val pending = goAsync()
        Thread {
            try {
                PushMessages.handle(context.applicationContext, data)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

package com.qeetgroup.qeetid.approvals.push

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.qeetgroup.qeetid.appContainer
import com.qeetgroup.qeetid.approvals.RequestInbox
import kotlinx.coroutines.runBlocking

/**
 * A push message from the server: a data-only message (`type`, `challenge_id`, `action`) — the app writes the
 * notification itself. Firebase's service and the debug entry point both hand messages here.
 */
object PushMessages {
    const val TYPE_SIGN_IN_REQUEST = "sign_in_request"

    /** The request id a message carries, if it is a sign-in request; null for anything else. */
    fun requestId(data: Map<String, String>): String? {
        val type = data["type"]
        // Servers before the native app sent no type, only the id and the action.
        if (type != null && type != TYPE_SIGN_IN_REQUEST) return null
        val id = data["challenge_id"]?.trim() ?: return null
        return id.takeIf(RequestInbox::isRequestId)
    }

    /**
     * Shows the request's notification and, when the app is in front, opens the request right away. Ignored when the
     * phone is not linked: it cannot answer. Runs on a background thread (Firebase's, or a broadcast's).
     */
    fun handle(
        context: Context,
        data: Map<String, String>,
    ) {
        val id = requestId(data) ?: return
        val container = context.appContainer
        val linked = runBlocking { container.links.get() } != null
        if (!linked) return
        SignInNotifications.show(context, id)
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            container.inbox.openRequest(id)
        }
    }
}

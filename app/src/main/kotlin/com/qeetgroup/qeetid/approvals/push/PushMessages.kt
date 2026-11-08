package com.qeetgroup.qeetid.approvals.push

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.appContainer
import com.qeetgroup.qeetid.approvals.BrowserName
import com.qeetgroup.qeetid.approvals.RequestInbox
import com.qeetgroup.qeetid.data.api.FetchResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

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
        SignInNotifications.show(context, id, describe(context, id))
        val inFront = ProcessLifecycleOwner.get().lifecycle.currentState
        if (inFront.isAtLeast(Lifecycle.State.STARTED)) container.inbox.openRequest(id)
    }

    /**
     * What's signing in and from where, for the notification ("Chrome on macOS, near Bengaluru, is signing in…"), so
     * a request nobody expected stands out before the app is open. The message carries only the id, so this reads the
     * request, briefly; null (the plain wording) when it can't.
     */
    private fun describe(
        context: Context,
        id: String,
    ): String? {
        val found =
            runBlocking { withTimeoutOrNull(LOOKUP_MILLIS) { context.appContainer.api.signInRequest(id) } } as? FetchResult.Found
                ?: return null
        val request = found.request
        val browser =
            BrowserName.describe(request.context.userAgent) { b, system -> context.getString(R.string.request_browser_on, b, system) }
                ?: return null
        val place = request.context.location?.takeIf { it.isNotBlank() }
        return if (place != null) {
            context.getString(R.string.notification_body_from_place, browser, place)
        } else {
            context.getString(R.string.notification_body_from, browser)
        }
    }

    private const val LOOKUP_MILLIS = 4_000L
}

package com.qeetgroup.qeetid.approvals

import com.qeetgroup.qeetid.data.api.PendingResult
import com.qeetgroup.qeetid.data.api.QeetIdApi
import com.qeetgroup.qeetid.data.api.SignInRequest
import com.qeetgroup.qeetid.ui.NamePhone
import com.qeetgroup.qeetid.ui.RequestsWaiting
import com.qeetgroup.qeetid.ui.SignInRequestRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Where the app should go next on its own: a sign-in request (from a notification tap, a push message while the app
 * is open, or the pending check) or a pairing link. The app opens [next] once it is unlocked and set up, then calls
 * [opened].
 *
 * The pending check asks the server for requests waiting for this phone when the app comes to the foreground, so a
 * request whose push was lost — or a phone with no push at all — still gets it. A request opened once is not opened
 * again by the automatic check; a pull to refresh opens it again. When more than one is waiting it opens Requests
 * waiting instead, so a second sign-in, or a flood of them, is seen together, not one at a time.
 */
class RequestInbox(
    private val links: PhoneLinkStore,
    private val api: QeetIdApi,
    private val scope: CoroutineScope,
    private val onUnlinkedByServer: suspend () -> Unit = {},
) {
    private val _next = MutableStateFlow<Any?>(null)
    private val seen = mutableSetOf<String>()

    /** The requests Requests waiting was opened for, marked seen when it opens. */
    private var waiting: List<String> = emptyList()
    private var checking: Job? = null

    val next: StateFlow<Any?> = _next.asStateFlow()

    /** Opens the sign-in request [id], if it is a request id. */
    fun openRequest(id: String) {
        if (isRequestId(id)) _next.value = SignInRequestRoute(id)
    }

    /** Opens the linking screen with [code], to ask before linking (a link must never link the phone by itself). */
    fun openPairing(code: String) {
        PairingCode.from(code)?.let { _next.value = NamePhone(it) }
    }

    /** The app went to [route]. */
    fun opened(route: Any) {
        if (route is SignInRequestRoute) synchronized(seen) { seen += route.id }
        if (route is RequestsWaiting) synchronized(seen) { seen += waiting }
        if (_next.value == route) _next.value = null
    }

    /**
     * Asks the server for waiting requests and opens the newest, or Requests waiting when there is more than one.
     * [explicit] (a pull to refresh) opens them even if they were opened before. Returns the job, so a refresh can show its spinner until it is done.
     */
    fun checkPending(explicit: Boolean = false): Job {
        checking?.takeIf { it.isActive && !explicit }?.let { return it }
        return scope
            .launch {
                val link = links.get() ?: return@launch
                when (val result = api.pendingRequests(link.deviceId, link.userId, link.deviceToken)) {
                    is PendingResult.Found -> {
                        val pending = result.requests.filter { it.status == SignInRequest.PENDING && isRequestId(it.id) }
                        val newest = pending.firstOrNull { explicit || synchronized(seen) { it.id !in seen } }
                        when {
                            newest == null -> {}

                            pending.size > 1 -> {
                                waiting = pending.map { it.id }
                                _next.value = RequestsWaiting
                            }

                            else -> {
                                _next.value = SignInRequestRoute(newest.id)
                            }
                        }
                    }

                    PendingResult.Unauthorized -> {
                        // The server no longer accepts this phone's credential: it approves nothing, so forget it.
                        links.clear()
                        onUnlinkedByServer()
                    }

                    PendingResult.NotSupported, PendingResult.Network -> {}
                }
            }.also { checking = it }
    }

    companion object {
        fun isRequestId(id: String): Boolean =
            try {
                UUID.fromString(id).toString().equals(id, ignoreCase = true)
            } catch (_: IllegalArgumentException) {
                false
            }
    }
}

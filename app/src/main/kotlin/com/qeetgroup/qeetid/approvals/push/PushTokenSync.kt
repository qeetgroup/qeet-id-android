package com.qeetgroup.qeetid.approvals.push

import com.qeetgroup.qeetid.approvals.PhoneLinkStore
import com.qeetgroup.qeetid.data.api.PushTokenResult
import com.qeetgroup.qeetid.data.api.QeetIdApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/**
 * Keeps the server's copy of this phone's push token current. Firebase replaces tokens now and then
 * (`onNewToken`); a linked phone sends the new one with its device credential to
 * `POST /v1/mfa/push/devices/{id}/push-token`. Until the server has it, requests go to the old token and are lost, so
 * the app also checks on every start ([syncCurrent]) and retries then if a send failed — offline, or a server that
 * does not have the route yet.
 */
class PushTokenSync(
    private val links: PhoneLinkStore,
    private val api: QeetIdApi,
    private val tokens: PushTokens,
    private val scope: CoroutineScope,
    private val onUnlinkedByServer: suspend () -> Unit = {},
) {
    private val mutex = Mutex()

    fun onNewToken(token: String) {
        scope.launch { sync(token) }
    }

    fun syncCurrent() {
        scope.launch { tokens.token()?.let { sync(it) } }
    }

    suspend fun sync(token: String): PushTokenResult? =
        mutex.withLock {
            val link = links.get() ?: return@withLock null
            val hash = hash(token)
            if (link.pushTokenHash == hash) return@withLock null
            val result = api.updatePushToken(link.deviceId, link.userId, link.deviceToken, token)
            when (result) {
                PushTokenResult.Updated -> links.update { it.copy(pushTokenHash = hash) }
                // The server no longer accepts this phone's credential: it was removed from the account, so the link
                // approves nothing. Forget it here too.
                PushTokenResult.Unauthorized -> {
                    links.clear()
                    tokens.stop()
                    onUnlinkedByServer()
                }
                else -> Unit
            }
            result
        }

    companion object {
        fun hash(token: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(token.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

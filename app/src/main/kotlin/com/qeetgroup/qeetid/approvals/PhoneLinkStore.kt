package com.qeetgroup.qeetid.approvals

import com.qeetgroup.qeetid.data.secure.SealedFiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * This phone's link to a Qeet ID account. [deviceToken] is a long-lived credential: with [userId] it approves sign-ins
 * with no signed-in session, until the server stops accepting it [LINK_DAYS] days after the phone was last linked. It is
 * kept sealed by the Keystore and never logged, shown or put in UI state.
 */
@Serializable
data class PhoneLink(
    val userId: String,
    val deviceId: String,
    val deviceToken: String,
    val deviceName: String,
    val linkedAtMillis: Long,
    val accountEmail: String? = null,
    /** SHA-256 of the push token the server last accepted for this phone, to know when a new one must be sent. */
    val pushTokenHash: String? = null,
) {
    @Transient
    val linkedUntilMillis: Long = linkedAtMillis + TimeUnit.DAYS.toMillis(LINK_DAYS)

    override fun toString(): String = "PhoneLink(deviceId=$deviceId, deviceName=$deviceName, linkedAtMillis=$linkedAtMillis)"

    companion object {
        const val LINK_DAYS = 180L
    }
}

/** What the screens may know about the link: never the credential. */
data class LinkSummary(
    val deviceName: String,
    val accountEmail: String?,
    val linkedUntilMillis: Long,
)

/** Keeps [PhoneLink] in one sealed file. */
class PhoneLinkStore(
    private val files: SealedFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _summary = MutableStateFlow<LinkSummary?>(null)
    private val _loaded = MutableStateFlow(false)

    /** The link as the screens show it; null when the phone is not linked. */
    val summary: StateFlow<LinkSummary?> = _summary.asStateFlow()

    /** Whether [summary] has been read from storage yet. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun get(): PhoneLink? =
        withContext(io) {
            mutex.withLock { read() }
        }

    suspend fun save(link: PhoneLink) =
        withContext(io) {
            mutex.withLock {
                files.write(FILE, json.encodeToString(PhoneLink.serializer(), link).toByteArray())
                publish(link)
            }
        }

    /** Applies [change] to the stored link, if there is one. */
    suspend fun update(change: (PhoneLink) -> PhoneLink) =
        withContext(io) {
            mutex.withLock {
                val current = read() ?: return@withLock
                val next = change(current)
                files.write(FILE, json.encodeToString(PhoneLink.serializer(), next).toByteArray())
                publish(next)
            }
        }

    suspend fun clear() =
        withContext(io) {
            mutex.withLock {
                files.delete(FILE)
                publish(null)
            }
        }

    private fun read(): PhoneLink? {
        val link =
            try {
                files.read(FILE)?.let { json.decodeFromString(PhoneLink.serializer(), it.decodeToString()) }
            } catch (_: SealedFiles.Unreadable) {
                // A credential this phone can no longer open approves nothing; linking again replaces it.
                files.delete(FILE)
                null
            }
        publish(link)
        return link
    }

    private fun publish(link: PhoneLink?) {
        _summary.value = link?.let { LinkSummary(it.deviceName, it.accountEmail, it.linkedUntilMillis) }
        _loaded.value = true
    }

    private companion object {
        const val FILE = "phone-link"
    }
}

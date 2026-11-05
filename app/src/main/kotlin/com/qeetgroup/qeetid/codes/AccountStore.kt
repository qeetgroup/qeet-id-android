package com.qeetgroup.qeetid.codes

import com.qeetgroup.qeetid.codes.totp.NewAccount
import com.qeetgroup.qeetid.codes.totp.Totp
import com.qeetgroup.qeetid.codes.totp.TotpAlgorithm
import com.qeetgroup.qeetid.codes.totp.TotpCode
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** An account on this phone: what is shown, and how its codes are made. Never its secret. */
@Serializable
data class Account(
    val id: String,
    val issuer: String,
    val label: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = Totp.MIN_DIGITS,
    val periodSeconds: Int = Totp.DEFAULT_PERIOD_SECONDS,
    val addedAtMillis: Long,
)

/**
 * The sign-in code accounts on this phone. The list (no secrets) is one sealed file; each account's secret is a sealed
 * file of its own (`secret-<id>`), so a secret is read only when its codes are needed and never travels with the list.
 *
 * Secrets read for codes are kept in a private map, never in UI state, and dropped by [forgetSecrets] when the app
 * locks.
 */
class AccountStore(
    private val files: SealedFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(Account.serializer())
    private val secrets = ConcurrentHashMap<String, ByteArray>()
    private val _accounts = MutableStateFlow<List<Account>?>(null)
    private val _unreadable = MutableStateFlow(false)

    /** The accounts, oldest first; null until [load] has read them. */
    val accounts: StateFlow<List<Account>?> = _accounts.asStateFlow()

    /** Whether the stored accounts could not be opened (the Keystore key is gone), so they were set aside. */
    val unreadable: StateFlow<Boolean> = _unreadable.asStateFlow()

    suspend fun load(): List<Account> =
        withContext(io) {
            mutex.withLock {
                _accounts.value ?: readList().also { _accounts.value = it }
            }
        }

    suspend fun add(account: NewAccount): Account =
        withContext(io) {
            mutex.withLock {
                val current = _accounts.value ?: readList()
                val added =
                    Account(
                        id = UUID.randomUUID().toString(),
                        issuer = account.issuer.trim(),
                        label = account.label.trim(),
                        algorithm = account.algorithm,
                        digits = account.digits,
                        periodSeconds = account.periodSeconds,
                        addedAtMillis = now(),
                    )
                // The secret first: a listed account always has one.
                files.write(secretFile(added.id), account.secret)
                writeList(current + added)
                added
            }
        }

    suspend fun rename(
        id: String,
        issuer: String,
        label: String,
    ) = withContext(io) {
        mutex.withLock {
            val current = _accounts.value ?: readList()
            writeList(current.map { if (it.id == id) it.copy(issuer = issuer.trim(), label = label.trim()) else it })
        }
    }

    suspend fun remove(id: String) =
        withContext(io) {
            mutex.withLock {
                val current = _accounts.value ?: readList()
                writeList(current.filterNot { it.id == id })
                files.delete(secretFile(id))
                secrets.remove(id)?.fill(0)
            }
        }

    /**
     * The current code for [account], computed in memory; null if its secret cannot be read. Reads the secret on first
     * use: call from a background dispatcher the first time (see [warmUp]).
     */
    fun code(
        account: Account,
        timeMillis: Long,
    ): TotpCode? {
        val key = secrets[account.id] ?: readSecret(account.id) ?: return null
        return Totp.code(key, timeMillis, account.periodSeconds, account.digits, account.algorithm)
    }

    /** Reads the secrets of [accounts] ahead of their first code, off the main thread. */
    suspend fun warmUp(accounts: List<Account>) =
        withContext(io) {
            accounts.forEach { if (!secrets.containsKey(it.id)) readSecret(it.id) }
        }

    /** Drops the secrets read for codes. They are read again, from the Keystore-sealed files, when next needed. */
    fun forgetSecrets() {
        secrets.values.forEach { it.fill(0) }
        secrets.clear()
    }

    private fun readSecret(id: String): ByteArray? =
        try {
            files.read(secretFile(id))?.also { secrets[id] = it }
        } catch (_: SealedFiles.Unreadable) {
            null
        }

    private fun readList(): List<Account> =
        try {
            files.read(LIST_FILE)?.let { json.decodeFromString(list, it.decodeToString()) } ?: emptyList()
        } catch (_: SealedFiles.Unreadable) {
            // Sealed by a key this phone no longer has: nothing in it can produce a code. Start again, and say so.
            _unreadable.value = true
            files.delete(LIST_FILE)
            emptyList()
        }

    private fun writeList(accounts: List<Account>) {
        files.write(LIST_FILE, json.encodeToString(list, accounts).toByteArray())
        _accounts.value = accounts
    }

    private fun secretFile(id: String) = "secret-$id"

    private companion object {
        const val LIST_FILE = "accounts"
    }
}

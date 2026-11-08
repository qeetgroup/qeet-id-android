package com.qeetgroup.qeetid.codes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.approvals.LinkSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One account on the home screen, with its current code. Never its secret. */
data class AccountRow(
    val id: String,
    val issuer: String,
    val label: String,
    val code: String?,
    val expiresAtMillis: Long,
    val periodSeconds: Int,
)

data class AccountsState(
    /** False until the accounts have been read from storage. */
    val loaded: Boolean = false,
    /** The accounts matching [query], oldest first. */
    val rows: List<AccountRow> = emptyList(),
    /** How many accounts there are on the phone, whatever the search. */
    val total: Int = 0,
    val query: String = "",
    val link: LinkSummary? = null,
    val hideCodes: Boolean = false,
    val online: Boolean = true,
    /** The stored accounts could not be opened (the Keystore was reset) and were set aside. */
    val vaultReset: Boolean = false,
    /** Qeet ID removed this phone's link, so requests stop; codes still work. */
    val unlinkedByQeetId: Boolean = false,
)

/** What the screen says once, in a snackbar. */
sealed interface AccountsMessage {
    data object Copied : AccountsMessage

    data class Deleted(
        val issuer: String,
    ) : AccountsMessage
}

/**
 * The home screen: the accounts and their codes, the link, and the problems that stop requests reaching the phone.
 * Codes are computed here each second from secrets the [AccountStore] keeps to itself.
 */
class AccountsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val store = container.accounts
    private val query = MutableStateFlow("")
    private val messages = Channel<AccountsMessage>(Channel.BUFFERED)

    private val clock: Flow<Long> =
        flow {
            while (true) {
                val now = container.now()
                emit(now)
                delay(TICK_MILLIS - now % TICK_MILLIS)
            }
        }

    private val accounts =
        store.accounts
            .filterNotNull()
            .onEach { store.warmUp(it) }

    val state: StateFlow<AccountsState> =
        combine(
            combine(accounts, query, clock) { list, q, now -> Triple(list, q, now) },
            combine(
                container.links.summary,
                container.preferences.hideCodes,
                container.connectivity.online,
                store.unreadable,
                container.preferences.unlinkedByQeetId,
            ) { link, hide, online, reset, unlinked ->
                Settings(link, hide, online, reset, unlinked && link == null)
            },
        ) { (list, q, now), settings ->
            val needle = q.trim()
            val matching =
                if (needle.isEmpty()) {
                    list
                } else {
                    list.filter { it.issuer.contains(needle, ignoreCase = true) || it.label.contains(needle, ignoreCase = true) }
                }
            AccountsState(
                loaded = true,
                rows =
                    matching.map { account ->
                        val code = store.code(account, now)
                        AccountRow(
                            id = account.id,
                            issuer = account.issuer,
                            label = account.label,
                            code = code?.code,
                            expiresAtMillis = code?.expiresAtMillis ?: now,
                            periodSeconds = account.periodSeconds,
                        )
                    },
                total = list.size,
                query = q,
                link = settings.link,
                hideCodes = settings.hideCodes,
                online = settings.online,
                vaultReset = settings.vaultReset,
                unlinkedByQeetId = settings.unlinkedByQeetId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AccountsState())

    val events: Flow<AccountsMessage> = messages.receiveAsFlow()

    init {
        viewModelScope.launch { store.load() }
        viewModelScope.launch { container.links.get() }
    }

    fun search(text: String) {
        query.value = text
    }

    /** Copies [row]'s code from the actions sheet. */
    fun copy(row: AccountRow) {
        val code = row.code ?: return
        container.clipboard.copy(code, listOf(row.issuer, row.label).filter { it.isNotBlank() }.joinToString(" · "))
        messages.trySend(AccountsMessage.Copied)
    }

    /** The code's own tap copied it (Qeetrix's code copies, marked sensitive, and clears it 30 s later): say so. */
    fun copied() {
        messages.trySend(AccountsMessage.Copied)
    }

    fun rename(
        id: String,
        issuer: String,
        label: String,
    ): Job = viewModelScope.launch { store.rename(id, issuer, label) }

    fun delete(row: AccountRow): Job =
        viewModelScope.launch {
            store.remove(row.id)
            messages.trySend(AccountsMessage.Deleted(row.issuer))
        }

    /** Pull to refresh: asks the server for a waiting sign-in request, and opens it. */
    fun refresh(): Job = container.inbox.checkPending(explicit = true)

    private data class Settings(
        val link: LinkSummary?,
        val hideCodes: Boolean,
        val online: Boolean,
        val vaultReset: Boolean,
        val unlinkedByQeetId: Boolean,
    )

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

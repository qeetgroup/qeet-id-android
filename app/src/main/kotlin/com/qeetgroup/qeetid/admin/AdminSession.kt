package com.qeetgroup.qeetid.admin

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
import kotlinx.serialization.json.Json

/** Who is signed in to administration, and in which organisation, as the screens show it. */
data class AdminSummary(
    val email: String,
    val organizationId: String?,
    val organizationName: String?,
)

/** How signing in went. */
sealed interface SignInOutcome {
    /** Signed in; [organizations] are the ones the person has a role in. */
    data class SignedIn(
        val organizations: List<Organization>,
    ) : SignInOutcome

    /** Signed in, but they administer no organisation: signed out again. */
    data object NotAnAdministrator : SignInOutcome

    data object Cancelled : SignInOutcome

    data object NoPasskey : SignInOutcome

    data object PasskeyUnavailable : SignInOutcome

    data object Network : SignInOutcome

    data object Refused : SignInOutcome
}

/** The stored session: the refresh token and where it is scoped. Sealed with the Keystore key, never in UI state. */
@Serializable
private data class StoredSession(
    val refreshToken: String,
    val email: String,
    val organizationId: String? = null,
    val organizationName: String? = null,
)

/**
 * The administrator's own Qeet ID session on this phone, apart from the phone's link (which approves sign-ins and
 * holds no session). It signs in with a passkey, keeps the refresh token sealed in its own file and the access token in
 * memory only, renews it when it runs out, and runs each administration call with it. A step-up (a fresh passkey
 * check) goes before anything that changes access.
 */
class AdminSession(
    private val files: SealedFiles,
    internal val api: AdminApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var access: String? = null
    private val _summary = MutableStateFlow<AdminSummary?>(null)

    /** Who is signed in, and where; null when nobody is. */
    val summary: StateFlow<AdminSummary?> = _summary.asStateFlow()

    /** Reads the stored session, so [summary] is right after the app starts. */
    suspend fun load() {
        withContext(io) { mutex.withLock { read() } }
    }

    /** Signs in with a passkey; on success the session is scoped to the person's own organisation until one is chosen. */
    suspend fun signIn(passkeys: PasskeyPrompt): SignInOutcome {
        val ceremony =
            when (val begun = api.beginSignIn()) {
                is AdminResult.Ok -> begun.value
                AdminResult.Network -> return SignInOutcome.Network
                else -> return SignInOutcome.Refused
            }
        val signed =
            when (val result = passkeys.sign(ceremony.publicKey.toString())) {
                is PasskeyResult.Signed -> result.json
                PasskeyResult.Cancelled -> return SignInOutcome.Cancelled
                PasskeyResult.NoPasskey -> return SignInOutcome.NoPasskey
                PasskeyResult.Unavailable -> return SignInOutcome.PasskeyUnavailable
            }
        val pair =
            when (val finished = api.finishSignIn(ceremony.sessionId, signed)) {
                is AdminResult.Ok -> finished.value
                AdminResult.Network -> return SignInOutcome.Network
                else -> return SignInOutcome.Refused
            }
        val email = (api.me(pair.accessToken) as? AdminResult.Ok)?.value?.email.orEmpty()
        val organizations =
            when (val listed = api.organizations(pair.accessToken)) {
                is AdminResult.Ok -> listed.value.filter { it.roles.isNotEmpty() }
                AdminResult.Network -> return SignInOutcome.Network.also { api.signOut(pair.accessToken) }
                else -> return SignInOutcome.Refused.also { api.signOut(pair.accessToken) }
            }
        if (organizations.isEmpty()) {
            api.signOut(pair.accessToken)
            return SignInOutcome.NotAnAdministrator
        }
        store(pair, StoredSession(pair.refreshToken, email))
        return SignInOutcome.SignedIn(organizations)
    }

    /** The organisations the signed-in person has a role in. */
    suspend fun organizations(): AdminResult<List<Organization>> =
        run { access ->
            api.organizations(access)
        }.map { list -> list.filter { it.roles.isNotEmpty() } }

    /** Scopes the session to [organization]: every call after it acts there. */
    suspend fun choose(organization: Organization): AdminResult<Unit> {
        val pair =
            when (val switched = run { access -> api.switchOrganization(access, organization.id) }) {
                is AdminResult.Ok -> switched.value
                else -> return switched.map { }
            }
        val current = withContext(io) { mutex.withLock { read() } } ?: return AdminResult.SignedOut
        store(pair, current.copy(refreshToken = pair.refreshToken, organizationId = organization.id, organizationName = organization.name))
        return AdminResult.Ok(Unit)
    }

    /**
     * Runs [action] with the session in its organisation: renews the access token when it has run out, once, and
     * forgets the session when Qeet ID has ended it.
     */
    internal suspend fun <T> inOrganization(action: suspend (access: String, tenant: String) -> AdminResult<T>): AdminResult<T> {
        val tenant = _summary.value?.organizationId ?: return AdminResult.SignedOut
        return run { access -> action(access, tenant) }
    }

    /** A fresh passkey check for this session, which Qeet ID asks for before access is given or taken back. */
    suspend fun stepUp(passkeys: PasskeyPrompt): StepUpOutcome {
        val ceremony =
            when (val begun = run { access -> api.beginStepUp(access) }) {
                is AdminResult.Ok -> begun.value
                AdminResult.SignedOut -> return StepUpOutcome.SignedOut
                AdminResult.Network -> return StepUpOutcome.Network
                else -> return StepUpOutcome.Refused
            }
        val signed =
            when (val result = passkeys.sign(ceremony.publicKey.toString())) {
                is PasskeyResult.Signed -> result.json
                PasskeyResult.Cancelled -> return StepUpOutcome.Cancelled
                PasskeyResult.NoPasskey, PasskeyResult.Unavailable -> return StepUpOutcome.Unavailable
            }
        return when (run { access -> api.finishStepUp(access, ceremony.sessionId, signed) }) {
            is AdminResult.Ok -> StepUpOutcome.Done
            AdminResult.SignedOut -> StepUpOutcome.SignedOut
            AdminResult.Network -> StepUpOutcome.Network
            else -> StepUpOutcome.Refused
        }
    }

    /** Ends the session on Qeet ID and forgets it here, whatever the server answers. */
    suspend fun signOut() {
        val token = access
        if (token != null) api.signOut(token)
        forget()
    }

    private suspend fun <T> run(action: suspend (access: String) -> AdminResult<T>): AdminResult<T> {
        val first = access ?: renew() ?: return AdminResult.SignedOut
        val result = action(first)
        if (result !is AdminResult.SignedOut) return result
        // The access token ran out: renew it once and try again.
        val renewed = renew() ?: return AdminResult.SignedOut
        return action(renewed)
    }

    /** A new access token from the stored refresh token; null (and the session forgotten) when Qeet ID ended it. */
    private suspend fun renew(): String? {
        val stored = withContext(io) { mutex.withLock { read() } } ?: return null
        return when (val renewed = api.refresh(stored.refreshToken)) {
            is AdminResult.Ok -> {
                store(renewed.value, stored.copy(refreshToken = renewed.value.refreshToken))
                renewed.value.accessToken
            }

            AdminResult.Network -> {
                null
            }

            else -> {
                forget()
                null
            }
        }
    }

    private suspend fun store(
        pair: TokenPair,
        session: StoredSession,
    ) = withContext(io) {
        mutex.withLock {
            files.write(FILE, json.encodeToString(StoredSession.serializer(), session).toByteArray())
            access = pair.accessToken
            publish(session)
        }
    }

    private suspend fun forget() =
        withContext(io) {
            mutex.withLock {
                files.delete(FILE)
                access = null
                publish(null)
            }
        }

    private fun read(): StoredSession? {
        val session =
            try {
                files.read(FILE)?.let { json.decodeFromString(StoredSession.serializer(), it.decodeToString()) }
            } catch (_: SealedFiles.Unreadable) {
                files.delete(FILE)
                null
            }
        publish(session)
        return session
    }

    private fun publish(session: StoredSession?) {
        _summary.value = session?.let { AdminSummary(it.email, it.organizationId, it.organizationName) }
    }

    private companion object {
        const val FILE = "admin-session"
    }
}

/** How a step-up went. */
enum class StepUpOutcome { Done, Cancelled, Unavailable, SignedOut, Network, Refused }

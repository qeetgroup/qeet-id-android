package com.qeetgroup.qeetid.admin

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How an administration call went. */
sealed interface AdminResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : AdminResult<T>

    /** The session is gone (signed out, timed out or revoked): sign in again. */
    data object SignedOut : AdminResult<Nothing>

    /** The action needs a fresh passkey check first (403 step_up_required). */
    data object StepUpRequired : AdminResult<Nothing>

    /** The person may not do this in this organisation. */
    data object Forbidden : AdminResult<Nothing>

    data object NotFound : AdminResult<Nothing>

    /** It changed meanwhile: already decided, no longer suspended. */
    data class Conflict(
        val code: String?,
    ) : AdminResult<Nothing>

    data object Network : AdminResult<Nothing>

    data class Refused(
        val code: String?,
    ) : AdminResult<Nothing>
}

/**
 * The Qeet ID routes the administration screens call, with the administrator's own session: the token form of passkey
 * sign-in, renewal and switching organisation, the passkey step-up, access requests, critical events, suspending a
 * person and the AI agent kill switch. The bearer token is passed in by [AdminSession] for each call; nothing here
 * keeps or logs it, or a body.
 */
class AdminApi(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
    private val userAgent: String,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    // Signing in

    internal suspend fun beginSignIn(): AdminResult<Ceremony> =
        call(post(url("passkeys", "login", "begin"), null, EMPTY), Ceremony.serializer())

    internal suspend fun finishSignIn(
        ceremony: String,
        credential: String,
    ): AdminResult<TokenPair> =
        call(post(url("passkeys", "login", "finish"), null, ceremonyBody(ceremony, credential)), TokenPair.serializer())

    internal suspend fun refresh(refreshToken: String): AdminResult<TokenPair> =
        call(post(url("auth", "refresh"), null, body { put("refresh_token", refreshToken) }), TokenPair.serializer())

    internal suspend fun switchOrganization(
        access: String,
        tenantId: String,
    ): AdminResult<TokenPair> =
        call(post(url("auth", "switch-tenant"), access, body { put("tenant_id", tenantId) }), TokenPair.serializer())

    internal suspend fun signOut(access: String): AdminResult<Unit> = call(request(url("auth", "session"), access).delete().build())

    internal suspend fun me(access: String): AdminResult<Me> = call(get(url("me"), access), Me.serializer())

    internal suspend fun organizations(access: String): AdminResult<List<Organization>> =
        call(get(url("me", "organizations"), access), OrganizationPage.serializer()).map { it.items }

    /** A passkey check for this session, which the server counts as a step-up for a few minutes. */
    internal suspend fun beginStepUp(access: String): AdminResult<Ceremony> =
        call(post(url("mfa", "webauthn", "challenge"), access, EMPTY_OBJECT), Ceremony.serializer())

    internal suspend fun finishStepUp(
        access: String,
        ceremony: String,
        credential: String,
    ): AdminResult<Unit> = call(post(url("mfa", "webauthn", "verify"), access, ceremonyBody(ceremony, credential)))

    // Access requests

    internal suspend fun accessRequests(
        access: String,
        tenant: String,
    ): AdminResult<AccessRequestPage> =
        call(
            get(
                url("tenants", tenant, "access-requests")
                    .newBuilder()
                    .addQueryParameter("status", AccessRequest.PENDING)
                    .build(),
                access,
            ),
            AccessRequestPage.serializer(),
        )

    internal suspend fun accessRequest(
        access: String,
        tenant: String,
        id: String,
    ): AdminResult<AccessRequest> =
        call(get(url("tenants", tenant, "access-requests", id), access), AccessRequestOne.serializer()).map {
            it.item
        }

    internal suspend fun approve(
        access: String,
        tenant: String,
        id: String,
        note: String,
    ): AdminResult<AccessRequest> =
        call(
            post(
                url("tenants", tenant, "access-requests", id, "approve"),
                access,
                body {
                    put("note", note)
                },
            ),
            AccessRequestOne.serializer(),
        ).map {
            it.item
        }

    internal suspend fun deny(
        access: String,
        tenant: String,
        id: String,
        note: String,
    ): AdminResult<AccessRequest> =
        call(
            post(url("tenants", tenant, "access-requests", id, "deny"), access, body { put("note", note) }),
            AccessRequestOne.serializer(),
        ).map {
            it.item
        }

    // Critical events

    internal suspend fun criticalEvents(
        access: String,
        tenant: String,
    ): AdminResult<CriticalEventPage> = call(get(url("tenants", tenant, "critical-events"), access), CriticalEventPage.serializer())

    // People

    internal suspend fun members(
        access: String,
        query: String,
    ): AdminResult<List<Member>> {
        val url =
            url("users")
                .newBuilder()
                .addQueryParameter("limit", "25")
                .addQueryParameter("sort", "name")
                .apply { if (query.isNotBlank()) addQueryParameter("q", query.trim()) }
                .build()
        return call(get(url, access), MemberPage.serializer()).map { it.items }
    }

    internal suspend fun member(
        access: String,
        id: String,
    ): AdminResult<Member> = call(get(url("users", id), access), Member.serializer())

    /** Suspends [id]: signed out everywhere, and no sign-in until reactivated. */
    internal suspend fun suspend(
        access: String,
        id: String,
    ): AdminResult<Unit> = call(request(url("users", id), access).patch(body { put("status", "suspended") }).build())

    /** Records why [id] was suspended, for whoever reviews it. */
    internal suspend fun recordSuspension(
        access: String,
        id: String,
        reason: SuspendReason,
    ): AdminResult<Unit> =
        call(
            request(url("users", id, "suspension"), access)
                .put(
                    body {
                        put("reason", reason.wire)
                        put("risk", "high")
                        put("review", "pending")
                    },
                ).build(),
        )

    internal suspend fun reactivate(
        access: String,
        id: String,
    ): AdminResult<Unit> = call(post(url("users", id, "reactivate"), access, EMPTY_OBJECT))

    // AI agents

    /** How many of the organisation's agents can act now. */
    internal suspend fun runningAgents(
        access: String,
        tenant: String,
    ): AdminResult<Int> =
        call(get(url("tenants", tenant, "agents"), access), AgentPage.serializer()).map { page ->
            page.items.count { !it.disabled }
        }

    internal suspend fun stopAllAgents(
        access: String,
        tenant: String,
    ): AdminResult<Int> =
        call(post(url("tenants", tenant, "agents", "kill-all"), access, EMPTY_OBJECT), AgentsStopped.serializer()).map {
            it.suspended
        }

    // Plumbing

    private fun url(vararg segments: String): HttpUrl =
        baseUrl
            .newBuilder()
            .addPathSegment("v1")
            .apply { segments.forEach(::addPathSegment) }
            .build()

    private fun request(
        url: HttpUrl,
        access: String?,
    ) = Request
        .Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("User-Agent", userAgent)
        .apply { if (access != null) header("Authorization", "Bearer $access") }

    private fun get(
        url: HttpUrl,
        access: String,
    ): Request = request(url, access).get().build()

    private fun post(
        url: HttpUrl,
        access: String?,
        body: RequestBody,
    ): Request = request(url, access).post(body).build()

    private fun body(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): RequestBody =
        buildJsonObject(build).toString().toRequestBody(JSON)

    private fun ceremonyBody(
        ceremony: String,
        credential: String,
    ): RequestBody {
        // The credential goes as the JSON the passkey prompt returned, not as a string.
        val parsed = json.parseToJsonElement(credential)
        return JsonObject(mapOf("session_id" to JsonPrimitive(ceremony), "credential" to parsed)).toString().toRequestBody(JSON)
    }

    private suspend fun call(request: Request): AdminResult<Unit> = call(request) { }

    private suspend fun <T> call(
        request: Request,
        serializer: KSerializer<T>,
    ): AdminResult<T> =
        call(request) { res ->
            try {
                json.decodeFromString(serializer, res.body.string())
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.let { result ->
            if (result is AdminResult.Ok && result.value == null) AdminResult.Refused(null) else result.map { it!! }
        }

    private suspend fun <T> call(
        request: Request,
        read: (Response) -> T,
    ): AdminResult<T> =
        try {
            client.newCall(request).await().use { res ->
                if (res.isSuccessful) return AdminResult.Ok(read(res))
                val code = errorCode(res)
                when {
                    res.code == 401 -> AdminResult.SignedOut
                    res.code == 403 && code == STEP_UP_REQUIRED -> AdminResult.StepUpRequired
                    res.code == 403 -> AdminResult.Forbidden
                    res.code == 404 -> AdminResult.NotFound
                    res.code == 409 -> AdminResult.Conflict(code)
                    else -> AdminResult.Refused(code)
                }
            }
        } catch (_: IOException) {
            AdminResult.Network
        }

    private fun errorCode(res: Response): String? =
        try {
            json
                .parseToJsonElement(res.body.string())
                .jsonObject["error"]
                ?.jsonObject
                ?.get("code")
                ?.jsonPrimitive
                ?.content
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        /** An empty body: a discoverable passkey sign-in, with no email asked for. */
        val EMPTY: RequestBody = ByteArray(0).toRequestBody(JSON)
        val EMPTY_OBJECT: RequestBody = "{}".toRequestBody(JSON)
        const val STEP_UP_REQUIRED = "step_up_required"
    }
}

internal inline fun <T, R> AdminResult<T>.map(transform: (T) -> R): AdminResult<R> =
    when (this) {
        is AdminResult.Ok -> AdminResult.Ok(transform(value))
        is AdminResult.SignedOut -> this
        is AdminResult.StepUpRequired -> this
        is AdminResult.Forbidden -> this
        is AdminResult.NotFound -> this
        is AdminResult.Conflict -> this
        is AdminResult.Network -> this
        is AdminResult.Refused -> this
    }

/** The next call, when this one went through. */
internal inline fun <T, R> AdminResult<T>.then(next: (T) -> AdminResult<R>): AdminResult<R> =
    when (this) {
        is AdminResult.Ok -> next(value)
        is AdminResult.SignedOut -> this
        is AdminResult.StepUpRequired -> this
        is AdminResult.Forbidden -> this
        is AdminResult.NotFound -> this
        is AdminResult.Conflict -> this
        is AdminResult.Network -> this
        is AdminResult.Refused -> this
    }

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    continuation.resumeWithException(e)
                }
            },
        )
    }

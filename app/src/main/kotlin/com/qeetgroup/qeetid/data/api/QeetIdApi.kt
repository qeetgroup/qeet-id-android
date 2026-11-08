package com.qeetgroup.qeetid.data.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Linking with a pairing code. */
sealed interface ClaimResult {
    data class Linked(
        val userId: String,
        val deviceId: String,
        val deviceToken: String,
        val accountEmail: String?,
    ) : ClaimResult {
        override fun toString(): String = "Linked(deviceId=$deviceId)"
    }

    /** Wrong, already used, replaced, withdrawn or expired: the server does not tell these apart. */
    data object InvalidCode : ClaimResult

    data class Throttled(
        val retryAfterSeconds: Int,
    ) : ClaimResult

    data object Network : ClaimResult

    data class Refused(
        val message: String?,
    ) : ClaimResult
}

/** Reading a sign-in request. */
sealed interface FetchResult {
    /** [expiresAtLocalMillis] is when it stops being answerable by this phone's clock, corrected by the server's. */
    data class Found(
        val request: SignInRequest,
        val expiresAtLocalMillis: Long,
    ) : FetchResult

    data object NotFound : FetchResult

    data object Network : FetchResult
}

/** Answering a sign-in request. */
sealed interface RespondResult {
    data object Done : RespondResult

    /** The number was not the one on the sign-in screen: the server refused the sign-in. */
    data object NumberMismatch : RespondResult

    /** Past its two minutes, or so old it was removed. */
    data object Expired : RespondResult

    data object AlreadyAnswered : RespondResult

    /** This phone's credential does not answer for the account that sent it (unlinked, or another account). */
    data object NotThisAccount : RespondResult

    data object Network : RespondResult

    data class Refused(
        val message: String?,
    ) : RespondResult
}

/** This phone removing itself from the account. */
sealed interface UnlinkResult {
    data object Removed : UnlinkResult

    /** The server no longer knew this phone (removed by an administrator, or an MFA reset). */
    data object AlreadyRemoved : UnlinkResult

    data object Network : UnlinkResult

    data class Refused(
        val message: String?,
    ) : UnlinkResult
}

/** Asking the server for sign-in requests waiting for this phone. */
sealed interface PendingResult {
    /**
     * Pending, unexpired requests, newest first (possibly none). [clockOffsetMillis] is the server's clock less the
     * phone's, from the answer's Date header, so each request's time left is right even when the phone's clock is not.
     */
    data class Found(
        val requests: List<SignInRequest>,
        val clockOffsetMillis: Long = 0L,
    ) : PendingResult

    /** The server has no such route yet (an older server). */
    data object NotSupported : PendingResult

    /** The credential is no longer accepted: the phone was removed from the account, or its link expired. */
    data object Unauthorized : PendingResult

    data object Network : PendingResult
}

/** Sending the server this phone's new push token. */
sealed interface PushTokenResult {
    data object Updated : PushTokenResult

    /** The server has no such route yet (an older server): try again after it is updated. */
    data object NotSupported : PushTokenResult

    /** The credential is no longer accepted: the phone was removed from the account. */
    data object Unauthorized : PushTokenResult

    data object Network : PushTokenResult

    data class Refused(
        val message: String?,
    ) : PushTokenResult
}

/**
 * The Qeet ID API routes this phone calls. None carries a bearer token or a cookie: linking is authorised by the
 * one-time pairing code, and everything after it by the device credential in the body (the design, see
 * qeet-id-server's internal/access/mfa/push.go). Nothing here logs a body, a code or a credential.
 *
 * Every call returns a result and never throws for a network or server failure.
 */
class QeetIdApi(
    internal val baseUrl: HttpUrl,
    internal val client: OkHttpClient = defaultClient(),
    internal val userAgent: String = "QeetID-Android",
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _updateRequired = MutableStateFlow(false)

    /** Qeet ID answered 426 Upgrade Required: it no longer accepts this version for sign-in approvals. */
    val updateRequired: StateFlow<Boolean> = _updateRequired.asStateFlow()

    private val json =
        Json {
            ignoreUnknownKeys = true
            // Send every field, defaults included ("platform": "android"); a null (an unset number) is left out.
            encodeDefaults = true
            explicitNulls = false
        }

    suspend fun claimPairing(
        code: String,
        name: String,
        pushToken: String,
    ): ClaimResult {
        val body = encode(ClaimRequest.serializer(), ClaimRequest(code, name, pushToken))
        return try {
            post(url("mfa", "push", "pairing", "claim"), body).use { res ->
                when {
                    res.isSuccessful -> {
                        val claim = decode(ClaimResponse.serializer(), res) ?: return ClaimResult.Refused(null)
                        if (claim.userId.isBlank() || claim.device.id.isBlank() || claim.deviceToken.isBlank()) {
                            ClaimResult.Refused(null)
                        } else {
                            ClaimResult.Linked(
                                claim.userId,
                                claim.device.id,
                                claim.deviceToken,
                                claim.account?.email?.takeIf { it.isNotBlank() },
                            )
                        }
                    }

                    else -> {
                        val error = error(res)
                        when {
                            res.code == 401 && error?.code == "mfa.pairing_code_invalid" -> ClaimResult.InvalidCode
                            res.code == 429 -> ClaimResult.Throttled(retryAfter(error, res))
                            else -> ClaimResult.Refused(error?.message)
                        }
                    }
                }
            }
        } catch (_: IOException) {
            ClaimResult.Network
        }
    }

    suspend fun signInRequest(id: String): FetchResult =
        try {
            get(url("mfa", "push", "challenges", id)).use { res ->
                when {
                    res.code == 404 || res.code == 400 -> {
                        FetchResult.NotFound
                    }

                    !res.isSuccessful -> {
                        FetchResult.Network
                    }

                    else -> {
                        val request = decode(SignInRequest.serializer(), res) ?: return FetchResult.Network
                        val expires = parseTime(request.expiresAt) ?: return FetchResult.Network
                        // The server's clock, from its Date header, so the time left is right even when the phone's is not.
                        val offset =
                            res.headers
                                .getDate("Date")
                                ?.time
                                ?.let { it - now() } ?: 0L
                        FetchResult.Found(request, expires - offset)
                    }
                }
            }
        } catch (_: IOException) {
            FetchResult.Network
        }

    suspend fun respond(
        id: String,
        userId: String,
        deviceToken: String,
        approved: Boolean,
        number: Int?,
    ): RespondResult {
        val body = encode(RespondRequest.serializer(), RespondRequest(userId, deviceToken, approved, if (approved) number else null))
        return try {
            post(url("mfa", "push", "challenges", id, "respond"), body).use { res ->
                if (res.isSuccessful) return RespondResult.Done
                val error = error(res)
                when {
                    res.code == 403 && error?.code == "mfa.push_number_mismatch" -> RespondResult.NumberMismatch
                    res.code == 410 || res.code == 404 -> RespondResult.Expired
                    res.code == 409 -> RespondResult.AlreadyAnswered
                    res.code == 401 -> RespondResult.NotThisAccount
                    res.code in 500..599 -> RespondResult.Network
                    else -> RespondResult.Refused(error?.message)
                }
            }
        } catch (_: IOException) {
            RespondResult.Network
        }
    }

    suspend fun unlink(
        deviceId: String,
        userId: String,
        deviceToken: String,
    ): UnlinkResult {
        val body = encode(CredentialBody.serializer(), CredentialBody(userId, deviceToken))
        return try {
            post(url("mfa", "push", "devices", deviceId, "unlink"), body).use { res ->
                if (res.isSuccessful) return UnlinkResult.Removed
                val error = error(res)
                when {
                    res.code == 401 && error?.code == PUSH_UNAUTHORIZED -> UnlinkResult.AlreadyRemoved
                    res.code in 500..599 -> UnlinkResult.Network
                    else -> UnlinkResult.Refused(error?.message)
                }
            }
        } catch (_: IOException) {
            UnlinkResult.Network
        }
    }

    /**
     * The sign-in requests waiting for this phone (`POST /v1/mfa/push/devices/{id}/pending`), asked for when the app
     * comes to the foreground: the way a request reaches a phone whose push message was lost, or that has no push.
     */
    suspend fun pendingRequests(
        deviceId: String,
        userId: String,
        deviceToken: String,
    ): PendingResult {
        val body = encode(CredentialBody.serializer(), CredentialBody(userId, deviceToken))
        return try {
            post(url("mfa", "push", "devices", deviceId, "pending"), body).use { res ->
                if (res.isSuccessful) {
                    val pending = decode(PendingResponse.serializer(), res) ?: return PendingResult.Network
                    val offset =
                        res.headers
                            .getDate("Date")
                            ?.time
                            ?.let { it - now() } ?: 0L
                    return PendingResult.Found(pending.challenges, offset)
                }
                val error = error(res)
                when {
                    (res.code == 404 || res.code == 405) && error?.code == null -> PendingResult.NotSupported

                    // Only Qeet ID's own answer forgets the link; a 401 from a proxy or gateway on the way must not.
                    res.code == 401 && error?.code == PUSH_UNAUTHORIZED -> PendingResult.Unauthorized

                    else -> PendingResult.Network
                }
            }
        } catch (_: IOException) {
            PendingResult.Network
        }
    }

    suspend fun updatePushToken(
        deviceId: String,
        userId: String,
        deviceToken: String,
        pushToken: String,
    ): PushTokenResult {
        val body = encode(PushTokenRequest.serializer(), PushTokenRequest(userId, deviceToken, pushToken))
        return try {
            post(url("mfa", "push", "devices", deviceId, "push-token"), body).use { res ->
                if (res.isSuccessful) return PushTokenResult.Updated
                val error = error(res)
                when {
                    // A route the server does not have answers 404 or 405 with no error code of Qeet ID's.
                    (res.code == 404 || res.code == 405) && error?.code == null -> PushTokenResult.NotSupported

                    res.code == 401 && error?.code == PUSH_UNAUTHORIZED -> PushTokenResult.Unauthorized

                    res.code in 500..599 -> PushTokenResult.Network

                    else -> PushTokenResult.Refused(error?.message)
                }
            }
        } catch (_: IOException) {
            PushTokenResult.Network
        }
    }

    private fun url(vararg segments: String): HttpUrl =
        baseUrl
            .newBuilder()
            .addPathSegment("v1")
            .apply { segments.forEach(::addPathSegment) }
            .build()

    private suspend fun get(url: HttpUrl): Response = execute(request(url).get().build())

    private suspend fun post(
        url: HttpUrl,
        body: String,
    ): Response = execute(request(url).post(body.toRequestBody(JSON)).build())

    private fun request(url: HttpUrl) =
        Request
            .Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)

    private fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
    ): String = json.encodeToString(serializer, value)

    private fun <T> decode(
        serializer: KSerializer<T>,
        res: Response,
    ): T? =
        try {
            json.decodeFromString(serializer, res.body.string())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun error(res: Response): ErrorBody? = decode(ErrorEnvelope.serializer(), res)?.error

    private fun retryAfter(
        error: ErrorBody?,
        res: Response,
    ): Int {
        val fromBody =
            error
                ?.metadata
                ?.get("retry_after")
                ?.jsonPrimitive
                ?.intOrNull
        val fromHeader = res.header("Retry-After")?.toIntOrNull()
        return (fromBody ?: fromHeader)?.takeIf { it > 0 } ?: DEFAULT_RETRY_SECONDS
    }

    private fun parseTime(text: String): Long? =
        try {
            OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val DEFAULT_RETRY_SECONDS = 60
        private const val HTTP_UPGRADE_REQUIRED = 426

        /** The server's answer when this phone's device credential is wrong or expired: the link is gone. */
        private const val PUSH_UNAUTHORIZED = "mfa.push_unauthorized"

        fun defaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                // The device credential travels in request bodies; a redirect must never carry it elsewhere.
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }

    private suspend fun execute(request: Request): Response {
        val response = client.newCall(request).await()
        if (response.code == HTTP_UPGRADE_REQUIRED) _updateRequired.value = true
        return response
    }
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

package com.qeetgroup.qeetid.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The request and response bodies of the routes a phone calls, as qeet-id-server's api/openapi/auth.yaml and
// internal/access/mfa/{pairing,push}.go define them. The phone holds no signed-in session: the pairing code links it,
// and the device credential (user_id + device_token in the body) answers for it after that.

@Serializable
internal data class ClaimRequest(
    val code: String,
    val name: String,
    @SerialName("push_token") val pushToken: String,
    val platform: String = "android",
)

@Serializable
internal data class ClaimResponse(
    @SerialName("user_id") val userId: String,
    val device: DeviceBody,
    @SerialName("device_token") val deviceToken: String,
    /** Newer servers name the account; older ones do not. */
    val account: AccountBody? = null,
)

@Serializable
internal data class DeviceBody(
    val id: String,
    val name: String = "",
)

@Serializable
internal data class AccountBody(
    val email: String? = null,
)

@Serializable
internal data class CredentialBody(
    @SerialName("user_id") val userId: String,
    @SerialName("device_token") val deviceToken: String,
)

@Serializable
internal data class RespondRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("device_token") val deviceToken: String,
    val approved: Boolean,
    /** The number shown where the sign-in was asked for; required to approve, left out to deny. */
    val number: Int? = null,
)

@Serializable
internal data class PushTokenRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("device_token") val deviceToken: String,
    @SerialName("push_token") val pushToken: String,
)

@Serializable
internal data class ErrorEnvelope(
    val error: ErrorBody? = null,
)

@Serializable
internal data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
    val metadata: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

/** A sign-in request, as `GET /v1/mfa/push/challenges/{id}` returns it. */
@Serializable
data class SignInRequest(
    val id: String,
    @SerialName("user_id") val userId: String,
    val action: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_at") val createdAt: String,
    val context: SignInContext = SignInContext(),
) {
    companion object {
        const val PENDING = "pending"
        const val APPROVED = "approved"
        const val DENIED = "denied"
        const val EXPIRED = "expired"
        const val ACTION_SIGN_IN = "sign_in"
    }
}

/** Where the request came from, to help the person decide. Every field is optional. */
@Serializable
data class SignInContext(
    val ip: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    val location: String? = null,
    val timestamp: String? = null,
)

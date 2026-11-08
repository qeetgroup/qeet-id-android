package com.qeetgroup.qeetid.admin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// What the administration screens read from Qeet ID. Tokens are in [TokenPair] only, which never reaches a screen.

/** A session's token pair, from a passkey sign-in, a renewal or a switch of organisation. */
@Serializable
internal data class TokenPair(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("user_id") val userId: String? = null,
)

/** A WebAuthn ceremony the server began: its id and the request options for the passkey prompt, as JSON. */
@Serializable
internal data class Ceremony(
    @SerialName("session_id") val sessionId: String,
    @SerialName("publicKey") val publicKey: JsonElement,
)

@Serializable
internal data class Me(
    val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
)

/** An organisation the person belongs to, with their roles in it (none: a member only). */
@Serializable
data class Organization(
    val id: String,
    val name: String,
    val roles: List<String> = emptyList(),
)

@Serializable
internal data class OrganizationPage(
    val items: List<Organization> = emptyList(),
)

@Serializable
data class RequestPerson(
    val id: String,
    val email: String,
    val name: String? = null,
)

@Serializable
data class RequestedRole(
    val id: String,
    val name: String,
    val description: String? = null,
)

/** An access request: who asks for which role, why, and for how long (no duration: until it's taken away). */
@Serializable
data class AccessRequest(
    val id: String,
    val requester: RequestPerson,
    val role: RequestedRole,
    val reason: String = "",
    @SerialName("duration_hours") val durationHours: Int? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("access_ends_at") val accessEndsAt: String? = null,
) {
    companion object {
        const val PENDING = "pending"
    }
}

@Serializable
internal data class AccessRequestPage(
    @SerialName("access_requests") val items: List<AccessRequest> = emptyList(),
    val pending: Int = 0,
)

@Serializable
internal data class AccessRequestOne(
    @SerialName("access_request") val item: AccessRequest,
)

/** A critical audit event or anomaly alert, high or medium. */
@Serializable
data class CriticalEvent(
    val id: String,
    val source: String = "audit",
    val level: String,
    val action: String = "",
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("actor_email") val actorEmail: String? = null,
    @SerialName("resource_type") val resourceType: String? = null,
    @SerialName("resource_id") val resourceId: String? = null,
    val ip: String? = null,
    val reasons: List<String> = emptyList(),
    val metadata: Map<String, JsonElement> = emptyMap(),
    @SerialName("occurred_at") val occurredAt: String,
) {
    val high: Boolean get() = level == HIGH

    /** A text value from the event's metadata, if it has one. */
    fun meta(key: String): String? = (metadata[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    /** The person the event is about: the one acted on, else the one who acted. */
    val personId: String? get() = resourceId?.takeIf { resourceType == "user" } ?: actorUserId

    companion object {
        const val HIGH = "high"
        const val ANOMALY = "anomaly"
    }
}

@Serializable
internal data class CriticalEventPage(
    val events: List<CriticalEvent> = emptyList(),
    val high: Int = 0,
)

/** A person in the organisation, as the suspend screens need them. */
@Serializable
data class Member(
    val id: String,
    val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val status: String = "active",
    val metadata: Map<String, JsonElement>? = null,
) {
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: email

    /** Their team, when the directory has one. */
    val team: String?
        get() =
            listOf("department", "team")
                .firstNotNullOfOrNull { key -> (metadata?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content }
                ?.takeIf { it.isNotBlank() }

    val suspended: Boolean get() = status == "suspended"
}

@Serializable
internal data class MemberPage(
    val items: List<Member> = emptyList(),
)

@Serializable
internal data class Agent(
    val id: String,
    val status: String? = null,
    val disabled: Boolean = false,
)

@Serializable
internal data class AgentPage(
    val items: List<Agent> = emptyList(),
)

@Serializable
internal data class AgentsStopped(
    val suspended: Int = 0,
)

/** Why a person was suspended: the reasons Qeet ID records for a suspension. */
enum class SuspendReason(
    val wire: String,
) {
    SuspiciousActivity("suspicious_activity"),
    AccountCompromise("account_compromise"),
    PolicyViolation("policy_violation"),
    Offboarding("offboarding"),
    Other("other"),
}

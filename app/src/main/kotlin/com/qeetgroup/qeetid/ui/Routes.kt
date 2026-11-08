package com.qeetgroup.qeetid.ui

import kotlinx.serialization.Serializable

// The app's screens, as type-safe Navigation Compose routes.

@Serializable
data object Welcome

@Serializable
data object LockSetup

@Serializable
data object NotificationsPrimer

@Serializable
data object Accounts

@Serializable
data object ScanAccount

@Serializable
data object TypeKey

@Serializable
data object LinkPhone

@Serializable
data object ScanPairing

/** Naming and linking the phone; [code] when it was scanned or opened from a link, null to type it. */
@Serializable
data class NamePhone(
    val code: String? = null,
)

@Serializable
data object PhoneLinked

/** A sign-in request; [id] is the server's challenge id. */
@Serializable
data class SignInRequestRoute(
    val id: String,
)

/** More than one sign-in request waiting for this phone. */
@Serializable
data object RequestsWaiting

@Serializable
data object Settings

@Serializable
data object About

@Serializable
data object MoveDevice

/** Qeet ID no longer accepts this version for sign-in approvals. */
@Serializable
data object UpdateRequired

// Administration: an administrator's own session, for access requests, emergencies and critical events.

@Serializable
data object AdminSignIn

@Serializable
data object AdminOrganizations

@Serializable
data object AdminHome

@Serializable
data object AdminRequests

@Serializable
data class AdminRequestRoute(
    val id: String,
)

@Serializable
data object AdminPeople

@Serializable
data class AdminSuspend(
    val userId: String,
)

@Serializable
data object AdminAgents

@Serializable
data object AdminEvents

@Serializable
data class AdminEventRoute(
    val id: String,
)

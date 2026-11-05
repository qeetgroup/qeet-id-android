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

@Serializable
data object Settings

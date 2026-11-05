package com.qeetgroup.qeetid.approvals.push

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/** How this build receives sign-in requests. */
enum class PushAvailability {
    /** Firebase Cloud Messaging is configured: requests arrive as push messages. */
    Firebase,

    /**
     * A debug build without Firebase: the phone links with a stand-in token, and requests reach it only through the
     * debug entry point (docs/firebase.md). Never in a release build.
     */
    DebugStandIn,

    /** A release build without Firebase: it cannot be linked. */
    Unavailable,
}

/** This phone's push token, which the server sends sign-in requests to. */
interface PushTokens {
    val availability: PushAvailability

    /** The current token, or null when it cannot be had (offline, or Google Play services missing). */
    suspend fun token(): String?

    /** Stops receiving: forgets the token, so the server's copy is useless, and stops Firebase making new ones. */
    suspend fun stop()
}

/**
 * Firebase Cloud Messaging. Firebase starts only when the build has a Firebase app config (app/google-services.json);
 * automatic token creation is off in the manifest, so a phone that was never linked never registers with Firebase.
 */
class FirebasePushTokens(
    private val context: Context,
) : PushTokens {
    override val availability: PushAvailability
        get() = if (FirebaseApp.getApps(context).isNotEmpty()) PushAvailability.Firebase else PushAvailability.Unavailable

    override suspend fun token(): String? {
        if (availability != PushAvailability.Firebase) return null
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = true
        return messaging.token.awaitOrNull()?.takeIf { it.isNotBlank() }
    }

    override suspend fun stop() {
        if (availability != PushAvailability.Firebase) return
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = false
        messaging.deleteToken().awaitOrNull()
    }
}

/** A debug build's stand-in token, so a phone can link to a development server without a Firebase project. */
class DebugStandInPushTokens(
    private val prefs: SharedPreferences,
) : PushTokens {
    override val availability = PushAvailability.DebugStandIn

    override suspend fun token(): String =
        prefs.getString(KEY, null) ?: "debug-stand-in-${UUID.randomUUID()}".also { prefs.edit { putString(KEY, it) } }

    override suspend fun stop() = prefs.edit { remove(KEY) }

    private companion object {
        const val KEY = "stand_in_push_token"
    }
}

/** A release build without Firebase. */
object NoPushTokens : PushTokens {
    override val availability = PushAvailability.Unavailable

    override suspend fun token(): String? = null

    override suspend fun stop() = Unit
}

private suspend fun <T> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }

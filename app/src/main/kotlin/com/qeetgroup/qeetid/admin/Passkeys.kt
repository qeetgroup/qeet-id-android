package com.qeetgroup.qeetid.admin

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

/** How the phone's passkey prompt went. */
sealed interface PasskeyResult {
    /** The signed assertion, as WebAuthn JSON, for the server to check. */
    data class Signed(
        val json: String,
    ) : PasskeyResult

    data object Cancelled : PasskeyResult

    /** No passkey for Qeet ID on this phone (or in the password manager it uses). */
    data object NoPasskey : PasskeyResult

    /** The phone couldn't ask (no screen lock, an old Play services, Qeet ID not trusted for the domain). */
    data object Unavailable : PasskeyResult
}

/**
 * The phone's own passkey prompt: the person picks their Qeet ID passkey and confirms with their fingerprint, face or
 * screen lock, as on the computer. Tests use a fake.
 */
fun interface PasskeyPrompt {
    /** Asks for a passkey assertion for [requestJson] (WebAuthn's PublicKeyCredentialRequestOptions, as JSON). */
    suspend fun sign(requestJson: String): PasskeyResult
}

/** A build or test with no passkey prompt. */
object NoPasskeys : PasskeyPrompt {
    override suspend fun sign(requestJson: String): PasskeyResult = PasskeyResult.Unavailable
}

/** Android's Credential Manager, which shows the system passkey sheet over [activity]. */
class CredentialManagerPasskeys(
    private val activity: Activity,
) : PasskeyPrompt {
    override suspend fun sign(requestJson: String): PasskeyResult {
        val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson)))
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is PublicKeyCredential) {
                PasskeyResult.Signed(
                    credential.authenticationResponseJson,
                )
            } else {
                PasskeyResult.Unavailable
            }
        } catch (_: GetCredentialCancellationException) {
            PasskeyResult.Cancelled
        } catch (_: NoCredentialException) {
            PasskeyResult.NoPasskey
        } catch (_: GetCredentialException) {
            PasskeyResult.Unavailable
        }
    }
}

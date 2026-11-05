package com.qeetgroup.qeetid.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What the person is asked to confirm, in Android's own prompt. */
data class VerifyPrompt(
    val title: String,
    val subtitle: String,
)

enum class VerifyResult {
    /** Fingerprint, face or the phone's screen lock matched. */
    Verified,

    /** The person closed the prompt, or the app went to the background. */
    Cancelled,

    /** Too many tries, or the hardware refused. */
    Failed,

    /** The phone has no screen lock, so there is nothing to verify with. */
    Unavailable,
}

/** Asks the person to prove they are the phone's owner: fingerprint, face or the phone's PIN, pattern or password. */
interface UserVerification {
    /** Whether the phone has a screen lock (a PIN, pattern or password; biometrics need one too). */
    fun isAvailable(): Boolean

    suspend fun verify(prompt: VerifyPrompt): VerifyResult
}

/**
 * Android's BiometricPrompt, allowing a strong biometric or the phone's screen lock. Android 10 cannot combine a strong
 * biometric with the screen lock in one prompt, so there it allows any biometric the phone accepts for unlocking, with
 * the screen lock as the fallback.
 */
class BiometricUserVerification(
    private val activity: FragmentActivity,
) : UserVerification {
    private val authenticators =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    override fun isAvailable(): Boolean = isDeviceSecure(activity)

    override suspend fun verify(prompt: VerifyPrompt): VerifyResult {
        if (!isAvailable()) return VerifyResult.Unavailable
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            return VerifyResult.Unavailable
        }
        return suspendCancellableCoroutine { continuation ->
            val biometricPrompt =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (continuation.isActive) continuation.resume(VerifyResult.Verified)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            val outcome =
                                when (errorCode) {
                                    BiometricPrompt.ERROR_USER_CANCELED,
                                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                    BiometricPrompt.ERROR_CANCELED,
                                    -> VerifyResult.Cancelled

                                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                                    -> VerifyResult.Unavailable

                                    else -> VerifyResult.Failed
                                }
                            if (continuation.isActive) continuation.resume(outcome)
                        }
                        // onAuthenticationFailed is one unrecognised finger; the prompt stays up and lets them try again.
                    },
                )
            continuation.invokeOnCancellation { biometricPrompt.cancelAuthentication() }
            biometricPrompt.authenticate(
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(prompt.title)
                    .setSubtitle(prompt.subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .setConfirmationRequired(true)
                    .build(),
            )
        }
    }

    companion object {
        fun isDeviceSecure(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }
}

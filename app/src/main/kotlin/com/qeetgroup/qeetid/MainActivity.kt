package com.qeetgroup.qeetid

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.qeetgroup.qeetid.admin.CredentialManagerPasskeys
import com.qeetgroup.qeetid.approvals.push.SignInNotifications
import com.qeetgroup.qeetid.lock.BiometricUserVerification
import com.qeetgroup.qeetid.ui.QeetIdApp

/**
 * The one activity. A FragmentActivity because BiometricPrompt needs one. It hands the app two kinds of intent: a tap
 * on a sign-in request notification, and the pairing link from the QR code in Qeet ID (`qeetidauth://pair?code=…`).
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Codes and sign-in requests stay out of screenshots, screen recordings and the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // No other app's window may sit over this one (a tapjacking overlay over Approve).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        } else {
            window.decorView.filterTouchesWhenObscured = true
        }
        val container = appContainer
        SignInNotifications.ensureChannel(this)
        if (savedInstanceState == null) handle(intent)
        val verification = BiometricUserVerification(this)
        val passkeys = CredentialManagerPasskeys(this)
        setContent { QeetIdApp(container, verification, passkeys) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val inbox = appContainer.inbox
        when {
            intent.action == SignInNotifications.ACTION_OPEN_REQUEST -> {
                intent.getStringExtra(SignInNotifications.EXTRA_REQUEST_ID)?.let(inbox::openRequest)
            }

            intent.action == Intent.ACTION_VIEW && intent.data?.scheme.equals(PAIRING_SCHEME, ignoreCase = true) -> {
                intent.dataString?.let(inbox::openPairing)
            }
        }
    }

    private companion object {
        const val PAIRING_SCHEME = "qeetidauth"
    }
}

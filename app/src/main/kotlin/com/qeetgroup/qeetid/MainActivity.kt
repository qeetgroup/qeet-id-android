package com.qeetgroup.qeetid

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
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
        val container = appContainer
        SignInNotifications.ensureChannel(this)
        if (savedInstanceState == null) handle(intent)
        val verification = BiometricUserVerification(this)
        setContent { QeetIdApp(container, verification) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val inbox = appContainer.inbox
        when {
            intent.action == SignInNotifications.ACTION_OPEN_REQUEST ->
                intent.getStringExtra(SignInNotifications.EXTRA_REQUEST_ID)?.let(inbox::openRequest)

            intent.action == Intent.ACTION_VIEW && intent.data?.scheme.equals(PAIRING_SCHEME, ignoreCase = true) ->
                intent.dataString?.let(inbox::openPairing)
        }
    }

    private companion object {
        const val PAIRING_SCHEME = "qeetidauth"
    }
}

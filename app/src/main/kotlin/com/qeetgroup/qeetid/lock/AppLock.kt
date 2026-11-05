package com.qeetgroup.qeetid.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is locked. With app lock on, opening the app asks for fingerprint, face or the screen lock, and so
 * does coming back to it after [GRACE_MILLIS] away — long enough to paste a code in another app, short enough that a
 * phone put down and picked up by someone else asks again. A phone with no screen lock has no lock to verify with, so
 * the app opens (as the React Native app did); turning app lock on asks for a screen lock first.
 *
 * Locking runs [onLock], which drops the secrets read for codes.
 */
class AppLock(
    private val enabled: () -> Boolean,
    private val deviceSecure: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val onLock: () -> Unit = {},
) {
    private val _locked = MutableStateFlow(shouldLock())
    private var backgroundAt: Long? = null

    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** The app went to the background. */
    fun onBackground() {
        backgroundAt = now()
    }

    /** The app came back to the foreground. */
    fun onForeground() {
        val away = backgroundAt?.let { now() - it } ?: return
        backgroundAt = null
        if (away >= GRACE_MILLIS && shouldLock()) lock()
    }

    fun lock() {
        onLock()
        _locked.value = shouldLock()
    }

    /** After a successful [UserVerification]. */
    fun unlock() {
        _locked.value = false
    }

    /** App lock was turned off. */
    fun release() {
        _locked.value = false
    }

    private fun shouldLock(): Boolean = enabled() && deviceSecure()

    companion object {
        const val GRACE_MILLIS = 30_000L
    }
}

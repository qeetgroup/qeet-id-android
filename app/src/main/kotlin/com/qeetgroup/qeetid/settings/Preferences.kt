package com.qeetgroup.qeetid.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's choices. None of them is secret — the secrets are sealed files — so they live in plain preferences, which
 * the backup rules keep on the phone all the same.
 */
class Preferences(
    private val prefs: SharedPreferences,
) {
    private val _onboarded = MutableStateFlow(prefs.getBoolean(ONBOARDED, false))
    private val _appLock = MutableStateFlow(prefs.getBoolean(APP_LOCK, false))
    private val _hideCodes = MutableStateFlow(prefs.getBoolean(HIDE_CODES, false))

    /** Whether the welcome pages have been through. */
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    /** Ask for fingerprint, face or screen lock to open the app. */
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()

    /** Show codes as dots until tapped. */
    val hideCodes: StateFlow<Boolean> = _hideCodes.asStateFlow()

    /** Whether the camera has been asked for once (to tell "not asked yet" from "refused for good"). */
    var cameraAsked: Boolean
        get() = prefs.getBoolean(CAMERA_ASKED, false)
        set(value) = prefs.edit { putBoolean(CAMERA_ASKED, value) }

    /** Whether notifications have been asked for once. */
    var notificationsAsked: Boolean
        get() = prefs.getBoolean(NOTIFICATIONS_ASKED, false)
        set(value) = prefs.edit { putBoolean(NOTIFICATIONS_ASKED, value) }

    fun setOnboarded(value: Boolean) = set(ONBOARDED, value, _onboarded)

    fun setAppLock(value: Boolean) = set(APP_LOCK, value, _appLock)

    fun setHideCodes(value: Boolean) = set(HIDE_CODES, value, _hideCodes)

    private fun set(
        key: String,
        value: Boolean,
        flow: MutableStateFlow<Boolean>,
    ) {
        prefs.edit { putBoolean(key, value) }
        flow.value = value
    }

    private companion object {
        const val ONBOARDED = "onboarded"
        const val APP_LOCK = "app_lock"
        const val HIDE_CODES = "hide_codes"
        const val CAMERA_ASKED = "camera_asked"
        const val NOTIFICATIONS_ASKED = "notifications_asked"
    }
}

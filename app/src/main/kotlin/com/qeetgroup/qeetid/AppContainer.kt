package com.qeetgroup.qeetid

import android.content.Context
import com.qeetgroup.qeetid.approvals.PhoneLinkStore
import com.qeetgroup.qeetid.approvals.push.DebugStandInPushTokens
import com.qeetgroup.qeetid.approvals.push.FirebasePushTokens
import com.qeetgroup.qeetid.approvals.push.NoPushTokens
import com.qeetgroup.qeetid.approvals.push.PushAvailability
import com.qeetgroup.qeetid.approvals.push.PushTokenSync
import com.qeetgroup.qeetid.approvals.push.PushTokens
import com.qeetgroup.qeetid.codes.AccountStore
import com.qeetgroup.qeetid.data.api.QeetIdApi
import com.qeetgroup.qeetid.data.secure.KeystoreSecretBox
import com.qeetgroup.qeetid.data.secure.SealedFiles
import com.qeetgroup.qeetid.data.secure.SecretBox
import com.qeetgroup.qeetid.lock.AppLock
import com.qeetgroup.qeetid.lock.BiometricUserVerification
import com.qeetgroup.qeetid.settings.Preferences
import com.qeetgroup.qeetid.system.CodeClipboard
import com.qeetgroup.qeetid.system.Connectivity
import com.qeetgroup.qeetid.system.SystemConnectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

/**
 * The app's objects, made once and handed to the screens (manual dependency injection). Tests build one with fakes
 * through the primary constructor.
 */
class AppContainer(
    val context: Context,
    val preferences: Preferences,
    val accounts: AccountStore,
    val links: PhoneLinkStore,
    val api: QeetIdApi,
    val pushTokens: PushTokens,
    val connectivity: Connectivity,
    val scope: CoroutineScope,
    val deviceSecure: () -> Boolean,
    val now: () -> Long = System::currentTimeMillis,
) {
    val clipboard = CodeClipboard(context, scope)

    val lock =
        AppLock(
            enabled = { preferences.appLock.value },
            deviceSecure = deviceSecure,
            now = now,
            onLock = accounts::forgetSecrets,
        )

    val pushSync = PushTokenSync(links, api, pushTokens, scope)

    companion object {
        fun create(context: Context): AppContainer {
            val app = context.applicationContext
            val box: SecretBox = KeystoreSecretBox()
            val files = SealedFiles(File(app.noBackupFilesDir, "vault"), box)
            val prefs = app.getSharedPreferences("qeetid", Context.MODE_PRIVATE)
            val firebase = FirebasePushTokens(app)
            val pushTokens =
                when {
                    firebase.availability == PushAvailability.Firebase -> firebase
                    BuildConfig.DEBUG -> DebugStandInPushTokens(prefs)
                    else -> NoPushTokens
                }
            return AppContainer(
                context = app,
                preferences = Preferences(prefs),
                accounts = AccountStore(files),
                links = PhoneLinkStore(files),
                api =
                    QeetIdApi(
                        baseUrl = apiBaseUrl(BuildConfig.API_BASE_URL, BuildConfig.DEBUG),
                        userAgent = "QeetID-Android/${BuildConfig.VERSION_NAME}",
                    ),
                pushTokens = pushTokens,
                connectivity = SystemConnectivity(app),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                deviceSecure = { BiometricUserVerification.isDeviceSecure(app) },
            )
        }

        /** The API's address. A release build accepts only HTTPS; a debug build may use HTTP for a LAN dev server. */
        fun apiBaseUrl(
            url: String,
            debug: Boolean,
        ): HttpUrl {
            val parsed = url.toHttpUrl()
            require(debug || parsed.isHttps) { "A release build talks to Qeet ID over HTTPS only" }
            return parsed
        }
    }
}

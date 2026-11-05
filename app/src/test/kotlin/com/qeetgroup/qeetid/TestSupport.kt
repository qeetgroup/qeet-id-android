package com.qeetgroup.qeetid

import android.content.Context
import com.qeetgroup.qeetid.approvals.PhoneLinkStore
import com.qeetgroup.qeetid.approvals.push.PushAvailability
import com.qeetgroup.qeetid.approvals.push.PushTokens
import com.qeetgroup.qeetid.codes.AccountStore
import com.qeetgroup.qeetid.data.api.QeetIdApi
import com.qeetgroup.qeetid.data.secure.SealedFiles
import com.qeetgroup.qeetid.data.secure.SecretBox
import com.qeetgroup.qeetid.lock.UserVerification
import com.qeetgroup.qeetid.lock.VerifyPrompt
import com.qeetgroup.qeetid.lock.VerifyResult
import com.qeetgroup.qeetid.settings.Preferences
import com.qeetgroup.qeetid.system.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * [com.qeetgroup.qeetid.data.secure.KeystoreSecretBox]'s format and AES-256-GCM with a software key: the JVM has no
 * Android Keystore. The binding to the file name (the GCM associated data) is real.
 */
class SoftwareSecretBox : SecretBox {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val random = SecureRandom()

    override fun seal(
        plain: ByteArray,
        context: String,
    ): ByteArray {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(context.toByteArray())
        return byteArrayOf(1) + nonce + cipher.doFinal(plain)
    }

    override fun open(
        sealed: ByteArray,
        context: String,
    ): ByteArray {
        if (sealed.size < 13 || sealed[0] != 1.toByte()) throw GeneralSecurityException("Not a sealed value")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 1, 12))
        cipher.updateAAD(context.toByteArray())
        return cipher.doFinal(sealed, 13, sealed.size - 13)
    }
}

class FakePushTokens(
    override val availability: PushAvailability = PushAvailability.Firebase,
    var token: String? = "fcm-token-1",
) : PushTokens {
    var stopped = 0

    override suspend fun token(): String? = token

    override suspend fun stop() {
        stopped++
    }
}

class FakeConnectivity(
    online: Boolean = true,
) : Connectivity {
    override val online = MutableStateFlow(online)
}

class FakeVerification(
    var result: VerifyResult = VerifyResult.Verified,
    private val available: Boolean = true,
) : UserVerification {
    val prompts = mutableListOf<VerifyPrompt>()

    override fun isAvailable(): Boolean = available

    override suspend fun verify(prompt: VerifyPrompt): VerifyResult {
        prompts += prompt
        return result
    }
}

object TestContainers {
    /** An [AppContainer] with sealed files in a fresh directory, a software box and fakes; [api] is the server to call. */
    fun create(
        context: Context,
        api: HttpUrl = "http://127.0.0.1:9/".toHttpUrl(),
        pushTokens: PushTokens = FakePushTokens(),
        connectivity: Connectivity = FakeConnectivity(),
        deviceSecure: Boolean = true,
        now: () -> Long = System::currentTimeMillis,
    ): AppContainer {
        val id = UUID.randomUUID().toString()
        val files = SealedFiles(File(context.cacheDir, "vault-$id"), SoftwareSecretBox())
        return AppContainer(
            context = context,
            preferences = Preferences(context.getSharedPreferences("test-$id", Context.MODE_PRIVATE)),
            accounts = AccountStore(files),
            links = PhoneLinkStore(files),
            api = QeetIdApi(api, now = now),
            pushTokens = pushTokens,
            connectivity = connectivity,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            deviceSecure = { deviceSecure },
            now = now,
        )
    }
}

/**
 * The flow's first value, collected off the main thread while the main looper keeps running: view models work on
 * Robolectric's main thread, so blocking it (runBlocking there) would never let them send.
 */
fun <T> Flow<T>.awaitFirst(timeoutMillis: Long = 5_000): T {
    val result = AtomicReference<Any?>(NONE)
    val job = CoroutineScope(Dispatchers.Default).launch { result.set(first()) }
    try {
        awaitUntil(timeoutMillis) { result.get() !== NONE }
    } finally {
        job.cancel()
    }
    @Suppress("UNCHECKED_CAST")
    return result.get() as T
}

private val NONE = Any()

/** Waits (in real time) until [condition] holds: work runs on OkHttp's and the IO threads. */
fun awaitUntil(
    timeoutMillis: Long = 5_000,
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "Timed out waiting" }
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
        Thread.sleep(10)
    }
}

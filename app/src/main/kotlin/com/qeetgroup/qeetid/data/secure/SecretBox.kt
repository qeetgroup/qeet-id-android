package com.qeetgroup.qeetid.data.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals and opens the app's secrets: the sign-in code secrets, the list of accounts and this phone's device
 * credential. [context] binds a sealed value to where it is kept (its file name), so one sealed file cannot be swapped
 * in for another.
 */
interface SecretBox {
    fun seal(
        plain: ByteArray,
        context: String,
    ): ByteArray

    /** @throws GeneralSecurityException when the value was not sealed by this box for [context], or was changed. */
    fun open(
        sealed: ByteArray,
        context: String,
    ): ByteArray
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore (in the phone's secure hardware where it has one) and never
 * leaves it: the app holds only ciphertext. Not the deprecated security-crypto library.
 *
 * A sealed value is `version (1) | nonce (12) | ciphertext and tag`; the nonce is the Keystore's own random one.
 */
class KeystoreSecretBox(
    private val alias: String = KEY_ALIAS,
) : SecretBox {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun seal(
        plain: ByteArray,
        context: String,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(context.toByteArray())
        val sealed = cipher.doFinal(plain)
        val nonce = cipher.iv
        check(nonce.size == NONCE_BYTES) { "Unexpected GCM nonce length" }
        return byteArrayOf(VERSION) + nonce + sealed
    }

    override fun open(
        sealed: ByteArray,
        context: String,
    ): ByteArray {
        if (sealed.size < 1 + NONCE_BYTES || sealed[0] != VERSION) throw GeneralSecurityException("Not a sealed value")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 1, NONCE_BYTES))
        cipher.updateAAD(context.toByteArray())
        return cipher.doFinal(sealed, 1 + NONCE_BYTES, sealed.size - 1 - NONCE_BYTES)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "qeetid.vault.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val VERSION: Byte = 1
    }
}

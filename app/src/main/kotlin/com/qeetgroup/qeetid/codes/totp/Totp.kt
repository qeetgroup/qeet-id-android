package com.qeetgroup.qeetid.codes.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The HMAC a code is computed with (RFC 6238 §1.2). */
enum class TotpAlgorithm(
    val uriName: String,
    internal val macName: String,
) {
    SHA1("SHA1", "HmacSHA1"),
    SHA256("SHA256", "HmacSHA256"),
    SHA512("SHA512", "HmacSHA512"),
    ;

    companion object {
        fun fromUriName(name: String): TotpAlgorithm? = entries.firstOrNull { it.uriName.equals(name.replace("-", ""), ignoreCase = true) }
    }
}

/** A code and the moment it stops being valid. */
data class TotpCode(
    val code: String,
    val validFromMillis: Long,
    val expiresAtMillis: Long,
)

/**
 * Time-based one-time passwords (RFC 6238, over RFC 4226's HOTP), computed with the platform's `javax.crypto`.
 * Nothing here keeps the key: callers pass it for each code.
 */
object Totp {
    const val MIN_DIGITS = 6
    const val MAX_DIGITS = 8
    const val DEFAULT_PERIOD_SECONDS = 30

    /** The code for the time step that contains [timeMillis]. */
    fun code(
        key: ByteArray,
        timeMillis: Long,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        digits: Int = MIN_DIGITS,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    ): TotpCode {
        require(periodSeconds > 0) { "The period is a positive number of seconds" }
        require(digits in MIN_DIGITS..MAX_DIGITS) { "A code has 6 to 8 digits" }
        val periodMillis = periodSeconds * 1000L
        val step = Math.floorDiv(timeMillis, periodMillis)
        val start = step * periodMillis
        return TotpCode(hotp(key, step, digits, algorithm), start, start + periodMillis)
    }

    /** RFC 4226 §5.3: HMAC over the 8-byte counter, dynamic truncation, the low [digits] decimal digits. */
    fun hotp(
        key: ByteArray,
        counter: Long,
        digits: Int,
        algorithm: TotpAlgorithm,
    ): String {
        val mac = Mac.getInstance(algorithm.macName)
        mac.init(SecretKeySpec(key, algorithm.macName))
        val message = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            message[i] = (value and 0xFF).toByte()
            value = value shr 8
        }
        val hash = mac.doFinal(message)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % POWERS[digits]
        return otp.toString().padStart(digits, '0')
    }

    private val POWERS = IntArray(9).also { it[0] = 1 }.also { for (i in 1..8) it[i] = it[i - 1] * 10 }
}

package com.qeetgroup.qeetid.codes.totp

/** An account read from a setup QR code or typed in: everything needed to compute its codes. */
class NewAccount(
    val issuer: String,
    val label: String,
    val secret: ByteArray,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = Totp.MIN_DIGITS,
    val periodSeconds: Int = Totp.DEFAULT_PERIOD_SECONDS,
) {
    // The secret stays out of logs and crash reports.
    override fun toString(): String = "NewAccount(issuer=$issuer, label=$label, ${algorithm.uriName}, $digits digits, ${periodSeconds}s)"
}

/** What a scanned `otpauth://` link turned out to be. */
sealed interface OtpAuthResult {
    data class Valid(
        val account: NewAccount,
    ) : OtpAuthResult

    /** A counter-based (HOTP) account: valid, but this app shows time-based codes only. */
    data object CounterBased : OtpAuthResult

    /** Not an `otpauth://` link, or one without a usable secret or settings. */
    data object Invalid : OtpAuthResult
}

/**
 * Parses the Key URI Format that 2-step setup pages put in their QR codes:
 * `otpauth://totp/Issuer:account?secret=BASE32&issuer=Issuer&algorithm=SHA1&digits=6&period=30`.
 *
 * Percent-escapes are decoded and `+` is kept as it is (it is common in email addresses). The `issuer` parameter wins
 * over the label's prefix. Codes have 6 to 8 digits and a period of 1 to 300 seconds; anything else is refused.
 */
object OtpAuthUri {
    private const val SCHEME = "otpauth://"
    private const val MAX_PERIOD_SECONDS = 300

    fun parse(text: String): OtpAuthResult {
        val uri = text.trim()
        if (!uri.startsWith(SCHEME, ignoreCase = true)) return OtpAuthResult.Invalid
        val rest = uri.substring(SCHEME.length)
        val slash = rest.indexOf('/')
        if (slash < 0) return OtpAuthResult.Invalid
        when (rest.substring(0, slash).lowercase()) {
            "totp" -> Unit
            "hotp" -> return OtpAuthResult.CounterBased
            else -> return OtpAuthResult.Invalid
        }
        val afterType = rest.substring(slash + 1).substringBefore('#')
        val path = decode(afterType.substringBefore('?')) ?: return OtpAuthResult.Invalid
        val query = afterType.substringAfter('?', "")
        val params = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = decode(pair.substringBefore('='))?.lowercase() ?: return OtpAuthResult.Invalid
            val value = decode(pair.substringAfter('=', "")) ?: return OtpAuthResult.Invalid
            params.putIfAbsent(key, value)
        }

        val secret = params["secret"]?.let(Base32::decode)?.takeIf { it.isNotEmpty() } ?: return OtpAuthResult.Invalid
        val algorithm = params["algorithm"]?.let { TotpAlgorithm.fromUriName(it) ?: return OtpAuthResult.Invalid } ?: TotpAlgorithm.SHA1
        val digits = params["digits"]?.let { it.toIntOrNull() ?: return OtpAuthResult.Invalid } ?: Totp.MIN_DIGITS
        if (digits !in Totp.MIN_DIGITS..Totp.MAX_DIGITS) return OtpAuthResult.Invalid
        val period = params["period"]?.let { it.toIntOrNull() ?: return OtpAuthResult.Invalid } ?: Totp.DEFAULT_PERIOD_SECONDS
        if (period !in 1..MAX_PERIOD_SECONDS) return OtpAuthResult.Invalid

        val prefix = path.substringBefore(':', "").trim()
        val account = (if (path.contains(':')) path.substringAfter(':') else path).trim()
        var issuer = params["issuer"]?.trim().orEmpty().ifEmpty { prefix }
        var label = account
        if (issuer.isEmpty()) {
            issuer = label
            label = ""
        }
        return OtpAuthResult.Valid(NewAccount(issuer, label, secret, algorithm, digits, period))
    }

    /** Percent-decodes [text] as UTF-8, leaving `+` alone; null when an escape is malformed. */
    private fun decode(text: String): String? {
        if ('%' !in text) return text
        val bytes = java.io.ByteArrayOutputStream(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%') {
                if (i + 2 >= text.length) return null
                val hex = text.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                bytes.write(hex)
                i += 3
            } else {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}

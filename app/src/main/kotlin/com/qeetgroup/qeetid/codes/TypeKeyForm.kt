package com.qeetgroup.qeetid.codes

import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.codes.totp.Base32
import com.qeetgroup.qeetid.codes.totp.NewAccount
import com.qeetgroup.qeetid.codes.totp.TotpAlgorithm

/** Checks a setup key typed by hand (Screen 8). Errors are string resources that say what to do. */
object TypeKeyForm {
    /** 80 bits: the shortest secret sites hand out in practice (RFC 4226 asks for 128 or more). */
    const val MIN_KEY_BYTES = 10

    sealed interface Result {
        data class Valid(
            val account: NewAccount,
        ) : Result

        data class Invalid(
            val serviceError: Int?,
            val keyError: Int?,
        ) : Result
    }

    fun validate(
        service: String,
        account: String,
        key: String,
        digits: Int,
        periodSeconds: Int,
        algorithm: TotpAlgorithm,
    ): Result {
        val issuer = service.trim()
        val serviceError = if (issuer.isEmpty()) R.string.type_key_service_required else null
        val compact = key.filterNot { it.isWhitespace() || it == '-' }.trimEnd('=')
        val secret = Base32.decode(compact)
        val keyError =
            when {
                compact.isEmpty() -> R.string.type_key_key_required
                secret == null -> R.string.type_key_key_invalid
                secret.size < MIN_KEY_BYTES -> R.string.type_key_key_short
                else -> null
            }
        if (serviceError != null || keyError != null || secret == null) return Result.Invalid(serviceError, keyError)
        return Result.Valid(NewAccount(issuer, account.trim(), secret, algorithm, digits, periodSeconds))
    }
}

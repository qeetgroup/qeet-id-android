package com.qeetgroup.qeetid.codes.totp

/**
 * RFC 4648 Base32, the encoding of setup keys. Decoding is forgiving the way people type keys: any case, spaces and
 * hyphens between groups, and the `=` padding optional.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** The key's bytes, or null when [text] holds a character that is not Base32 or a length no key can have. */
    fun decode(text: String): ByteArray? {
        val clean =
            buildString(text.length) {
                for (c in text) {
                    if (c == ' ' || c == '-' || c == '\t' || c == '\n') continue
                    append(c.uppercaseChar())
                }
            }.trimEnd('=')
        if (clean.isEmpty()) return null
        // 5 bits per character: a length of 1, 3 or 6 (mod 8) leaves bits that cannot end a byte.
        if (clean.length % 8 in setOf(1, 3, 6)) return null
        val out = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[index++] = (buffer shr bits and 0xFF).toByte()
            }
        }
        return out
    }

    /** [bytes] in Base32, without padding. */
    fun encode(bytes: ByteArray): String =
        buildString((bytes.size * 8 + 4) / 5) {
            var buffer = 0
            var bits = 0
            for (b in bytes) {
                buffer = (buffer shl 8) or (b.toInt() and 0xFF)
                bits += 8
                while (bits >= 5) {
                    bits -= 5
                    append(ALPHABET[buffer shr bits and 0x1F])
                }
            }
            if (bits > 0) append(ALPHABET[buffer shl (5 - bits) and 0x1F])
        }
}

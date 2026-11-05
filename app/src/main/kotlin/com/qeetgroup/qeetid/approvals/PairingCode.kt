package com.qeetgroup.qeetid.approvals

/**
 * Reads a pairing code from what was scanned or typed: the link in the QR code Qeet ID shows under Link a phone
 * (`qeetidauth://pair?code=ABCDE-FGHJK`, which also opens this app from the phone's camera), or the code itself, with
 * or without its hyphen. The server reads codes loosely (case, spaces, O for 0, I or L for 1), so this checks only the
 * shape: ten letters or digits, shown as two groups of five.
 */
object PairingCode {
    private val LINK = Regex("^qeetidauth://pair/?\\?(?:[^#]*&)?code=([^&#]+)", RegexOption.IGNORE_CASE)
    private val SHAPE = Regex("^[0-9A-Z]{10}$")
    private const val LENGTH = 10

    /** The code as `ABCDE-FGHJK`, or null when [text] is neither a pairing link nor a code. */
    fun from(text: String): String? {
        val trimmed = text.trim()
        val link = LINK.find(trimmed)
        val raw =
            if (link != null) {
                try {
                    java.net.URLDecoder.decode(link.groupValues[1], Charsets.UTF_8.name())
                } catch (_: IllegalArgumentException) {
                    return null
                }
            } else {
                trimmed
            }
        val compact = raw.filterNot { it == '-' || it.isWhitespace() }.uppercase()
        if (!SHAPE.matches(compact)) return null
        return compact.substring(0, LENGTH / 2) + "-" + compact.substring(LENGTH / 2)
    }

    /** Whether [text] is the pairing link (scanned where a setup QR code was expected). */
    fun isPairingLink(text: String): Boolean = text.trim().startsWith("qeetidauth://pair", ignoreCase = true)
}

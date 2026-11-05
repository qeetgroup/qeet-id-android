package com.qeetgroup.qeetid.approvals

/**
 * A short name for the browser a sign-in came from, from its user agent: "Chrome on macOS". Only the common browsers
 * and systems are named; anything else is shown as the server sent it, shortened.
 */
object BrowserName {
    private const val MAX_RAW = 60

    data class Parts(
        val browser: String?,
        val system: String?,
    )

    fun parts(userAgent: String): Parts {
        val ua = userAgent
        val browser =
            when {
                "Edg/" in ua || "EdgA/" in ua || "EdgiOS/" in ua -> "Edge"
                "OPR/" in ua || "Opera" in ua -> "Opera"
                "SamsungBrowser/" in ua -> "Samsung Internet"
                "Firefox/" in ua || "FxiOS/" in ua -> "Firefox"
                "Chrome/" in ua || "CriOS/" in ua -> "Chrome"
                "Safari/" in ua && "Version/" in ua -> "Safari"
                else -> null
            }
        val system =
            when {
                "Windows NT" in ua -> "Windows"
                "iPhone" in ua -> "iOS"
                "iPad" in ua -> "iPadOS"
                "CrOS" in ua -> "ChromeOS"
                "Android" in ua -> "Android"
                "Mac OS X" in ua || "Macintosh" in ua -> "macOS"
                "Linux" in ua -> "Linux"
                else -> null
            }
        return Parts(browser, system)
    }

    /** "Chrome on macOS" (with [on] the locale's "%1$s on %2$s"), or the user agent shortened when it is not known. */
    fun describe(
        userAgent: String?,
        on: (String, String) -> String,
    ): String? {
        val ua = userAgent?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val (browser, system) = parts(ua)
        return when {
            browser != null && system != null -> on(browser, system)
            browser != null -> browser
            else -> if (ua.length > MAX_RAW) ua.take(MAX_RAW).trimEnd() + "…" else ua
        }
    }
}

package com.qeetgroup.qeetid.approvals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserNameTest {
    private fun describe(ua: String?) = BrowserName.describe(ua) { browser, system -> "$browser on $system" }

    @Test
    fun commonBrowsersAreNamed() {
        assertEquals(
            "Chrome on macOS",
            describe(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            ),
        )
        assertEquals(
            "Edge on Windows",
            describe(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36 Edg/131.0",
            ),
        )
        assertEquals("Firefox on Linux", describe("Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0"))
        assertEquals(
            "Safari on iOS",
            describe(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1",
            ),
        )
    }

    @Test
    fun unknownAgentsAreShownShortened() {
        assertEquals("curl/8.7.1", describe("curl/8.7.1"))
        assertEquals(61, describe("x".repeat(200))!!.length)
        assertNull(describe(null))
        assertNull(describe("  "))
    }
}

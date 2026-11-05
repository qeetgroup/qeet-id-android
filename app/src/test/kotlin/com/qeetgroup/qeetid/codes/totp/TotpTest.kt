package com.qeetgroup.qeetid.codes.totp

import org.junit.Assert.assertEquals
import org.junit.Test

class TotpTest {
    private val sha1Key = "12345678901234567890".toByteArray()
    private val sha256Key = "12345678901234567890123456789012".toByteArray()
    private val sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    /** RFC 6238, Appendix B: time (s), SHA-1, SHA-256, SHA-512, 8 digits, 30 s steps. */
    private val vectors =
        listOf(
            Triple(59L, "94287082", Pair("46119246", "90693936")),
            Triple(1111111109L, "07081804", Pair("68084774", "25091201")),
            Triple(1111111111L, "14050471", Pair("67062674", "99943326")),
            Triple(1234567890L, "89005924", Pair("91819424", "93441116")),
            Triple(2000000000L, "69279037", Pair("90698825", "38618901")),
            Triple(20000000000L, "65353130", Pair("77737706", "47863826")),
        )

    @Test
    fun rfc6238VectorsForEveryAlgorithm() {
        for ((seconds, sha1, others) in vectors) {
            val millis = seconds * 1000
            assertEquals("SHA1 at $seconds", sha1, Totp.code(sha1Key, millis, 30, 8, TotpAlgorithm.SHA1).code)
            assertEquals("SHA256 at $seconds", others.first, Totp.code(sha256Key, millis, 30, 8, TotpAlgorithm.SHA256).code)
            assertEquals("SHA512 at $seconds", others.second, Totp.code(sha512Key, millis, 30, 8, TotpAlgorithm.SHA512).code)
        }
    }

    @Test
    fun rfc4226HotpVectors() {
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { counter, code ->
            assertEquals("counter $counter", code, Totp.hotp(sha1Key, counter.toLong(), 6, TotpAlgorithm.SHA1))
        }
    }

    @Test
    fun sixDigitCodesAreTheLowSixOfTheEightDigitOnes() {
        assertEquals("287082", Totp.code(sha1Key, 59_000, 30, 6).code)
        assertEquals("4287082", Totp.code(sha1Key, 59_000, 30, 7).code)
    }

    @Test
    fun theStepGivesTheCodesWindow() {
        val code = Totp.code(sha1Key, 59_000, 30, 6)
        assertEquals(30_000, code.validFromMillis)
        assertEquals(60_000, code.expiresAtMillis)
        val sixty = Totp.code(sha1Key, 59_000, 60, 6)
        assertEquals(0, sixty.validFromMillis)
        assertEquals(60_000, sixty.expiresAtMillis)
    }

    @Test
    fun leadingZerosAreKept() {
        assertEquals("07081804", Totp.code(sha1Key, 1_111_111_109_000, 30, 8).code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fiveDigitsAreRefused() {
        Totp.code(sha1Key, 0, 30, 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aZeroPeriodIsRefused() {
        Totp.code(sha1Key, 0, 0, 6)
    }

    @Test
    fun algorithmNamesAreReadLoosely() {
        assertEquals(TotpAlgorithm.SHA256, TotpAlgorithm.fromUriName("sha256"))
        assertEquals(TotpAlgorithm.SHA512, TotpAlgorithm.fromUriName("SHA-512"))
        assertEquals(null, TotpAlgorithm.fromUriName("MD5"))
    }
}

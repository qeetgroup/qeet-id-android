package com.qeetgroup.qeetid.codes.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Base32Test {
    /** RFC 4648 §10. */
    private val vectors =
        listOf(
            "f" to "MY======",
            "fo" to "MZXQ====",
            "foo" to "MZXW6===",
            "foob" to "MZXW6YQ=",
            "fooba" to "MZXW6YTB",
            "foobar" to "MZXW6YTBOI======",
        )

    @Test
    fun rfc4648Vectors() {
        for ((plain, encoded) in vectors) {
            assertArrayEquals(plain, plain.toByteArray(), Base32.decode(encoded))
            assertEquals(plain, encoded.trimEnd('='), Base32.encode(plain.toByteArray()))
        }
    }

    @Test
    fun keysAreReadAsPeopleTypeThem() {
        val expected = Base32.decode("JBSWY3DPEHPK3PXP")
        assertArrayEquals(expected, Base32.decode("jbsw y3dp ehpk 3pxp"))
        assertArrayEquals(expected, Base32.decode("JBSW-Y3DP-EHPK-3PXP"))
        assertArrayEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1), expected)
    }

    @Test
    fun nonBase32IsRefused() {
        assertNull(Base32.decode("JBSWY3DP1"))
        assertNull(Base32.decode("JBSWY3D0"))
        assertNull(Base32.decode(""))
        assertNull(Base32.decode("   "))
    }

    @Test
    fun lengthsNoKeyCanHaveAreRefused() {
        assertNull(Base32.decode("A"))
        assertNull(Base32.decode("ABC"))
        assertNull(Base32.decode("ABCDEF"))
    }

    @Test
    fun roundTrip() {
        val bytes = ByteArray(20) { (it * 37).toByte() }
        assertArrayEquals(bytes, Base32.decode(Base32.encode(bytes)))
    }
}

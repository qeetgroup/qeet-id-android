package com.qeetgroup.qeetid.codes.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpAuthUriTest {
    private fun valid(uri: String): NewAccount {
        val result = OtpAuthUri.parse(uri)
        assertTrue("$uri should be valid, was $result", result is OtpAuthResult.Valid)
        return (result as OtpAuthResult.Valid).account
    }

    @Test
    fun theKeyUriFormatExample() {
        val account = valid("otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example")
        assertEquals("Example", account.issuer)
        assertEquals("alice@google.com", account.label)
        assertArrayEquals(Base32.decode("JBSWY3DPEHPK3PXP"), account.secret)
        assertEquals(TotpAlgorithm.SHA1, account.algorithm)
        assertEquals(6, account.digits)
        assertEquals(30, account.periodSeconds)
    }

    @Test
    fun theIssuerParameterWinsOverTheLabelPrefix() {
        val account = valid("otpauth://totp/Old%20Name:priya@acme.in?secret=JBSWY3DPEHPK3PXP&issuer=Acme%20Corp")
        assertEquals("Acme Corp", account.issuer)
        assertEquals("priya@acme.in", account.label)
    }

    @Test
    fun percentEscapesAreDecodedAndPlusIsKept() {
        val account = valid("otpauth://totp/GitHub:brightleaf%2Bops%40example.com?secret=JBSWY3DPEHPK3PXP")
        assertEquals("GitHub", account.issuer)
        assertEquals("brightleaf+ops@example.com", account.label)
    }

    @Test
    fun aLabelWithoutIssuerBecomesTheTitle() {
        val account = valid("otpauth://totp/brightleaf-foods?secret=JBSWY3DPEHPK3PXP")
        assertEquals("brightleaf-foods", account.issuer)
        assertEquals("", account.label)
    }

    @Test
    fun settingsAreRead() {
        val account = valid("OTPAUTH://TOTP/AWS:billing?secret=jbswy3dpehpk3pxp&algorithm=SHA256&digits=8&period=60&issuer=AWS")
        assertEquals(TotpAlgorithm.SHA256, account.algorithm)
        assertEquals(8, account.digits)
        assertEquals(60, account.periodSeconds)
    }

    @Test
    fun counterBasedCodesAreRecognised() {
        assertEquals(OtpAuthResult.CounterBased, OtpAuthUri.parse("otpauth://hotp/Acme:priya?secret=JBSWY3DPEHPK3PXP&counter=1"))
    }

    @Test
    fun invalidLinksAreRefused() {
        val invalid =
            listOf(
                "https://example.com",
                "qeetidauth://pair?code=KM7QX-4TRWP",
                "otpauth://totp/Acme:priya",
                "otpauth://totp/Acme:priya?secret=NOT*BASE32",
                "otpauth://totp/Acme:priya?secret=JBSWY3DPEHPK3PXP&digits=5",
                "otpauth://totp/Acme:priya?secret=JBSWY3DPEHPK3PXP&digits=10",
                "otpauth://totp/Acme:priya?secret=JBSWY3DPEHPK3PXP&period=0",
                "otpauth://totp/Acme:priya?secret=JBSWY3DPEHPK3PXP&period=abc",
                "otpauth://totp/Acme:priya?secret=JBSWY3DPEHPK3PXP&algorithm=MD5",
                "otpauth://totp/Acme%ZZ:priya?secret=JBSWY3DPEHPK3PXP",
                "otpauth://steam/Acme?secret=JBSWY3DPEHPK3PXP",
            )
        for (uri in invalid) assertEquals(uri, OtpAuthResult.Invalid, OtpAuthUri.parse(uri))
    }

    @Test
    fun theSecretNeverAppearsInToString() {
        val account = valid("otpauth://totp/Acme:priya?secret=JBSWY3DPEHPK3PXP")
        assertTrue(!account.toString().contains("JBSW"))
        assertTrue(!account.toString().contains(account.secret.contentToString()))
    }
}

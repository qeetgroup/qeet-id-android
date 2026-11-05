package com.qeetgroup.qeetid.approvals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {
    @Test
    fun theConsolesLinkAndTypedCodesAreRead() {
        assertEquals("KM7QX-4TRWP", PairingCode.from("qeetidauth://pair?code=KM7QX-4TRWP"))
        assertEquals("KM7QX-4TRWP", PairingCode.from("QEETIDAUTH://pair/?v=1&code=km7qx4trwp"))
        assertEquals("KM7QX-4TRWP", PairingCode.from("qeetidauth://pair?code=KM7QX%2D4TRWP#x"))
        assertEquals("KM7QX-4TRWP", PairingCode.from("km7qx-4trwp"))
        assertEquals("KM7QX-4TRWP", PairingCode.from("  KM7QX 4TRWP "))
        assertEquals("KM7QX-4TRWP", PairingCode.from("KM7QX4TRWP"))
    }

    @Test
    fun otherTextIsRefused() {
        assertNull(PairingCode.from("KM7QX-4TRW"))
        assertNull(PairingCode.from("KM7QX-4TRWPX"))
        assertNull(PairingCode.from("KM7QX_4TRWP"))
        assertNull(PairingCode.from("otpauth://totp/Acme?secret=JBSWY3DPEHPK3PXP"))
        assertNull(PairingCode.from("https://example.com/pair?code=KM7QX-4TRWP"))
        assertNull(PairingCode.from(""))
    }

    @Test
    fun thePairingLinkIsRecognised() {
        assertTrue(PairingCode.isPairingLink("qeetidauth://pair?code=x"))
        assertFalse(PairingCode.isPairingLink("otpauth://totp/x"))
    }
}

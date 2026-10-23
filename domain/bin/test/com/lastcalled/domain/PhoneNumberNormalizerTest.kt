package com.lastcalled.domain

import com.lastcalled.domain.logic.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberNormalizerTest {
    private fun n(raw: String?, e164: String? = null) = PhoneNumberNormalizer.normalize(raw, e164)

    @Test fun `international, trunk-prefixed and bare national forms all match`() {
        val forms = listOf("+91 9876543210", "09876543210", "9876543210", "+91 (987) 654-3210", "0091 98765 43210")
        for (a in forms) for (b in forms) {
            assertTrue("$a vs $b", PhoneNumberNormalizer.matches(n(a)!!, n(b)!!))
        }
    }

    @Test fun `00 prefix is converted to plus`() {
        assertEquals("+919876543210", n("0091 98765 43210")!!.e164)
    }

    @Test fun `separators are ignored`() {
        val x = n("+1 (415) 555-0123")!!
        assertEquals("+14155550123", x.e164)
        assertEquals("14155550123", x.significantDigits)
    }

    @Test fun `national number has no e164 unless the platform provides one`() {
        assertNull(n("09876543210")!!.e164)
        assertEquals("+919876543210", n("09876543210", "+919876543210")!!.e164)
        assertEquals("9876543210", n("09876543210")!!.significantDigits)
    }

    @Test fun `malformed platform e164 is ignored`() {
        assertNull(n("12345", "not-a-number")!!.e164)
    }

    @Test fun `different countries with the same national digits do not match`() {
        assertFalse(PhoneNumberNormalizer.matches(n("+91 9876543210")!!, n("+1 9876543210")!!))
    }

    @Test fun `short service numbers match only exactly`() {
        assertTrue(PhoneNumberNormalizer.matches(n("121")!!, n("121")!!))
        assertFalse(PhoneNumberNormalizer.matches(n("121")!!, n("+91 9876543121")!!))
    }

    @Test fun `numbers sharing only a short suffix do not match`() {
        assertFalse(PhoneNumberNormalizer.matches(n("+919876543210")!!, n("+919999943210")!!))
    }

    @Test fun `exact international match is stronger than suffix match`() {
        val intl = n("+919876543210")!!
        assertEquals(3, PhoneNumberNormalizer.matchStrength(intl, n("+919876543210")!!))
        assertEquals(1, PhoneNumberNormalizer.matchStrength(intl, n("09876543210")!!))
    }

    @Test fun `blank and null produce null`() {
        assertNull(n(null))
        assertNull(n("   "))
    }

    @Test fun `non numeric address keeps raw value as canonical`() {
        val x = n("anonymous")!!
        assertEquals("", x.significantDigits)
        assertEquals("anonymous", x.canonical)
    }

    @Test fun `looksLikePhoneNumber distinguishes names from numbers`() {
        assertTrue(PhoneNumberNormalizer.looksLikePhoneNumber("+91 98765 43210"))
        assertFalse(PhoneNumberNormalizer.looksLikePhoneNumber("Rahul Sharma"))
        assertFalse(PhoneNumberNormalizer.looksLikePhoneNumber("12"))
    }
}

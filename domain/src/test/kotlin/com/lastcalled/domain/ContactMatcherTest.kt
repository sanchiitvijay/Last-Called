package com.lastcalled.domain

import com.lastcalled.domain.Fixtures.contact
import com.lastcalled.domain.logic.ContactMatcher
import com.lastcalled.domain.logic.PhoneNumberNormalizer.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactMatcherTest {
    private val matcher = ContactMatcher(Fixtures.all)

    @Test fun `matches regardless of number format`() {
        assertEquals("rahul", matcher.matchNumber(normalize("09876543210")))
        assertEquals("rahul", matcher.matchNumber(normalize("+919876543210")))
        assertEquals("ankit", matcher.matchNumber(normalize("+91 98111 22233")))
        assertEquals("mehul", matcher.matchNumber(normalize("+919822001122")))
    }

    @Test fun `contact with multiple numbers matches each`() {
        assertEquals("priya", matcher.matchNumber(normalize("+919900112233")))
        assertEquals("priya", matcher.matchNumber(normalize("08012345678")))
    }

    @Test fun `unknown number is not matched`() {
        assertNull(matcher.matchNumber(normalize("+14155550123")))
        assertNull(matcher.matchNumber(null))
    }

    @Test fun `duplicate contacts with the same number resolve deterministically`() {
        val dupes = ContactMatcher(listOf(contact("b-key", "Rahul (work)", "9876543210" to null), contact("a-key", "Rahul", "9876543210" to null)))
        assertEquals("a-key", dupes.matchNumber(normalize("+919876543210")))
    }

    @Test fun `exact international match beats a suffix match`() {
        val m = ContactMatcher(
            listOf(
                contact("suffix", "Local", "9876543210" to null),
                contact("exact", "Exact", "+919876543210" to "+919876543210"),
            ),
        )
        assertEquals("exact", m.matchNumber(normalize("+919876543210")))
    }

    @Test fun `contacts without phone numbers never match`() {
        val m = ContactMatcher(listOf(contact("empty", "No Number")))
        assertNull(m.matchNumber(normalize("+919876543210")))
    }

    @Test fun `name matching is case and accent insensitive but refuses ambiguity`() {
        val m = ContactMatcher(listOf(contact("j1", "José Pérez", "1111111111" to null), contact("a1", "Amit", "2222222222" to null), contact("a2", "amit", "3333333333" to null)))
        assertEquals("j1", m.matchName("jose perez"))
        assertNull(m.matchName("Amit"))
        assertNull(m.matchName("Nobody"))
    }
}

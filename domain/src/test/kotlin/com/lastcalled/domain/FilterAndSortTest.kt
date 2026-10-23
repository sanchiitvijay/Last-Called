package com.lastcalled.domain

import com.lastcalled.domain.Fixtures.DAY
import com.lastcalled.domain.Fixtures.NOW
import com.lastcalled.domain.logic.PeopleSelector
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallSnapshot
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.PersonSummary
import com.lastcalled.domain.model.SortOrder
import com.lastcalled.domain.model.TrackedPerson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTest {
    @Test fun `empty filter matches everything`() {
        assertTrue(CallFilter.ALL.matches(CallSource.UNKNOWN, CallDirection.UNKNOWN, CallType.UNKNOWN))
    }

    @Test fun `WhatsApp plus Outgoing shows only WhatsApp calls the user made`() {
        val f = CallFilter(sources = setOf(CallSource.WHATSAPP), directions = setOf(CallDirection.OUTGOING))
        assertTrue(f.matches(CallSource.WHATSAPP, CallDirection.OUTGOING, CallType.VOICE))
        assertFalse(f.matches(CallSource.WHATSAPP, CallDirection.INCOMING, CallType.VOICE))
        assertFalse(f.matches(CallSource.PHONE, CallDirection.OUTGOING, CallType.VOICE))
    }

    @Test fun `values within one dimension are alternatives`() {
        val f = CallFilter(types = setOf(CallType.VOICE, CallType.VIDEO))
        assertTrue(f.matches(CallSource.PHONE, CallDirection.OUTGOING, CallType.VIDEO))
        assertFalse(f.matches(CallSource.PHONE, CallDirection.OUTGOING, CallType.UNKNOWN))
    }

    @Test fun `video filter excludes unknown type`() {
        val f = CallFilter(types = setOf(CallType.VIDEO))
        assertFalse(f.matches(CallSource.WHATSAPP, CallDirection.OUTGOING, CallType.UNKNOWN))
    }
}

class PeopleSelectorTest {
    private fun person(key: String, name: String?, daysAgo: Long?, number: String? = null) = PersonSummary(
        key = if (name != null) PersonKey.Contact(key) else PersonKey.Number(number ?: key),
        contactName = name,
        phoneNumber = number,
        nameHint = null,
        photoUri = null,
        totalCalls = 1,
        lastMatchingCall = daysAgo?.let { CallSnapshot(NOW - it * DAY, CallDirection.OUTGOING, CallSource.PHONE, CallType.VOICE, 10) },
    )

    private val rahul = person("r", "Rahul", 4)
    private val ankit = person("a", "Ankit", 0)
    private val priya = person("p", "Priya", 23)
    private val mehul = person("m", "Mehul", null)
    private val unknown = person("u", null, 40, "+14155550123")
    private val all = listOf(rahul, ankit, priya, mehul, unknown)

    @Test fun `recently called puts most recent first and never-called last`() {
        assertEquals(listOf(ankit, rahul, priya, unknown, mehul), PeopleSelector.sort(all, SortOrder.RECENTLY_CALLED))
    }

    @Test fun `longest since last call puts never-called first then oldest`() {
        assertEquals(listOf(mehul, unknown, priya, rahul, ankit), PeopleSelector.sort(all, SortOrder.LONGEST_SINCE_CALLED))
    }

    @Test fun `alphabetical is case insensitive`() {
        val lower = person("z", "aman", 1)
        assertEquals(listOf("aman", "Ankit"), PeopleSelector.sort(listOf(ankit, lower), SortOrder.ALPHABETICAL).map { it.label })
    }

    @Test fun `search matches names case-insensitively and numbers by digits`() {
        assertEquals(listOf(rahul), PeopleSelector.search(all, "rah"))
        assertEquals(listOf(rahul), PeopleSelector.search(all, "RAHUL"))
        assertEquals(listOf(unknown), PeopleSelector.search(all, "555 01"))
        assertEquals(all, PeopleSelector.search(all, "  "))
        assertEquals(emptyList<PersonSummary>(), PeopleSelector.search(all, "zzz"))
    }

    @Test fun `not called recently keeps people beyond threshold, longest first`() {
        val list = listOf(rahul, ankit, priya, mehul)
        assertEquals(listOf(mehul, priya), PeopleSelector.notCalledRecently(list, 7, NOW))
        assertEquals(listOf(mehul, priya, rahul), PeopleSelector.notCalledRecently(list, 3, NOW))
        assertEquals(listOf(mehul), PeopleSelector.notCalledRecently(list, 30, NOW))
    }

    private fun tracked(key: PersonKey, name: String?, number: String? = null) = TrackedPerson(key, name, number, number, NOW)

    @Test fun `watch list contains only the people the user added`() {
        val list = PeopleSelector.watchList(listOf(tracked(rahul.key, "Rahul"), tracked(unknown.key, null)), all)
        assertEquals(listOf(rahul, unknown), list)
        assertEquals(listOf(unknown), PeopleSelector.notCalledRecently(list, 7, NOW))
    }

    @Test fun `added people without any calls appear as never called`() {
        val typed = tracked(PersonKey.Number("+447700900123"), null, "+447700900123")
        val newContact = tracked(PersonKey.Contact("new"), "New Friend", "+15550000000")
        val list = PeopleSelector.watchList(listOf(typed, newContact), all)
        assertEquals("+447700900123", list[0].label)
        assertEquals("New Friend", list[1].contactName)
        assertEquals(2, PeopleSelector.notCalledRecently(list, 3, NOW).size)
    }

    @Test fun `empty watch list gives an empty dashboard`() {
        assertEquals(emptyList<PersonSummary>(), PeopleSelector.notCalledRecently(PeopleSelector.watchList(emptyList(), all), 3, NOW))
    }

    @Test fun `exactly at threshold counts as not called within it`() {
        assertEquals(listOf(rahul), PeopleSelector.notCalledRecently(listOf(rahul), 4, NOW))
    }
}

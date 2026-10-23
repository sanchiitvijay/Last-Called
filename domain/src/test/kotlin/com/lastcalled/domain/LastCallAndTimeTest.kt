package com.lastcalled.domain

import com.lastcalled.domain.Fixtures.DAY
import com.lastcalled.domain.Fixtures.HOUR
import com.lastcalled.domain.Fixtures.MINUTE
import com.lastcalled.domain.Fixtures.NOW
import com.lastcalled.domain.Fixtures.record
import com.lastcalled.domain.logic.LastCallCalculator
import com.lastcalled.domain.logic.TimeSince
import com.lastcalled.domain.logic.formatDuration
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastOutgoingCallTest {
    @Test fun `only outgoing calls count`() {
        val calls = listOf(
            record(agoMillis = 1 * DAY, direction = CallDirection.INCOMING),
            record(agoMillis = 2 * DAY, direction = CallDirection.MISSED),
            record(agoMillis = 5 * DAY, direction = CallDirection.OUTGOING),
            record(agoMillis = 9 * DAY, direction = CallDirection.OUTGOING),
        )
        assertEquals(NOW - 5 * DAY, LastCallCalculator.latestOutgoing(calls)!!.timestamp)
    }

    @Test fun `no outgoing calls gives null`() {
        assertNull(LastCallCalculator.latestOutgoing(listOf(record(direction = CallDirection.INCOMING))))
        assertNull(LastCallCalculator.latestOutgoing(emptyList()))
    }

    @Test fun `zero duration outgoing call still counts as a call made`() {
        val unanswered = record(agoMillis = HOUR).copy(durationSeconds = 0)
        assertEquals(unanswered, LastCallCalculator.latestOutgoing(listOf(unanswered, record(agoMillis = DAY))))
    }

    @Test fun `filters combine with the outgoing rule`() {
        val calls = listOf(
            record(agoMillis = 1 * DAY, source = CallSource.PHONE),
            record(agoMillis = 3 * DAY, source = CallSource.WHATSAPP),
            record(agoMillis = 2 * DAY, source = CallSource.WHATSAPP, direction = CallDirection.INCOMING),
        )
        val filter = CallFilter(sources = setOf(CallSource.WHATSAPP), directions = setOf(CallDirection.OUTGOING))
        assertEquals(NOW - 3 * DAY, LastCallCalculator.latestMatching(calls, filter)!!.timestamp)
    }
}

class TimeSinceTest {
    private fun ago(ms: Long) = TimeSince.between(NOW - ms, NOW).toEnglish()

    @Test fun units() {
        assertEquals("Just now", ago(30_000))
        assertEquals("1 minute ago", ago(MINUTE))
        assertEquals("59 minutes ago", ago(59 * MINUTE + 59_000))
        assertEquals("3 hours ago", ago(3 * HOUR + 5 * MINUTE))
        assertEquals("1 day ago", ago(DAY))
        assertEquals("2 days ago", ago(2 * DAY + 23 * HOUR))
        assertEquals("18 days ago", ago(18 * DAY))
        assertEquals("364 days ago", ago(364 * DAY))
        assertEquals("1 year ago", ago(365 * DAY))
        assertEquals("3 years ago", ago(3 * 365 * DAY + DAY))
    }

    @Test fun `future timestamps (clock changes) never go negative`() {
        assertEquals("Just now", TimeSince.between(NOW + HOUR, NOW).toEnglish())
        assertEquals(0, TimeSince.wholeDays(NOW + DAY, NOW))
    }

    @Test fun `elapsed time ignores timezone and DST because it uses epoch millis`() {
        // 25 real hours across a DST change is still "1 day ago", independent of wall clock.
        assertEquals("1 day ago", TimeSince.between(NOW - 25 * HOUR, NOW).toEnglish())
    }

    @Test fun `whole days for dashboard`() {
        assertEquals(4, TimeSince.wholeDays(NOW - 4 * DAY - HOUR, NOW))
    }

    @Test fun durations() {
        assertEquals("0s", formatDuration(0))
        assertEquals("45s", formatDuration(45))
        assertEquals("12m 31s", formatDuration(751))
        assertEquals("18m 04s", formatDuration(1084))
        assertEquals("1h 02m 05s", formatDuration(3725))
        assertEquals("0s", formatDuration(-5))
    }
}

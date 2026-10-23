package com.lastcalled.domain

import com.lastcalled.domain.Fixtures.MINUTE
import com.lastcalled.domain.Fixtures.record
import com.lastcalled.domain.logic.CallDeduplicator
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.RecordOrigin
import com.lastcalled.domain.model.SystemCallKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduplicationTest {
    @Test fun `already imported rows are skipped`() {
        val a = record(systemId = 1)
        val b = record(systemId = 2)
        val result = CallDeduplicator.newSystemRecords(listOf(a, b), setOf(SystemCallKey(1, a.timestamp)))
        assertEquals(listOf(b), result)
    }

    @Test fun `duplicates within one batch are inserted once`() {
        val a = record(systemId = 7)
        assertEquals(1, CallDeduplicator.newSystemRecords(listOf(a, a.copy()), emptySet()).size)
    }

    @Test fun `a reused system id with a different date is a different call`() {
        val old = record(systemId = 3, agoMillis = 100 * MINUTE)
        val reused = record(systemId = 3, agoMillis = 1 * MINUTE)
        assertEquals(listOf(reused), CallDeduplicator.newSystemRecords(listOf(reused), setOf(SystemCallKey(3, old.timestamp))))
    }

    @Test fun `two genuine calls to the same person are both kept`() {
        val a = record(systemId = 10, agoMillis = 10 * MINUTE)
        val b = record(systemId = 11, agoMillis = 9 * MINUTE)
        assertEquals(2, CallDeduplicator.newSystemRecords(listOf(a, b), emptySet()).size)
    }

    private val system = record(source = CallSource.WHATSAPP, agoMillis = 10 * MINUTE)
    private val notification = record(source = CallSource.WHATSAPP, agoMillis = 10 * MINUTE + 40_000, origin = RecordOrigin.WHATSAPP_NOTIFICATION)

    @Test fun `notification event and call log row of the same WhatsApp call are merged`() {
        assertTrue(CallDeduplicator.isSameCall(system, notification))
        assertEquals(listOf(notification), CallDeduplicator.supersededNotificationRecords(listOf(system), listOf(notification)))
    }

    @Test fun `not merged when far apart, different person, source or direction`() {
        assertFalse(CallDeduplicator.isSameCall(system, notification.copy(timestamp = system.timestamp - 10 * MINUTE)))
        assertFalse(CallDeduplicator.isSameCall(system, notification.copy(personKey = PersonKey.Contact("other"))))
        assertFalse(CallDeduplicator.isSameCall(system.copy(source = CallSource.PHONE), notification))
        assertFalse(CallDeduplicator.isSameCall(system, notification.copy(direction = CallDirection.INCOMING)))
        assertFalse(CallDeduplicator.isSameCall(system, system.copy(id = 99)))
    }

    @Test fun `missed and rejected are compatible because notifications cannot tell them apart`() {
        assertTrue(
            CallDeduplicator.isSameCall(
                system.copy(direction = CallDirection.REJECTED),
                notification.copy(direction = CallDirection.MISSED),
            ),
        )
    }
}

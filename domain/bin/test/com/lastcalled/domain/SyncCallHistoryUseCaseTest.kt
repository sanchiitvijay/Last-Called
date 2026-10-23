package com.lastcalled.domain

import com.lastcalled.domain.Fixtures.DAY
import com.lastcalled.domain.Fixtures.MINUTE
import com.lastcalled.domain.Fixtures.NOW
import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.RawCallLogEntry
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.RecordOrigin
import com.lastcalled.domain.notification.NotificationCallEvent
import com.lastcalled.domain.notification.RecordNotificationCallUseCase
import com.lastcalled.domain.sync.CallLogSnapshot
import com.lastcalled.domain.sync.ContactsSource
import com.lastcalled.domain.sync.SourceResult
import com.lastcalled.domain.sync.SyncCallHistoryUseCase
import com.lastcalled.domain.sync.SystemCallLogSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCallHistoryUseCaseTest {
    private var callLog: SourceResult<CallLogSnapshot> = SourceResult.Available(CallLogSnapshot(emptyList(), false))
    private var contacts: SourceResult<List<DeviceContact>> = SourceResult.Available(Fixtures.all)
    private val store = InMemoryCallStore()

    private val sync = SyncCallHistoryUseCase(
        object : SystemCallLogSource { override suspend fun readCallLog() = callLog },
        object : ContactsSource { override suspend fun readContacts() = contacts },
        store,
    ) { NOW }

    private fun log(vararg rows: RawCallLogEntry) {
        callLog = SourceResult.Available(CallLogSnapshot(rows.toList(), voipRowsRequested = true))
    }

    @Test fun `imports, classifies and matches calls`() = runTest {
        log(
            Fixtures.phoneOutgoing(), Fixtures.phoneIncoming(), Fixtures.missed(),
            Fixtures.whatsAppVoice(), Fixtures.whatsAppVideo(), Fixtures.carrierVideo(), Fixtures.unknownCaller(),
        )
        val result = sync()
        assertEquals(7, result.inserted)
        assertEquals(2, result.whatsAppRowsRead)
        val rahul = store.records.filter { it.personKey == PersonKey.Contact("rahul") }
        assertEquals(6, rahul.size)
        assertEquals(2, rahul.count { it.source == CallSource.WHATSAPP })
        assertEquals(2, rahul.count { it.callType == CallType.VIDEO })
        assertEquals(1, rahul.count { it.direction == CallDirection.MISSED })
        val unknown = store.records.single { it.contactKey == null }
        assertEquals(PersonKey.Number("+14155550123"), unknown.personKey)
        assertEquals(listOf("rahul"), store.contacts.map { it.lookupKey })
    }

    @Test fun `repeated refresh does not insert duplicates`() = runTest {
        log(Fixtures.phoneOutgoing(), Fixtures.whatsAppVoice())
        sync()
        val second = sync()
        assertEquals(0, second.inserted)
        assertEquals(2, second.alreadyImported)
        assertEquals(2, store.records.size)
    }

    @Test fun `rows dropped by the system are kept locally`() = runTest {
        log(Fixtures.phoneOutgoing(agoMillis = 400 * DAY), Fixtures.phoneOutgoing())
        sync()
        log(Fixtures.phoneOutgoing(agoMillis = 0))
        sync()
        assertEquals(3, store.records.size)
    }

    @Test fun `renamed contact keeps the same person, deleted contact becomes unknown`() = runTest {
        log(Fixtures.phoneOutgoing())
        sync()
        contacts = SourceResult.Available(listOf(Fixtures.rahul.copy(displayName = "Rahul S.")))
        sync()
        assertEquals("Rahul S.", store.contacts.single().displayName)
        assertEquals(PersonKey.Contact("rahul"), store.records.single().personKey)

        contacts = SourceResult.Available(emptyList())
        val result = sync()
        assertEquals(1, result.remapped)
        assertEquals(PersonKey.Number("+919876543210"), store.records.single().personKey)
        assertTrue(store.contacts.isEmpty())
    }

    @Test fun `contact added later is matched on next refresh`() = runTest {
        contacts = SourceResult.Available(emptyList())
        log(Fixtures.unknownCaller())
        sync()
        contacts = SourceResult.Available(listOf(Fixtures.contact("new", "New Friend", "+1 415 555 0123" to "+14155550123")))
        sync()
        assertEquals(PersonKey.Contact("new"), store.records.single().personKey)
    }

    @Test fun `without contacts permission previous names are kept`() = runTest {
        log(Fixtures.phoneOutgoing())
        sync()
        contacts = SourceResult.PermissionMissing
        log(Fixtures.phoneOutgoing(), Fixtures.phoneIncoming())
        val result = sync()
        assertFalse(result.contactsAvailable)
        assertTrue(store.records.all { it.personKey == PersonKey.Contact("rahul") })
        assertEquals(1, store.contacts.size)
    }

    @Test fun `without call log permission nothing is imported and nothing is erased`() = runTest {
        log(Fixtures.phoneOutgoing())
        sync()
        callLog = SourceResult.PermissionMissing
        val result = sync()
        assertFalse(result.callLogAvailable)
        assertEquals(1, store.records.size)
    }

    @Test fun `empty call log`() = runTest {
        val result = sync()
        assertTrue(result.callLogAvailable)
        assertEquals(0, result.inserted)
    }

    @Test fun `withheld numbers are grouped as hidden`() = runTest {
        log(Fixtures.raw("", presentation = 2), Fixtures.raw(null, presentation = 3))
        sync()
        assertTrue(store.records.all { it.personKey == PersonKey.Hidden && it.phoneNumber == null })
    }

    @Test fun `large history imports`() = runTest {
        log(*Array(5_000) { i -> Fixtures.raw("+9198765${(10000 + i % 300)}", agoMillis = i * MINUTE) })
        val result = sync()
        assertEquals(5_000, result.inserted)
        assertEquals(300, store.records.map { it.personKey }.distinct().size)
    }

    @Test fun `system WhatsApp row supersedes a notification record of the same call`() = runTest {
        val record = RecordNotificationCallUseCase(
            object : ContactsSource { override suspend fun readContacts() = contacts },
            store,
        ) { NOW }
        record(NotificationCallEvent("Rahul Sharma", NOW - DAY + 30_000, 60, CallDirection.OUTGOING, CallType.VOICE))
        assertEquals(RecordOrigin.WHATSAPP_NOTIFICATION, store.records.single().origin)

        log(Fixtures.whatsAppVoice(agoMillis = DAY))
        val result = sync()
        assertEquals(1, result.notificationRecordsMerged)
        assertEquals(RecordOrigin.SYSTEM_CALL_LOG, store.records.single().origin)
    }
}

class RecordNotificationCallUseCaseTest {
    private val store = InMemoryCallStore()
    private val record = RecordNotificationCallUseCase(
        object : ContactsSource { override suspend fun readContacts() = SourceResult.Available(Fixtures.all) },
        store,
    ) { NOW }

    @Test fun `matches contact by exact name`() = runTest {
        assertTrue(record(NotificationCallEvent("rahul sharma", NOW, 30, CallDirection.OUTGOING, CallType.VIDEO)))
        val r = store.records.single()
        assertEquals(PersonKey.Contact("rahul"), r.personKey)
        assertEquals(CallSource.WHATSAPP, r.source)
        assertNull(r.systemCallLogId)
        assertEquals(1, store.contacts.size)
    }

    @Test fun `matches contact by number when WhatsApp shows a number`() = runTest {
        record(NotificationCallEvent("+91 98111 22233", NOW, 30, CallDirection.INCOMING, CallType.VOICE))
        assertEquals(PersonKey.Contact("ankit"), store.records.single().personKey)
    }

    @Test fun `unknown name is stored as a name-only person`() = runTest {
        record(NotificationCallEvent("Someone New", NOW, 30, CallDirection.OUTGOING, CallType.UNKNOWN))
        assertEquals(PersonKey.Name("Someone New"), store.records.single().personKey)
    }

    @Test fun `not stored when the system call log already has it`() = runTest {
        store.insertRecord(Fixtures.record(source = CallSource.WHATSAPP, agoMillis = 0), null)
        assertFalse(record(NotificationCallEvent("Rahul Sharma", NOW + 20_000, 30, CallDirection.OUTGOING, CallType.VOICE)))
        assertEquals(1, store.records.size)
    }

    @Test fun `same event delivered twice is stored once`() = runTest {
        val e = NotificationCallEvent("Rahul Sharma", NOW, 30, CallDirection.OUTGOING, CallType.VOICE)
        record(e)
        record(e)
        assertEquals(1, store.records.size)
    }
}

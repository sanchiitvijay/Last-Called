package com.lastcalled.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastcalled.app.data.local.AppDatabase
import com.lastcalled.app.data.local.RoomCallStore
import com.lastcalled.app.data.repository.CallHistoryRepository
import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.DevicePhoneNumber
import com.lastcalled.domain.logic.RawCallLogEntry
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.sync.CallLogSnapshot
import com.lastcalled.domain.sync.ContactsSource
import com.lastcalled.domain.sync.SourceResult
import com.lastcalled.domain.sync.SyncCallHistoryUseCase
import com.lastcalled.domain.sync.SystemCallLogSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Full import pipeline against a real Room database: matching, classification, dedup, re-matching. */
@RunWith(AndroidJUnit4::class)
class SyncIntegrationTest {
    private val now = TestData.NOW
    private val day = TestData.DAY
    private lateinit var db: AppDatabase
    private var rows = listOf<RawCallLogEntry>()
    private var contacts = listOf(
        DeviceContact("rahul", 1, "Rahul Sharma", null, listOf(DevicePhoneNumber("+91 98765 43210", "+919876543210", "Mobile"))),
        DeviceContact("ankit", 2, "Ankit", null, listOf(DevicePhoneNumber("098111 22233", "+919811122233", null))),
    )
    private lateinit var sync: SyncCallHistoryUseCase

    private fun row(id: Long, number: String, type: Int, daysAgo: Long, component: String, features: Int = 0) =
        RawCallLogEntry(id, number, now - daysAgo * day, 30, type, features, component, 1, null)

    @Before fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        sync = SyncCallHistoryUseCase(
            object : SystemCallLogSource { override suspend fun readCallLog() = SourceResult.Available(CallLogSnapshot(rows, true)) },
            object : ContactsSource { override suspend fun readContacts() = SourceResult.Available(contacts) },
            RoomCallStore(db),
        ) { now }
    }

    @After fun tearDown() = db.close()

    @Test fun importsMatchesAndDeduplicates() = runTest {
        rows = listOf(
            row(1, "9876543210", 2, 4, "com.whatsapp/x"),
            row(2, "+919876543210", 1, 9, "com.android.phone/y"),
            row(3, "+91 98111 22233", 2, 1, "com.android.phone/y", features = 1),
            row(4, "+14155550123", 3, 2, "com.android.phone/y"),
            row(5, "+442071838750", 1, 1, "org.example.voip/z"),
        )
        assertEquals(5, sync().inserted)
        assertEquals(0, sync().inserted)

        val people = CallHistoryRepository(db).observePeople(CallFilter.OUTGOING).first().associateBy { it.key }
        assertEquals(4, people.size)
        val rahul = people.getValue(PersonKey.Contact("rahul"))
        assertEquals(CallSource.WHATSAPP, rahul.lastMatchingCall!!.source)
        assertEquals(CallType.VOICE, rahul.lastMatchingCall!!.callType)
        assertEquals(CallType.VIDEO, people.getValue(PersonKey.Contact("ankit")).lastMatchingCall!!.callType)
        // Another VoIP app is never labelled WhatsApp.
        val other = CallHistoryRepository(db).observePeople(CallFilter.ALL).first().single { it.key == PersonKey.Number("+442071838750") }
        assertEquals(CallSource.UNKNOWN, other.lastMatchingCall!!.source)
        assertEquals(CallType.UNKNOWN, other.lastMatchingCall!!.callType)
    }

    @Test fun contactRenameAndDeletionAreReflected() = runTest {
        rows = listOf(row(1, "+919876543210", 2, 1, "com.android.phone/y"))
        sync()
        contacts = listOf(contacts[0].copy(displayName = "Rahul S"))
        sync()
        assertEquals("Rahul S", CallHistoryRepository(db).observePerson(PersonKey.Contact("rahul")).first().contactName)
        contacts = emptyList()
        sync()
        val keys = CallHistoryRepository(db).observePeople(CallFilter.ALL).first().map { it.key }
        assertEquals(listOf<PersonKey>(PersonKey.Number("+919876543210")), keys)
    }
}

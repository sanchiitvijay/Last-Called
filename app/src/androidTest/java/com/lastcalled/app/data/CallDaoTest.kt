package com.lastcalled.app.data

import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastcalled.app.data.TestData.call
import com.lastcalled.app.data.local.AppDatabase
import com.lastcalled.app.data.local.ContactEntity
import com.lastcalled.app.data.local.ContactPhoneNumberEntity
import com.lastcalled.app.data.local.RoomCallStore
import com.lastcalled.app.data.local.toEntity
import com.lastcalled.app.data.repository.CallHistoryRepository
import com.lastcalled.app.data.repository.SettingsRepository
import com.lastcalled.domain.logic.LastCallCalculator
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.SortOrder
import com.lastcalled.domain.model.TrackedPerson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room persistence and the SQL that derives "last called" from stored records. */
@RunWith(AndroidJUnit4::class)
class CallDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CallHistoryRepository

    private val rahul = PersonKey.Contact("rahul")
    private val unknown = PersonKey.Number("+14155550123")

    private val calls = listOf(
        call(rahul, 1.0, CallDirection.INCOMING),
        call(rahul, 4.0, CallDirection.OUTGOING, CallSource.WHATSAPP, CallType.VOICE, duration = 751),
        call(rahul, 11.0, CallDirection.OUTGOING, CallSource.PHONE),
        call(rahul, 17.0, CallDirection.OUTGOING, CallSource.WHATSAPP, CallType.VIDEO),
        call(unknown, 2.0, CallDirection.MISSED, number = "+14155550123"),
    )

    @Before fun setUp() = runTest {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = CallHistoryRepository(db)
        db.callDao().replaceContacts(
            listOf(ContactEntity("rahul", 1, "Rahul Sharma", null, 0)),
            listOf(ContactPhoneNumberEntity("rahul", "+91 98765 43210", "+919876543210", "Mobile")),
        )
        db.callDao().insertAll(calls.map { it.toEntity() })
    }

    @After fun tearDown() = db.close()

    @Test fun summaryUsesLatestOutgoingCall() = runTest {
        val people = repo.observePeople(CallFilter.OUTGOING).first()
        val r = people.single { it.key == rahul }
        val expected = LastCallCalculator.latestOutgoing(calls.filter { it.personKey == rahul })!!
        assertEquals(expected.timestamp, r.lastMatchingCall!!.timestamp)
        assertEquals(CallSource.WHATSAPP, r.lastMatchingCall!!.source)
        assertEquals(751L, r.lastMatchingCall!!.durationSeconds)
        assertEquals("Rahul Sharma", r.contactName)
        assertEquals(4, r.totalCalls)
    }

    @Test fun unknownCallerWithoutOutgoingIsListedAsNeverCalled() = runTest {
        val u = repo.observePeople(CallFilter.OUTGOING).first().single { it.key == unknown }
        assertNull(u.lastMatchingCall)
        assertNull(u.contactName)
        assertEquals("+14155550123", u.phoneNumber)
    }

    @Test fun sqlFiltersAgreeWithDomainFilter() = runTest {
        val filters = listOf(
            CallFilter.ALL,
            CallFilter(sources = setOf(CallSource.WHATSAPP), directions = setOf(CallDirection.OUTGOING)),
            CallFilter(types = setOf(CallType.VIDEO)),
            CallFilter(sources = setOf(CallSource.PHONE)),
            CallFilter(directions = setOf(CallDirection.INCOMING, CallDirection.MISSED)),
        )
        for (f in filters) {
            val sql = repo.observePeople(f).first().associate { it.key to it.lastMatchingCall?.timestamp }
            val expected = calls.groupBy { it.personKey }.mapValues { (_, c) -> LastCallCalculator.latestMatching(c, f)?.timestamp }
            assertEquals("filter $f", expected, sql)
        }
    }

    @Test fun duplicateSystemRowsAreIgnoredByUniqueIndex() = runTest {
        val ids = db.callDao().insertAll(calls.map { it.toEntity() })
        assertTrue(ids.all { it == -1L })
        assertEquals(calls.size, repo.observeStats().first().total)
    }

    @Test fun historyIsPagedNewestFirstAndFiltered() = runTest {
        val source = db.callDao().pagedHistory(rahul.value, false, listOf("WHATSAPP"), true, emptyList(), true, emptyList())
        val page = source.load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page
        assertEquals(2, page.data.size)
        assertTrue(page.data[0].timestamp > page.data[1].timestamp)
    }

    @Test fun personDetailsCombineContactAndLatestOutgoing() = runTest {
        val d = repo.observePerson(rahul).first()
        assertEquals("Rahul Sharma", d.contactName)
        assertEquals("+91 98765 43210", d.numbers.single().number)
        assertEquals(CallSource.WHATSAPP, d.latestOutgoing!!.source)
        assertEquals(CallDirection.INCOMING, d.latestAny!!.direction)
    }

    @Test fun statsCountWhatsAppAndOutgoing() = runTest {
        val s = repo.observeStats().first()
        assertEquals(5, s.total)
        assertEquals(2, s.whatsApp)
        assertEquals(3, s.outgoing)
    }

    @Test fun resetDeletesRecordsAndContactsButKeepsSettingsAndWatchList() = runTest {
        val settings = SettingsRepository(db.settingsDao())
        settings.setSortOrder(SortOrder.ALPHABETICAL)
        RoomCallStore(db).upsertTracked(TrackedPerson(rahul, "Rahul Sharma", "+919876543210", "+919876543210", 1), null)
        repo.resetImportedRecords()
        assertEquals(listOf(rahul), repo.observeTracked().first().map { it.personKey })
        assertEquals(0, repo.observeStats().first().total)
        assertTrue(db.callDao().allContacts().isEmpty())
        assertEquals(SortOrder.ALPHABETICAL, settings.current().sortOrder)
    }

    @Test fun watchListPersistsAndSyncUpdatesMoveEntries() = runTest {
        val store = RoomCallStore(db)
        store.upsertTracked(TrackedPerson(unknown, null, "+14155550123", "+14155550123", 1), null)
        store.upsertTracked(TrackedPerson(unknown, "Same", "+14155550123", "+14155550123", 2), null)
        assertEquals(1, repo.observeTracked().first().size)
        store.applySync(
            com.lastcalled.domain.sync.SyncPlan(
                newRecords = emptyList(), personUpdates = emptyList(), contacts = null, recordIdsToDelete = emptyList(),
                trackedUpdates = listOf(
                    com.lastcalled.domain.sync.TrackedUpdate(unknown, TrackedPerson(PersonKey.Contact("x"), "X", "+14155550123", "+14155550123", 2)),
                ),
                syncedAt = 3, voipRowsRequested = false,
            ),
        )
        assertEquals(listOf<PersonKey>(PersonKey.Contact("x")), repo.observeTracked().first().map { it.personKey })
        repo.clearTracked()
        assertTrue(repo.observeTracked().first().isEmpty())
    }

    @Test fun settingsPersistAndReset() = runTest {
        val settings = SettingsRepository(db.settingsDao())
        assertEquals(7, settings.current().defaultThresholdDays)
        settings.setDefaultThreshold(14)
        settings.setNotificationDetection(true)
        assertEquals(14, settings.settings.first().defaultThresholdDays)
        assertTrue(settings.current().notificationDetectionEnabled)
        settings.reset()
        assertEquals(7, settings.current().defaultThresholdDays)
    }
}


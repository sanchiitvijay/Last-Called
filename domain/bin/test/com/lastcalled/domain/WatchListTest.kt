package com.lastcalled.domain

import com.lastcalled.domain.Fixtures.NOW
import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.sync.CallLogSnapshot
import com.lastcalled.domain.sync.ContactsSource
import com.lastcalled.domain.sync.SourceResult
import com.lastcalled.domain.sync.SyncCallHistoryUseCase
import com.lastcalled.domain.sync.SystemCallLogSource
import com.lastcalled.domain.sync.WatchListUseCase
import com.lastcalled.domain.sync.WatchListUseCase.AddResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchListTest {
    private var contacts: List<DeviceContact> = Fixtures.all
    private val store = InMemoryCallStore()
    private val contactsSource = object : ContactsSource { override suspend fun readContacts() = SourceResult.Available(contacts) }
    private val watchList = WatchListUseCase(contactsSource, store) { NOW }
    private val sync = SyncCallHistoryUseCase(
        object : SystemCallLogSource { override suspend fun readCallLog() = SourceResult.Available(CallLogSnapshot(listOf(Fixtures.phoneOutgoing()), false)) },
        contactsSource,
        store,
    ) { NOW }

    @Test fun `typed number that belongs to a contact is tracked as that contact`() = runTest {
        val result = watchList.addNumber("098111 22233") as AddResult.Added
        assertEquals(PersonKey.Contact("ankit"), result.person.personKey)
        assertEquals("Ankit", result.person.displayName)
        assertEquals(listOf("ankit"), store.contacts.map { it.lookupKey })
    }

    @Test fun `typed unknown number is tracked by number`() = runTest {
        val result = watchList.addNumber("+44 7700 900123") as AddResult.Added
        assertEquals(PersonKey.Number("+447700900123"), result.person.personKey)
    }

    @Test fun `text that is not a number is rejected`() = runTest {
        assertEquals(AddResult.InvalidNumber, watchList.addNumber("Rahul"))
        assertTrue(store.tracked.isEmpty())
    }

    @Test fun `adding a contact without calls stores it so its name shows`() = runTest {
        watchList.addContact(Fixtures.mehul)
        assertEquals(PersonKey.Contact("mehul"), store.tracked.keys.single())
        assertEquals("Mehul", store.contacts.single().displayName)
    }

    @Test fun `adding twice keeps one entry and remove deletes it`() = runTest {
        watchList.addContact(Fixtures.rahul)
        watchList.addExisting(PersonKey.Contact("rahul"), "Rahul Sharma", "+919876543210")
        assertEquals(1, store.tracked.size)
        watchList.remove(PersonKey.Contact("rahul"))
        assertTrue(store.tracked.isEmpty())
    }

    @Test fun `hidden numbers cannot be tracked`() = runTest {
        watchList.addExisting(PersonKey.Hidden, null, null)
        assertTrue(store.tracked.isEmpty())
    }

    @Test fun `tracked entry follows the person when the contact is deleted or saved later`() = runTest {
        watchList.addContact(Fixtures.rahul)
        contacts = emptyList()
        sync()
        assertEquals(PersonKey.Number("+919876543210"), store.tracked.keys.single())

        contacts = listOf(Fixtures.rahul.copy(displayName = "Rahul (new)"))
        sync()
        val t = store.tracked.values.single()
        assertEquals(PersonKey.Contact("rahul"), t.personKey)
        assertEquals("Rahul (new)", t.displayName)
    }

    @Test fun `tracked contact without calls is kept in stored contacts after sync`() = runTest {
        watchList.addContact(Fixtures.mehul)
        sync()
        assertTrue(store.contacts.any { it.lookupKey == "mehul" })
    }
}

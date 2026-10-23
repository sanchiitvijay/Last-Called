package com.lastcalled.domain.sync

import com.lastcalled.domain.logic.ContactMatcher
import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.PersonResolver
import com.lastcalled.domain.logic.PhoneNumberNormalizer
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.TrackedPerson

/**
 * Adds and removes people on the user's "keep in touch" list. Entries are resolved to the same
 * [PersonKey] the call records use, so a typed-in number that belongs to a contact (or to an
 * existing unknown caller) joins up with that person's call history.
 */
class WatchListUseCase(
    private val contacts: ContactsSource,
    private val store: CallStore,
    private val clock: () -> Long,
) {
    sealed interface AddResult {
        data class Added(val person: TrackedPerson) : AddResult
        data object InvalidNumber : AddResult
    }

    /** Adds a phone number typed by the user. */
    suspend fun addNumber(raw: String): AddResult {
        if (!PhoneNumberNormalizer.looksLikePhoneNumber(raw)) return AddResult.InvalidNumber
        val number = PhoneNumberNormalizer.normalize(raw) ?: return AddResult.InvalidNumber
        val contactList = currentContacts()
        val resolution = PersonResolver.resolve(number, null, ContactMatcher(contactList))
        val contact = resolution.contactKey?.let { key -> contactList.firstOrNull { it.lookupKey == key } }
        val person = TrackedPerson(resolution.personKey, contact?.displayName, number.raw, number.canonical, clock())
        store.upsertTracked(person, contact)
        return AddResult.Added(person)
    }

    /** Adds a device contact (picked from the contact list), even if there are no calls yet. */
    suspend fun addContact(contact: DeviceContact) {
        val first = contact.phoneNumbers.firstOrNull()
        val number = first?.let { PhoneNumberNormalizer.normalize(it.number, it.platformE164) }
        store.upsertTracked(
            TrackedPerson(PersonKey.Contact(contact.lookupKey), contact.displayName, number?.raw, number?.canonical, clock()),
            contact,
        )
    }

    /** Adds a person that already has call history in the app. Hidden numbers cannot be tracked. */
    suspend fun addExisting(key: PersonKey, displayName: String?, phoneNumber: String?) {
        if (key is PersonKey.Hidden) return
        val number = PhoneNumberNormalizer.normalize(phoneNumber)
        store.upsertTracked(TrackedPerson(key, displayName, number?.raw, number?.canonical, clock()), contact = null)
    }

    suspend fun remove(key: PersonKey) = store.removeTracked(key)

    private suspend fun currentContacts(): List<DeviceContact> =
        (contacts.readContacts() as? SourceResult.Available)?.data ?: store.storedContacts()
}

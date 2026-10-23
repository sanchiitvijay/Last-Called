package com.lastcalled.domain.logic

import java.text.Normalizer

/** A device contact as needed for matching. */
data class DeviceContact(
    val lookupKey: String,
    val deviceContactId: Long,
    val displayName: String,
    val photoThumbnailUri: String?,
    val phoneNumbers: List<DevicePhoneNumber>,
)

data class DevicePhoneNumber(
    val number: String,
    val platformE164: String?,
    /** Human readable label such as "Mobile"; may be null. */
    val label: String?,
)

/**
 * Resolves phone numbers (and, for notification-only records, display names) to device contacts.
 *
 * Built once per sync from a snapshot of the contacts provider. Lookups are O(1) on average via an
 * index on the trailing digits.
 *
 * Ambiguity rules (e.g. the same number saved in two contacts): the strongest number match wins;
 * ties are broken by lookup key so the result is deterministic across syncs.
 */
class ContactMatcher(contacts: List<DeviceContact>) {

    private data class Entry(val lookupKey: String, val number: NormalizedNumber)

    private val byIndexKey: Map<String, List<Entry>>
    private val byName: Map<String, List<String>>

    init {
        val entries = contacts.flatMap { contact ->
            contact.phoneNumbers.mapNotNull { phone ->
                PhoneNumberNormalizer.normalize(phone.number, phone.platformE164)
                    ?.takeIf { it.significantDigits.isNotEmpty() }
                    ?.let { Entry(contact.lookupKey, it) }
            }
        }
        byIndexKey = entries.groupBy { it.number.indexKey }
        byName = contacts
            .groupBy({ nameKey(it.displayName) }, { it.lookupKey })
            .mapValues { (_, keys) -> keys.distinct() }
    }

    /** Returns the lookup key of the best matching contact, or null when the number is unknown. */
    fun matchNumber(number: NormalizedNumber?): String? {
        if (number == null || number.significantDigits.isEmpty()) return null
        val candidates = byIndexKey[number.indexKey] ?: return null
        return candidates
            .map { it to PhoneNumberNormalizer.matchStrength(number, it.number) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.lookupKey })
            .firstOrNull()
            ?.first
            ?.lookupKey
    }

    /**
     * Matches a display name exactly (case and accent insensitive). Returns null when no contact or
     * more than one contact has that name, because guessing between two people would be wrong.
     */
    fun matchName(displayName: String?): String? {
        if (displayName.isNullOrBlank()) return null
        return byName[nameKey(displayName)]?.singleOrNull()
    }

    companion object {
        fun nameKey(name: String): String =
            Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
                .replace(Regex("\\s+"), " ")
    }
}

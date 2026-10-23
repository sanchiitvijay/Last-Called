package com.lastcalled.domain.logic

import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.PersonSummary
import com.lastcalled.domain.model.SortOrder
import com.lastcalled.domain.model.TrackedPerson
import java.text.Collator
import java.text.Normalizer

/** Sorting, searching and threshold selection over aggregated person summaries. */
object PeopleSelector {

    fun sort(people: List<PersonSummary>, order: SortOrder): List<PersonSummary> {
        // Collator is not thread-safe, so create one per call.
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val byName = compareBy<PersonSummary, String>(collator) { it.label }
        return when (order) {
            // Most recent first; people never called go last.
            SortOrder.RECENTLY_CALLED -> people.sortedWith(
                compareBy<PersonSummary> { it.lastMatchingCall == null }
                    .thenByDescending { it.lastMatchingCall?.timestamp ?: 0 }
                    .then(byName),
            )
            // People never called first (the longest possible gap), then oldest call first.
            SortOrder.LONGEST_SINCE_CALLED -> people.sortedWith(
                compareBy<PersonSummary> { it.lastMatchingCall != null }
                    .thenBy { it.lastMatchingCall?.timestamp ?: 0 }
                    .then(byName),
            )
            SortOrder.ALPHABETICAL -> people.sortedWith(byName)
        }
    }

    /**
     * Case- and accent-insensitive search over contact names and number hints. If the query contains
     * at least three digits it also matches phone numbers by digits ("98765" finds "+91 98765 43210").
     */
    fun search(people: List<PersonSummary>, query: String): List<PersonSummary> {
        val q = fold(query)
        if (q.isEmpty()) return people
        val queryDigits = query.filter { it.isDigit() }
        return people.filter { p ->
            val nameMatch = listOfNotNull(p.contactName, p.nameHint).any { fold(it).contains(q) }
            val numberMatch = queryDigits.length >= 3 &&
                (p.phoneNumber?.filter { it.isDigit() }?.contains(queryDigits) == true)
            nameMatch || numberMatch
        }
    }

    /**
     * The user's watch list as person summaries. People on the list with no stored calls at all (for
     * example a number typed in by hand) are still included, with no last call.
     */
    fun watchList(tracked: List<TrackedPerson>, summaries: List<PersonSummary>): List<PersonSummary> {
        val byKey = summaries.associateBy { it.key }
        return tracked.distinctBy { it.personKey }.map { t ->
            byKey[t.personKey]?.let { s -> if (s.contactName == null && s.nameHint == null) s.copy(nameHint = t.displayName) else s }
                ?: PersonSummary(
                    key = t.personKey,
                    contactName = if (t.personKey is PersonKey.Contact) t.displayName else null,
                    phoneNumber = t.phoneNumber,
                    nameHint = if (t.personKey is PersonKey.Contact) null else t.displayName,
                    photoUri = null,
                    totalCalls = 0,
                    lastMatchingCall = null,
                )
        }
    }

    /**
     * People from [people] (normally the watch list) not called for at least [thresholdDays],
     * including people never called, longest gap first.
     */
    fun notCalledRecently(people: List<PersonSummary>, thresholdDays: Int, nowMillis: Long): List<PersonSummary> {
        val cutoff = nowMillis - TimeSince.daysToMillis(thresholdDays)
        return sort(
            people.filter { it.lastMatchingCall == null || it.lastMatchingCall.timestamp <= cutoff },
            SortOrder.LONGEST_SINCE_CALLED,
        )
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}

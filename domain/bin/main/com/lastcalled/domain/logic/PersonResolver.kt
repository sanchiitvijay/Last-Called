package com.lastcalled.domain.logic

import com.lastcalled.domain.model.PersonKey

data class Resolution(val personKey: PersonKey, val contactKey: String?)

/** Decides which person a call belongs to. Used both at import time and when re-matching. */
object PersonResolver {
    fun resolve(number: NormalizedNumber?, nameHint: String?, matcher: ContactMatcher): Resolution {
        if (number != null && number.significantDigits.isNotEmpty()) {
            val contact = matcher.matchNumber(number)
            return if (contact != null) {
                Resolution(PersonKey.Contact(contact), contact)
            } else {
                Resolution(PersonKey.Number(number.canonical), null)
            }
        }
        if (!nameHint.isNullOrBlank()) {
            val contact = matcher.matchName(nameHint)
            return if (contact != null) {
                Resolution(PersonKey.Contact(contact), contact)
            } else {
                Resolution(PersonKey.Name(nameHint.trim()), null)
            }
        }
        return Resolution(PersonKey.Hidden, null)
    }
}

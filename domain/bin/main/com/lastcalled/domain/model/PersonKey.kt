package com.lastcalled.domain.model

/**
 * Stable identity of a "person" row in the app. Calls are grouped by this key.
 *
 * - [Contact]: the call matched a device contact (identified by its lookup key).
 * - [Number]: the call did not match a contact; grouped by canonical phone number.
 * - [Name]: a notification-only record that carried a display name but no number and did not
 *   match a contact.
 * - [Hidden]: the number was withheld / private / unavailable.
 */
sealed interface PersonKey {
    val value: String

    data class Contact(val lookupKey: String) : PersonKey {
        override val value get() = "c:$lookupKey"
    }

    data class Number(val canonicalNumber: String) : PersonKey {
        override val value get() = "n:$canonicalNumber"
    }

    data class Name(val displayName: String) : PersonKey {
        override val value get() = "w:$displayName"
    }

    data object Hidden : PersonKey {
        override val value get() = "h:"
    }

    companion object {
        fun parse(value: String): PersonKey? {
            val payload = value.substring(minOf(2, value.length))
            return when {
                value.startsWith("c:") && payload.isNotEmpty() -> Contact(payload)
                value.startsWith("n:") && payload.isNotEmpty() -> Number(payload)
                value.startsWith("w:") && payload.isNotEmpty() -> Name(payload)
                value == "h:" -> Hidden
                else -> null
            }
        }
    }
}

package com.lastcalled.domain.model

/** One row on the home screen, aggregated from persisted call records. */
data class PersonSummary(
    val key: PersonKey,
    /** Current contact name, or null for unknown numbers. */
    val contactName: String?,
    val phoneNumber: String?,
    /** Name from a WhatsApp notification, used when there is no contact name or number. */
    val nameHint: String?,
    val photoUri: String?,
    val totalCalls: Int,
    /** Latest call that matches the active filter (by default: latest outgoing call). */
    val lastMatchingCall: CallSnapshot?,
) {
    val isContact: Boolean get() = key is PersonKey.Contact

    /** Name used for display and alphabetical sorting. */
    val label: String
        get() = contactName ?: phoneNumber ?: nameHint ?: ""
}

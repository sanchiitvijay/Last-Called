package com.lastcalled.domain.model

/**
 * Someone the user explicitly added to their "keep in touch" list. Only these people appear in the
 * "People not called recently" dashboard.
 *
 * The number is kept so the entry can follow the person when contacts change (a contact deleted,
 * or an unknown number saved as a contact later).
 */
data class TrackedPerson(
    val personKey: PersonKey,
    /** Name at the time it was added / last synced; used when no call or contact supplies one. */
    val displayName: String?,
    val phoneNumber: String?,
    val normalizedNumber: String?,
    val addedAt: Long,
)

package com.lastcalled.domain.model

/**
 * Filter over calls. An empty set means "no restriction" for that dimension, so filters combine
 * naturally: `CallFilter(sources = setOf(WHATSAPP), directions = setOf(OUTGOING))` is
 * "WhatsApp calls I made".
 */
data class CallFilter(
    val sources: Set<CallSource> = emptySet(),
    val directions: Set<CallDirection> = emptySet(),
    val types: Set<CallType> = emptySet(),
) {
    fun matches(source: CallSource, direction: CallDirection, type: CallType): Boolean =
        (sources.isEmpty() || source in sources) &&
            (directions.isEmpty() || direction in directions) &&
            (types.isEmpty() || type in types)

    fun matches(record: CallRecord): Boolean = matches(record.source, record.direction, record.callType)

    val isOutgoingOnly: Boolean get() = directions == setOf(CallDirection.OUTGOING)

    companion object {
        val ALL = CallFilter()

        /** Default for the home screen: the app's primary metric is the last call *you* made. */
        val OUTGOING = CallFilter(directions = setOf(CallDirection.OUTGOING))
    }
}

enum class SortOrder { RECENTLY_CALLED, LONGEST_SINCE_CALLED, ALPHABETICAL }

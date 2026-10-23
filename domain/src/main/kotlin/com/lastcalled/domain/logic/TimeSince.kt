package com.lastcalled.domain.logic

/**
 * Elapsed time between a call and "now", for human readable labels ("3 hours ago").
 *
 * Calculated from epoch milliseconds, so timezone and daylight-saving changes do not affect it.
 * Only the timestamp is persisted; this value is always computed on demand.
 */
data class TimeSince(val amount: Long, val unit: Unit) {
    enum class Unit { JUST_NOW, MINUTES, HOURS, DAYS, YEARS }

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        fun between(thenMillis: Long, nowMillis: Long): TimeSince {
            // A timestamp in the future (clock changed, or the call log came from another device) is shown as "just now".
            val elapsed = (nowMillis - thenMillis).coerceAtLeast(0)
            return when {
                elapsed < MINUTE -> TimeSince(0, Unit.JUST_NOW)
                elapsed < HOUR -> TimeSince(elapsed / MINUTE, Unit.MINUTES)
                elapsed < DAY -> TimeSince(elapsed / HOUR, Unit.HOURS)
                elapsed < 365 * DAY -> TimeSince(elapsed / DAY, Unit.DAYS)
                else -> TimeSince(elapsed / (365 * DAY), Unit.YEARS)
            }
        }

        /** Whole days elapsed, used by the "not called in N days" dashboard. */
        fun wholeDays(thenMillis: Long, nowMillis: Long): Long = ((nowMillis - thenMillis) / DAY).coerceAtLeast(0)

        fun daysToMillis(days: Int): Long = days * DAY
    }

    /** English rendering, used by tests and as a fallback; the UI uses localized plurals. */
    fun toEnglish(): String = when (unit) {
        Unit.JUST_NOW -> "Just now"
        Unit.MINUTES -> plural(amount, "minute") + " ago"
        Unit.HOURS -> plural(amount, "hour") + " ago"
        Unit.DAYS -> plural(amount, "day") + " ago"
        Unit.YEARS -> plural(amount, "year") + " ago"
    }

    private fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
}

/** "12m 31s", "1h 02m 05s", "0s". */
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "%dh %02dm %02ds".format(h, m, sec)
        m > 0 -> "%dm %02ds".format(m, sec)
        else -> "${sec}s"
    }
}

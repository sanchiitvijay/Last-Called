package com.lastcalled.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lastcalled.app.R
import com.lastcalled.domain.logic.TimeSince
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "3 hours ago". Computed from the stored timestamp and the current time; never persisted. */
@Composable
fun timeAgo(thenMillis: Long, nowMillis: Long): String {
    val t = TimeSince.between(thenMillis, nowMillis)
    val n = t.amount.toInt()
    return when (t.unit) {
        TimeSince.Unit.JUST_NOW -> stringResource(R.string.time_just_now)
        TimeSince.Unit.MINUTES -> pluralStringResource(R.plurals.time_minutes_ago, n, n)
        TimeSince.Unit.HOURS -> pluralStringResource(R.plurals.time_hours_ago, n, n)
        TimeSince.Unit.DAYS -> pluralStringResource(R.plurals.time_days_ago, n, n)
        TimeSince.Unit.YEARS -> pluralStringResource(R.plurals.time_years_ago, n, n)
    }
}

/** Compact gap for the dashboard: "4 days", or "Today". */
@Composable
fun daysCompact(thenMillis: Long, nowMillis: Long): String {
    val days = TimeSince.wholeDays(thenMillis, nowMillis).toInt()
    return if (days == 0) stringResource(R.string.today_compact) else pluralStringResource(R.plurals.days_compact, days, days)
}

/** Dates are formatted in the device's current timezone at display time, so travel and DST are handled. */
object DateFormats {
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    private val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    private fun zoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

    fun longDate(millis: Long): String = date.format(zoned(millis))
    fun mediumDate(millis: Long): String = shortDate.format(zoned(millis))
    fun time(millis: Long): String = time.format(zoned(millis))
}

@StringRes
fun CallSource.label(): Int = when (this) {
    CallSource.PHONE -> R.string.source_phone
    CallSource.WHATSAPP -> R.string.source_whatsapp
    CallSource.UNKNOWN -> R.string.source_unknown
}

@StringRes
fun CallType.label(): Int = when (this) {
    CallType.VOICE -> R.string.type_voice
    CallType.VIDEO -> R.string.type_video
    CallType.UNKNOWN -> R.string.type_unknown
}

@StringRes
fun CallDirection.label(): Int = when (this) {
    CallDirection.OUTGOING -> R.string.direction_outgoing
    CallDirection.INCOMING -> R.string.direction_incoming
    CallDirection.MISSED -> R.string.direction_missed
    CallDirection.REJECTED -> R.string.direction_rejected
    CallDirection.UNKNOWN -> R.string.direction_unknown
}

/** "Phone call", "WhatsApp voice", "WhatsApp video", ... Never claims more than the data says. */
@StringRes
fun callKindLabel(source: CallSource, type: CallType): Int = when (source) {
    CallSource.PHONE -> if (type == CallType.VIDEO) R.string.call_video else R.string.call_phone
    CallSource.WHATSAPP -> when (type) {
        CallType.VOICE -> R.string.call_whatsapp_voice
        CallType.VIDEO -> R.string.call_whatsapp_video
        CallType.UNKNOWN -> R.string.call_whatsapp
    }
    CallSource.UNKNOWN -> if (type == CallType.VIDEO) R.string.call_video else R.string.call_other_app
}

@Composable
fun personTitle(key: PersonKey, contactName: String?, phoneNumber: String?, nameHint: String?): String = when {
    contactName != null -> contactName
    key is PersonKey.Hidden -> stringResource(R.string.hidden_number)
    phoneNumber != null -> phoneNumber
    nameHint != null -> nameHint
    else -> stringResource(R.string.unknown_number)
}

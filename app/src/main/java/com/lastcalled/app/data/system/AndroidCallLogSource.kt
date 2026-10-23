package com.lastcalled.app.data.system

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import com.lastcalled.domain.logic.RawCallLogEntry
import com.lastcalled.domain.sync.CallLogSnapshot
import com.lastcalled.domain.sync.SourceResult
import com.lastcalled.domain.sync.SystemCallLogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads android.provider.CallLog.Calls with READ_CALL_LOG.
 *
 * From Android 16.1 (API 36.1) the platform can store calls from VoIP apps that integrate with
 * Telecom ("unified call history"), but only returns them when the query sets the
 * `include_voip_calls` parameter (CallLog.Calls.INCLUDE_VOIP_CALLS_PARAM_KEY, public in API 37).
 * We ask for them on API 36+; if the provider refuses, we fall back to the plain URI. Whatever rows
 * come back are used as-is: nothing is inferred or added.
 */
class AndroidCallLogSource(
    private val resolver: ContentResolver,
    private val permissions: PermissionChecker,
    private val country: DeviceCountry,
) : SystemCallLogSource {

    override suspend fun readCallLog(): SourceResult<CallLogSnapshot> = withContext(Dispatchers.IO) {
        if (!permissions.hasCallLog()) return@withContext SourceResult.PermissionMissing
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                val voipUri = CallLog.Calls.CONTENT_URI.buildUpon()
                    .appendQueryParameter(INCLUDE_VOIP_CALLS_PARAM, "true")
                    .build()
                val withVoip = runCatching { query(voipUri) }.getOrNull()
                if (withVoip != null) return@withContext SourceResult.Available(CallLogSnapshot(withVoip, voipRowsRequested = true))
            }
            SourceResult.Available(CallLogSnapshot(query(CallLog.Calls.CONTENT_URI), voipRowsRequested = false))
        } catch (e: SecurityException) {
            SourceResult.PermissionMissing
        } catch (e: RuntimeException) {
            // Exception messages may contain provider details; report the type only.
            SourceResult.Failed(e.javaClass.simpleName)
        }
    }

    private fun query(uri: Uri): List<RawCallLogEntry> {
        val cursor = resolver.query(uri, PROJECTION, null, null, "${CallLog.Calls.DATE} DESC")
            ?: throw IllegalStateException("CallLog query returned null")
        return cursor.use { c -> readRows(c) }
    }

    private fun readRows(c: Cursor): List<RawCallLogEntry> {
        val id = c.getColumnIndexOrThrow(CallLog.Calls._ID)
        val number = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val date = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val duration = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        val type = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val features = c.getColumnIndex(CallLog.Calls.FEATURES)
        val component = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
        val presentation = c.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)
        val normalized = c.getColumnIndex(CallLog.Calls.CACHED_NORMALIZED_NUMBER)
        val countryIso = c.getColumnIndex(CallLog.Calls.COUNTRY_ISO)

        val rows = ArrayList<RawCallLogEntry>(c.count)
        while (c.moveToNext()) {
            val rawNumber = c.stringOrNull(number)
            val e164 = c.stringOrNull(normalized)?.takeIf { it.startsWith("+") }
                ?: country.toE164(rawNumber, c.stringOrNull(countryIso))
            rows += RawCallLogEntry(
                systemId = c.getLong(id),
                number = rawNumber,
                dateMillis = c.getLong(date),
                durationSeconds = c.getLong(duration),
                type = c.intOrNull(type),
                features = c.intOrNull(features),
                phoneAccountComponent = c.stringOrNull(component),
                presentation = c.intOrNull(presentation),
                platformE164 = e164,
            )
        }
        return rows
    }

    companion object {
        /** Same value as CallLog.Calls.INCLUDE_VOIP_CALLS_PARAM_KEY; spelled out so it also works on 36.1. */
        const val INCLUDE_VOIP_CALLS_PARAM = "include_voip_calls"

        private val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.FEATURES,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.NUMBER_PRESENTATION,
            CallLog.Calls.CACHED_NORMALIZED_NUMBER,
            CallLog.Calls.COUNTRY_ISO,
        )
    }
}

internal fun Cursor.stringOrNull(index: Int): String? = if (index < 0 || isNull(index)) null else getString(index)
internal fun Cursor.intOrNull(index: Int): Int? = if (index < 0 || isNull(index)) null else getInt(index)

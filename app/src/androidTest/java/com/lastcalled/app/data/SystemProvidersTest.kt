package com.lastcalled.app.data

import android.Manifest
import android.os.Build
import android.provider.CallLog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lastcalled.app.data.system.AndroidCallLogSource
import com.lastcalled.app.data.system.DeviceCountry
import com.lastcalled.app.data.system.PermissionChecker
import com.lastcalled.domain.logic.CallSourceDetector
import com.lastcalled.domain.logic.SystemCallLogContract
import com.lastcalled.domain.sync.SourceResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads the real CallLog provider. A row is inserted through the shell (the app itself has no
 * WRITE_CALL_LOG permission) and must come back correctly mapped.
 *
 * The write test only runs on emulators: it must never touch a real phone's call history.
 */
@RunWith(AndroidJUnit4::class)
class SystemProvidersTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALL_LOG)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val marker = "+15550001234"
    private var insertedId: Long? = null

    private val isEmulator: Boolean
        get() = Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk") ||
            Build.FINGERPRINT.contains("emulator") || Build.FINGERPRINT.startsWith("generic")

    /** executeShellCommand does not go through a shell, so arguments must not rely on quoting. */
    private fun shell(cmd: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).close()
        Thread.sleep(500)
    }

    @After fun cleanUp() {
        insertedId?.let { shell("content delete --uri content://call_log/calls --where _id=$it") }
    }

    @Test fun contractConstantsMatchFramework() {
        assertEquals(CallLog.Calls.INCOMING_TYPE, SystemCallLogContract.INCOMING_TYPE)
        assertEquals(CallLog.Calls.OUTGOING_TYPE, SystemCallLogContract.OUTGOING_TYPE)
        assertEquals(CallLog.Calls.MISSED_TYPE, SystemCallLogContract.MISSED_TYPE)
        assertEquals(CallLog.Calls.VOICEMAIL_TYPE, SystemCallLogContract.VOICEMAIL_TYPE)
        assertEquals(CallLog.Calls.REJECTED_TYPE, SystemCallLogContract.REJECTED_TYPE)
        assertEquals(CallLog.Calls.BLOCKED_TYPE, SystemCallLogContract.BLOCKED_TYPE)
        assertEquals(CallLog.Calls.ANSWERED_EXTERNALLY_TYPE, SystemCallLogContract.ANSWERED_EXTERNALLY_TYPE)
        assertEquals(CallLog.Calls.FEATURES_VIDEO, SystemCallLogContract.FEATURES_VIDEO)
        assertEquals(CallLog.Calls.PRESENTATION_ALLOWED, SystemCallLogContract.PRESENTATION_ALLOWED)
        if (Build.VERSION.SDK_INT >= 37) {
            assertEquals(CallLog.Calls.INCLUDE_VOIP_CALLS_PARAM_KEY, AndroidCallLogSource.INCLUDE_VOIP_CALLS_PARAM)
        }
    }

    @Test fun readsAndMapsCallLogRows() = runTest {
        assumeTrue("Writes to the call log; emulator only", isEmulator)
        val date = System.currentTimeMillis() - 60_000
        shell(
            "content insert --uri content://call_log/calls --bind number:s:$marker --bind type:i:2 " +
                "--bind date:l:$date --bind duration:l:42 --bind features:i:1 --bind presentation:i:1 " +
                "--bind subscription_component_name:s:com.whatsapp/com.whatsapp.Svc",
        )
        val source = AndroidCallLogSource(context.contentResolver, PermissionChecker(context), DeviceCountry(context))
        val result = source.readCallLog()
        assertTrue(result is SourceResult.Available)
        val row = (result as SourceResult.Available).data.entries.firstOrNull { it.number == marker }
        insertedId = row?.systemId
        assertNotNull("inserted row not returned", row)
        assertEquals(date, row!!.dateMillis)
        assertEquals(42L, row.durationSeconds)
        assertEquals(SystemCallLogContract.OUTGOING_TYPE, row.type)
        assertEquals(SystemCallLogContract.FEATURES_VIDEO, row.features)
        assertEquals("com.whatsapp", CallSourceDetector.packageOf(row.phoneAccountComponent))
    }
}

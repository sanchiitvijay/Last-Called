package com.lastcalled.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastcalled.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates every exported schema version and runs all registered migrations up to the current
 * version, checking the result against the schema Room expects. When a version is added, add a
 * Migration to AppDatabase.MIGRATIONS and this test covers it automatically.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migrate1To2() {
        helper.createDatabase("m12", 1).close()
        helper.runMigrationsAndValidate("m12", 2, true, AppDatabase.MIGRATION_1_2).close()
    }

    @Test fun migrateAllVersionsKeepingData() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                """INSERT INTO call_records (personKey, contactKey, phoneNumber, normalizedNumber, displayNameHint,
                   timestamp, durationSeconds, direction, source, callType, origin, systemCallLogId, sourcePackage, createdAt, updatedAt)
                   VALUES ('n:+1', NULL, '+1', '+1', NULL, 1000, 5, 'OUTGOING', 'PHONE', 'VOICE', 'SYSTEM_CALL_LOG', 1, NULL, 0, 0)""",
            )
        }
        helper.runMigrationsAndValidate(dbName, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM call_records").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            // v2: the watch list table exists and starts empty.
            db.query("SELECT COUNT(*) FROM tracked_people").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }
}

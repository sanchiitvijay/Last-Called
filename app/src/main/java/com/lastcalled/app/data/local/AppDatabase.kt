package com.lastcalled.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version history:
 *  1 – initial schema (contacts, contact_phone_numbers, call_records, app_settings).
 *  2 – tracked_people: the user's "keep in touch" list shown on the dashboard.
 *
 * Schema changes must add a [Migration] to [MIGRATIONS]; destructive fallback is deliberately not
 * enabled, because imported history can be older than what the system call log still holds.
 */
@Database(
    entities = [
        ContactEntity::class,
        ContactPhoneNumberEntity::class,
        CallRecordEntity::class,
        AppSettingsEntity::class,
        TrackedPersonEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
    abstract fun settingsDao(): SettingsDao
    abstract fun trackedDao(): TrackedDao

    companion object {
        const val NAME = "last_called.db"
        const val VERSION = 2

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracked_people` (`personKey` TEXT NOT NULL, `displayName` TEXT, " +
                        "`phoneNumber` TEXT, `normalizedNumber` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`personKey`))",
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        /** The database lives in app-private storage (Context.getDatabasePath) and is never exported. */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}

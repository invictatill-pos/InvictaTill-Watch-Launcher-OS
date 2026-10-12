package com.healthsync.phone.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schemas before version 4 were never exported. Preserve their original tables without
 * guessing how old health values were captured, then start verified history in schema 6.
 * Room manages its identity table after migration and validates the new active schema.
 */
internal object LegacyDatabasePreservation {
    private val applicationTables = listOf(
        "heart_rate", "steps", "step_buckets", "spo2", "sleep", "notification_history", "workouts"
    )

    // Keep in step with Room's generated schema. Test-Health-Migrations.py compares
    // columns, defaults, primary keys and indices against FitnessDatabase_Impl.
    private val schema6Statements = listOf(
        "CREATE TABLE IF NOT EXISTS `heart_rate` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bpm` INTEGER NOT NULL, `accuracy` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `steps` (`dayKey` TEXT NOT NULL, `steps` INTEGER NOT NULL, `calories` REAL NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`dayKey`))",
        "CREATE TABLE IF NOT EXISTS `step_buckets` (`timestamp` INTEGER NOT NULL, `steps` INTEGER NOT NULL, `calories` REAL NOT NULL, `dayKey` TEXT NOT NULL, PRIMARY KEY(`timestamp`))",
        "CREATE TABLE IF NOT EXISTS `spo2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `percentage` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `source` TEXT NOT NULL DEFAULT 'LEGACY_UNVERIFIED')",
        "CREATE TABLE IF NOT EXISTS `sleep` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `deepMinutes` INTEGER NOT NULL, `lightMinutes` INTEGER NOT NULL, `remMinutes` INTEGER NOT NULL, `awakeMinutes` INTEGER NOT NULL, `source` TEXT NOT NULL DEFAULT 'LEGACY_UNVERIFIED', `estimatedMinutes` INTEGER NOT NULL DEFAULT 0, `stagesAvailable` INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS `notification_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityType` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, `calories` REAL NOT NULL, `avgHeartRate` INTEGER NOT NULL, `steps` INTEGER NOT NULL, `swingsCount` INTEGER NOT NULL, `routeJson` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL DEFAULT 0, `distanceSource` TEXT NOT NULL DEFAULT 'UNKNOWN', `caloriesEstimated` INTEGER NOT NULL DEFAULT 1)"
    )

    fun migration(fromVersion: Int): Migration {
        require(fromVersion in 1..3)
        return object : Migration(fromVersion, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in applicationTables) {
                    if (!tableExists(db, table)) continue
                    val archive = "legacy_v${fromVersion}_$table"
                    check(!tableExists(db, archive)) { "Legacy archive already exists: $archive" }
                    // RENAME preserves every column/value, primary key, index and trigger.
                    // These tables stay local and are never queried as captured health history.
                    db.execSQL("ALTER TABLE `$table` RENAME TO `$archive`")
                }
                schema6Statements.forEach(db::execSQL)
            }
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
            it.moveToFirst()
        }
}

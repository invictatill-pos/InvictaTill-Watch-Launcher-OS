package com.healthsync.watch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class WatchDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "WatchDatabase"
        private const val DATABASE_NAME = "healthsync_watch.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_WORKOUTS = "workout_sessions"
        const val TABLE_DAILY_STEPS = "daily_steps"

        @Volatile
        private var instance: WatchDatabaseHelper? = null

        fun getInstance(context: Context): WatchDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: WatchDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createWorkoutsTable = """
            CREATE TABLE $TABLE_WORKOUTS (
                id TEXT PRIMARY KEY,
                activity_type TEXT,
                start_time INTEGER,
                end_time INTEGER,
                duration_seconds INTEGER,
                distance_meters REAL,
                calories_burned REAL,
                step_count INTEGER,
                swings_count INTEGER,
                avg_heart_rate INTEGER,
                route_json TEXT DEFAULT '[]',
                is_synced INTEGER DEFAULT 0
            );
        """.trimIndent()

        val createDailyStepsTable = """
            CREATE TABLE $TABLE_DAILY_STEPS (
                timestamp INTEGER PRIMARY KEY,
                step_delta INTEGER,
                calories_delta REAL,
                is_synced INTEGER DEFAULT 0
            );
        """.trimIndent()

        db.execSQL(createWorkoutsTable)
        db.execSQL(createDailyStepsTable)
        Log.d(TAG, "Watch database tables created successfully.")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add route_json column if upgrading from v1
            try {
                db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN route_json TEXT DEFAULT '[]'")
                Log.d(TAG, "Migrated Watch DB to v2: added route_json column")
            } catch (e: Exception) {
                Log.w(TAG, "Migration v1->v2 failed (column may already exist): ${e.message}")
            }
        }
    }

    // ── Workout Operations ────────────────────────────────────────────────────

    fun insertWorkoutSession(session: WorkoutSessionPayload, isSynced: Boolean = false): Boolean {
        return try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("id", session.session_id)
                put("activity_type", session.activity_type)
                put("start_time", session.start_time)
                put("end_time", session.end_time)
                put("duration_seconds", session.duration_sec)
                put("distance_meters", session.distance_m)
                put("calories_burned", session.calories)
                put("step_count", session.step_count)
                put("swings_count", session.swings)
                put("avg_heart_rate", session.avg_hr)
                put("route_json", session.route_json)
                put("is_synced", if (isSynced) 1 else 0)
            }
            val res = db.insertWithOnConflict(TABLE_WORKOUTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            Log.d(TAG, "Inserted workout session ${session.session_id}, res=$res")
            res != -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting workout: ${e.message}")
            false
        }
    }

    fun getUnsyncedWorkouts(limit: Int = 100): List<WorkoutSessionPayload> {
        val list = mutableListOf<WorkoutSessionPayload>()
        try {
            val db = readableDatabase
            // Limit in SQLite, before allocating potentially large route histories.
            val cursor = db.rawQuery("SELECT * FROM $TABLE_WORKOUTS WHERE is_synced = 0 ORDER BY start_time ASC LIMIT ?",
                arrayOf(limit.coerceIn(1, 100).toString()))
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        WorkoutSessionPayload(
                            session_id = it.getString(it.getColumnIndexOrThrow("id")),
                            activity_type = it.getString(it.getColumnIndexOrThrow("activity_type")),
                            start_time = it.getLong(it.getColumnIndexOrThrow("start_time")),
                            end_time = it.getLong(it.getColumnIndexOrThrow("end_time")),
                            duration_sec = it.getInt(it.getColumnIndexOrThrow("duration_seconds")),
                            distance_m = it.getFloat(it.getColumnIndexOrThrow("distance_meters")),
                            calories = it.getDouble(it.getColumnIndexOrThrow("calories_burned")),
                            step_count = it.getInt(it.getColumnIndexOrThrow("step_count")),
                            swings = it.getInt(it.getColumnIndexOrThrow("swings_count")),
                            avg_hr = it.getInt(it.getColumnIndexOrThrow("avg_heart_rate")),
                            route_json = it.getString(it.getColumnIndexOrThrow("route_json")) ?: "[]"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unsynced workouts: ${e.message}")
        }
        return list
    }

    fun markWorkoutsSynced(sessionIds: List<String>) {
        if (sessionIds.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (id in sessionIds) {
                    val cv = ContentValues().apply { put("is_synced", 1) }
                    db.update(TABLE_WORKOUTS, cv, "id = ?", arrayOf(id))
                }
                db.setTransactionSuccessful()
                Log.d(TAG, "Marked ${sessionIds.size} workouts as synced in Watch DB.")
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking workouts synced: ${e.message}")
        }
    }

    // ── Daily Step Bucket Operations ──────────────────────────────────────────

    @Synchronized
    fun insertOrUpdateStepBucket(timestampBucket: Long, stepDelta: Int, caloriesDelta: Double): Boolean {
        if (stepDelta <= 0 || !caloriesDelta.isFinite() || caloriesDelta < 0.0 || timestampBucket < 0L) return false
        return try {
            val db = writableDatabase
            db.beginTransaction()
            try {
            val cursor = db.rawQuery("SELECT step_delta, calories_delta FROM $TABLE_DAILY_STEPS WHERE timestamp = ?", arrayOf(timestampBucket.toString()))
            var existingSteps = 0
            var existingCal = 0.0
            var exists = false
            cursor.use {
                if (it.moveToFirst()) {
                    exists = true
                    existingSteps = it.getInt(0)
                    existingCal = it.getDouble(1)
                }
            }

            val cv = ContentValues().apply {
                put("timestamp", timestampBucket)
                put("step_delta", if (exists) existingSteps + stepDelta else stepDelta)
                put("calories_delta", if (exists) existingCal + caloriesDelta else caloriesDelta)
                put("is_synced", 0)
            }
            val res = db.insertWithOnConflict(TABLE_DAILY_STEPS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            if (res != -1L) db.setTransactionSuccessful()
            res != -1L
            } finally { db.endTransaction() }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating step bucket: ${e.message}")
            false
        }
    }

    fun getUnsyncedStepBuckets(limit: Int = 2000): List<DailyStepBucketPayload> {
        val list = mutableListOf<DailyStepBucketPayload>()
        try {
            val db = readableDatabase
            // An ACK must never cover increments that arrived after the snapshot.
            // Closed buckets are immutable during ordinary chronological tracking.
            val openBucket = System.currentTimeMillis() / 300_000L * 300_000L
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DAILY_STEPS WHERE is_synced = 0 AND timestamp < ? ORDER BY timestamp ASC LIMIT ?",
                arrayOf(openBucket.toString(), limit.coerceIn(1, 2000).toString()))
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        DailyStepBucketPayload(
                            timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                            steps = it.getInt(it.getColumnIndexOrThrow("step_delta")),
                            calories = it.getDouble(it.getColumnIndexOrThrow("calories_delta"))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unsynced step buckets: ${e.message}")
        }
        return list
    }

    fun markStepBucketsSynced(timestamps: List<Long>) {
        if (timestamps.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (ts in timestamps) {
                    val cv = ContentValues().apply { put("is_synced", 1) }
                    db.update(TABLE_DAILY_STEPS, cv, "timestamp = ?", arrayOf(ts.toString()))
                }
                db.setTransactionSuccessful()
                Log.d(TAG, "Marked ${timestamps.size} step buckets as synced in Watch DB.")
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking step buckets synced: ${e.message}")
        }
    }

    fun getLatestWorkouts(limit: Int = 10): List<WorkoutSessionPayload> {
        val list = mutableListOf<WorkoutSessionPayload>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_WORKOUTS ORDER BY start_time DESC LIMIT ?", arrayOf(limit.coerceIn(1, 1000).toString()))
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        WorkoutSessionPayload(
                            session_id = it.getString(it.getColumnIndexOrThrow("id")),
                            activity_type = it.getString(it.getColumnIndexOrThrow("activity_type")),
                            start_time = it.getLong(it.getColumnIndexOrThrow("start_time")),
                            end_time = it.getLong(it.getColumnIndexOrThrow("end_time")),
                            duration_sec = it.getInt(it.getColumnIndexOrThrow("duration_seconds")),
                            distance_m = it.getFloat(it.getColumnIndexOrThrow("distance_meters")),
                            calories = it.getDouble(it.getColumnIndexOrThrow("calories_burned")),
                            step_count = it.getInt(it.getColumnIndexOrThrow("step_count")),
                            swings = it.getInt(it.getColumnIndexOrThrow("swings_count")),
                            avg_hr = it.getInt(it.getColumnIndexOrThrow("avg_heart_rate")),
                            route_json = try { it.getString(it.getColumnIndexOrThrow("route_json")) ?: "[]" } catch (_: Exception) { "[]" }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest workouts: ${e.message}")
        }
        return list
    }
}

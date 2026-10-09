package com.healthsync.phone.data

import androidx.room.*
import com.healthsync.phone.data.model.*
import kotlinx.coroutines.flow.Flow

// ── Heart Rate DAO ────────────────────────────────────────────────────────────

@Dao
interface HeartRateDao {
    @Insert
    suspend fun insert(entity: HeartRateEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM heart_rate WHERE timestamp = :timestamp AND bpm = :bpm AND accuracy BETWEEN 1 AND 3)")
    suspend fun hasReading(timestamp: Long, bpm: Int): Boolean

    @Query("SELECT * FROM heart_rate WHERE timestamp >= :since AND accuracy BETWEEN 1 AND 3 AND bpm BETWEEN 25 AND 240 ORDER BY timestamp DESC")
    fun getLast24h(since: Long): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rate WHERE accuracy BETWEEN 1 AND 3 AND bpm BETWEEN 25 AND 240 ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<HeartRateEntity?>

    @Query("SELECT AVG(bpm) FROM heart_rate WHERE timestamp >= :since AND accuracy BETWEEN 1 AND 3 AND bpm BETWEEN 25 AND 240")
    suspend fun getAvgBpm(since: Long): Float?

    @Query("DELETE FROM heart_rate WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ── Steps DAO ─────────────────────────────────────────────────────────────────

@Dao
interface StepsDao {
    // Upsert: replaces the row if dayKey already exists, so only ONE row per day.
    // The watch sends cumulative totals, not deltas — so we must overwrite, not sum.
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StepsEntity)

    // Single row for today — no SUM needed, steps field IS the daily total
    @Query("SELECT * FROM steps WHERE dayKey = :dayKey LIMIT 1")
    fun getLatestForDay(dayKey: String): Flow<StepsEntity?>

    @Query("SELECT * FROM steps WHERE dayKey = :dayKey LIMIT 1")
    suspend fun getLatestForDayOnce(dayKey: String): StepsEntity?

    // Per-day totals for 7-day bar chart — one row per day already
    @Query("SELECT * FROM steps WHERE dayKey >= :fromDay ORDER BY dayKey ASC")
    fun getLast7Days(fromDay: String): Flow<List<StepsEntity>>

    @Query("DELETE FROM steps WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface StepBucketDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StepBucketEntity)

    @Query("SELECT * FROM step_buckets WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getSince(since: Long): Flow<List<StepBucketEntity>>

    @Query("SELECT * FROM step_buckets WHERE dayKey = :dayKey")
    suspend fun getForDay(dayKey: String): List<StepBucketEntity>

    @Query("SELECT * FROM step_buckets WHERE timestamp = :timestamp LIMIT 1")
    suspend fun getByTimestamp(timestamp: Long): StepBucketEntity?

    @Query("DELETE FROM step_buckets WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ── SpO2 DAO ──────────────────────────────────────────────────────────────────

@Dao
interface SpO2Dao {
    @Insert
    suspend fun insert(entity: SpO2Entity)

    @Query("SELECT * FROM spo2 WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getLast24h(since: Long): Flow<List<SpO2Entity>>

    @Query("SELECT * FROM spo2 ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<SpO2Entity?>

    @Query("DELETE FROM spo2 WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ── Sleep DAO ─────────────────────────────────────────────────────────────────

@Dao
interface SleepDao {
    @Insert
    suspend fun insert(entity: SleepEntity)

    @Query("SELECT * FROM sleep WHERE startTime = :startTime LIMIT 1")
    suspend fun getByStartTime(startTime: Long): SleepEntity?

    @Query("SELECT * FROM sleep ORDER BY startTime DESC LIMIT 7")
    fun getLast7Nights(): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep ORDER BY startTime DESC LIMIT 1")
    fun getLatestNight(): Flow<SleepEntity?>

    @Query("DELETE FROM sleep WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ── Notification History DAO ──────────────────────────────────────────────────

@Dao
interface NotificationHistoryDao {
    @Insert
    suspend fun insert(entity: NotificationHistoryEntity)

    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC LIMIT 100")
    fun getRecent(): Flow<List<NotificationHistoryEntity>>

    @Query("DELETE FROM notification_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ── Workout DAO ───────────────────────────────────────────────────────────────

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(entity: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE startTime = :startTime LIMIT 1")
    suspend fun getByStartTime(startTime: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY startTime DESC LIMIT 500")
    fun getRecentWorkouts(): Flow<List<WorkoutEntity>>
}


// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [
        HeartRateEntity::class,
        StepsEntity::class,
        StepBucketEntity::class,
        SpO2Entity::class,
        SleepEntity::class,
        NotificationHistoryEntity::class,
        WorkoutEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao
    abstract fun stepsDao(): StepsDao
    abstract fun stepBucketDao(): StepBucketDao
    abstract fun spO2Dao(): SpO2Dao
    abstract fun sleepDao(): SleepDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
    abstract fun workoutDao(): WorkoutDao
}

package com.healthsync.phone.data

import com.healthsync.phone.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FitnessRepository @Inject constructor(
    private val db: FitnessDatabase
) {
    private fun dayKey(timestamp: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    // Refresh query boundaries while the dashboard remains open across midnight.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun <T> rollingQuery(query: (Long) -> Flow<T>): Flow<T> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }.flatMapLatest(query)

    // ── Heart Rate ────────────────────────────────────────────────────────────

    suspend fun saveHeartRate(payload: HeartRatePayload?, timestamp: Long = System.currentTimeMillis()) {
        if (payload == null || !SyncValidation.validHeartRate(payload.bpm, payload.accuracy, timestamp)) return
        // A reconnect replays the last sample with its original timestamp. Keep
        // that history available without counting it as a second measurement.
        db.withTransaction {
            if (db.heartRateDao().hasReading(timestamp, payload.bpm)) return@withTransaction
            db.heartRateDao().insert(
                HeartRateEntity(
                    bpm = payload.bpm,
                    accuracy = payload.accuracy,
                    timestamp = timestamp
                )
            )
        }
    }

    fun getLatestHeartRate(): Flow<HeartRateEntity?> = db.heartRateDao().getLatest()

    fun getHeartRateLast24h(): Flow<List<HeartRateEntity>> =
        rollingQuery { db.heartRateDao().getLast24h(it - 24 * 60 * 60 * 1000L) }

    // ── Steps ─────────────────────────────────────────────────────────────────

    suspend fun saveSteps(payload: StepsPayload?, timestamp: Long = System.currentTimeMillis()) = db.withTransaction {
        if (payload == null) return@withTransaction
        if (!SyncValidation.validTimestamp(timestamp) || !SyncValidation.validSteps(payload.steps, payload.calories)) return@withTransaction
        val dayKey = dayKey(timestamp)
        val previous = db.stepsDao().getLatestForDayOnce(dayKey)
        db.stepsDao().insert(
            StepsEntity(
                steps    = maxOf(payload.steps, previous?.steps ?: 0),
                calories = maxOf(payload.calories, previous?.calories ?: 0.0),
                timestamp = maxOf(timestamp, previous?.timestamp ?: 0),
                dayKey   = dayKey
            )
        )
    }

    suspend fun saveStepBucket(incoming: DailyStepBucketPayload?) = db.withTransaction {
        val payload = requireNotNull(incoming) { "Missing step bucket" }
        val timestamp = payload.timestamp
        require(SyncValidation.validTimestamp(timestamp) && SyncValidation.validSteps(payload.steps, payload.calories))
        val dayKey = dayKey(timestamp)
        db.stepBucketDao().insert(
            StepBucketEntity(
                timestamp = timestamp,
                steps = payload.steps,
                calories = payload.calories,
                dayKey = dayKey
            )
        )
        val buckets = db.stepBucketDao().getForDay(dayKey)
        val previous = db.stepsDao().getLatestForDayOnce(dayKey)
        db.stepsDao().insert(
            StepsEntity(
                dayKey = dayKey,
                steps = maxOf(previous?.steps ?: 0, buckets.sumOf { it.steps.toLong() }.coerceAtMost(1_000_000).toInt()),
                calories = maxOf(previous?.calories ?: 0.0, buckets.sumOf { it.calories }),
                timestamp = maxOf(previous?.timestamp ?: 0, buckets.maxOfOrNull { it.timestamp } ?: timestamp)
            )
        )
    }

    /** Persist a chunk atomically and recompute each affected day only once. */
    suspend fun saveStepBuckets(payloads: List<DailyStepBucketPayload?>?): List<Long> = db.withTransaction {
        val valid = payloads.orEmpty().filterNotNull().filter { SyncValidation.validTimestamp(it.timestamp) && SyncValidation.validSteps(it.steps, it.calories) }
        for (payload in valid) {
            db.stepBucketDao().insert(StepBucketEntity(payload.timestamp, payload.steps, payload.calories, dayKey(payload.timestamp)))
        }
        for (day in valid.map { dayKey(it.timestamp) }.distinct()) {
            val buckets = db.stepBucketDao().getForDay(day)
            val previous = db.stepsDao().getLatestForDayOnce(day)
            db.stepsDao().insert(StepsEntity(
                dayKey = day,
                steps = maxOf(previous?.steps ?: 0, buckets.sumOf { it.steps.toLong() }.coerceAtMost(1_000_000).toInt()),
                calories = maxOf(previous?.calories ?: 0.0, buckets.sumOf { it.calories }),
                timestamp = maxOf(previous?.timestamp ?: 0, buckets.maxOfOrNull { it.timestamp } ?: 0)
            ))
        }
        valid.map { it.timestamp }
    }

    fun getTodaySteps(): Flow<StepsEntity?> {
        return rollingQuery { db.stepsDao().getLatestForDay(dayKey(it)) }
    }

    fun getStepsLast7Days(): Flow<List<StepsEntity>> {
        return rollingQuery { now ->
            val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -6) }
            db.stepsDao().getLast7Days(dayKey(cal.timeInMillis))
        }
    }

    fun getStepsLast24h(): Flow<List<StepBucketEntity>> =
        rollingQuery { db.stepBucketDao().getSince(it - 24 * 60 * 60 * 1000L) }

    // ── SpO2 ──────────────────────────────────────────────────────────────────

    suspend fun saveSpO2(payload: SpO2Payload?, timestamp: Long = System.currentTimeMillis()) {
        if (payload == null || !SyncValidation.validSpO2(payload.percentage, timestamp)) return
        db.spO2Dao().insert(
            SpO2Entity(
                percentage = payload.percentage,
                timestamp = timestamp
            )
        )
    }

    fun getLatestSpO2(): Flow<SpO2Entity?> = db.spO2Dao().getLatest()

    fun getSpO2Last24h(): Flow<List<SpO2Entity>> =
        rollingQuery { db.spO2Dao().getLast24h(it - 24 * 60 * 60 * 1000L) }

    // ── Sleep ─────────────────────────────────────────────────────────────────

    suspend fun saveSleep(incoming: SleepPayload?) = db.withTransaction {
        val payload = requireNotNull(incoming) { "Missing sleep record" }
        require(SyncValidation.validSleep(payload.startTime, payload.endTime, payload.deepMinutes,
            payload.lightMinutes, payload.remMinutes, payload.awakeMinutes))
        if (db.sleepDao().getByStartTime(payload.startTime) != null) return@withTransaction
        db.sleepDao().insert(
            SleepEntity(
                startTime = payload.startTime,
                endTime = payload.endTime,
                deepMinutes = payload.deepMinutes,
                lightMinutes = payload.lightMinutes,
                remMinutes = payload.remMinutes,
                awakeMinutes = payload.awakeMinutes
            )
        )
    }

    fun getLatestSleep(): Flow<SleepEntity?> = db.sleepDao().getLatestNight()
    fun getLast7NightsSleep(): Flow<List<SleepEntity>> = db.sleepDao().getLast7Nights()

    // ── Notifications ─────────────────────────────────────────────────────────

    suspend fun saveNotification(payload: NotificationPayload) {
        db.notificationHistoryDao().insert(
            NotificationHistoryEntity(
                packageName = payload.packageName,
                appLabel = payload.appLabel,
                title = payload.title,
                text = payload.text,
                timestamp = payload.time
            )
        )
    }

    fun getRecentNotifications(): Flow<List<NotificationHistoryEntity>> =
        db.notificationHistoryDao().getRecent()

    // ── Workouts ──────────────────────────────────────────────────────────────

    private val gson = Gson()

    suspend fun saveWorkout(incoming: WorkoutPayload?) = db.withTransaction {
        val payload = requireNotNull(incoming) { "Missing workout" }
        require(SyncValidation.validWorkoutRecord(payload.activityType, payload.startTime, payload.endTime,
            payload.durationSeconds, payload.distanceMeters, payload.calories, payload.steps, payload.avgHeartRate, payload.swings))
        val routeJson = gson.toJson(SyncValidation.sanitizeRoute(payload.route, payload.startTime, payload.endTime))
        val existing = db.workoutDao().getByStartTime(payload.startTime)
        if (existing != null) return@withTransaction
        db.workoutDao().insert(
            WorkoutEntity(
                activityType = payload.activityType,
                startTime = payload.startTime,
                endTime = payload.endTime,
                distanceMeters = payload.distanceMeters,
                calories = payload.calories,
                avgHeartRate = payload.avgHeartRate,
                steps = payload.steps,
                swingsCount = payload.swings,
                routeJson = routeJson,
                durationSeconds = payload.durationSeconds
            )
        )
    }

    // Used by batch sync — saves WorkoutSessionPayload directly preserving route_json
    suspend fun saveWorkoutSession(incoming: WorkoutSessionPayload?) = db.withTransaction {
        val session = requireNotNull(incoming) { "Missing workout session" }
        require(!session.session_id.isNullOrBlank() && session.session_id.length <= 200 &&
            SyncValidation.validWorkoutRecord(session.activity_type, session.start_time, session.end_time,
                session.duration_sec, session.distance_m, session.calories, session.step_count, session.avg_hr, session.swings))
        val routeJson = requireNotNull(SyncValidation.sanitizeRouteJson(session.route_json ?: "[]", session.start_time, session.end_time)) {
            "Malformed workout route"
        }
        val existing = db.workoutDao().getByStartTime(session.start_time)
        if (existing != null) return@withTransaction // already saved, skip duplicate
        db.workoutDao().insert(
            WorkoutEntity(
                activityType = session.activity_type
                    .replace("_", " ")
                    .lowercase()
                    .replaceFirstChar { it.titlecase() },
                startTime = session.start_time,
                endTime = session.end_time,
                distanceMeters = session.distance_m,
                calories = session.calories,
                avgHeartRate = session.avg_hr,
                steps = session.step_count,
                swingsCount = session.swings,
                routeJson = routeJson,
                durationSeconds = session.duration_sec
            )
        )
    }

    fun getRecentWorkouts(): Flow<List<WorkoutEntity>> = db.workoutDao().getRecentWorkouts()

    // ── Cleanup ───────────────────────────────────────────────────────────────

    suspend fun pruneOldData() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        db.heartRateDao().deleteOlderThan(thirtyDaysAgo)
        db.stepsDao().deleteOlderThan(thirtyDaysAgo)
        db.stepBucketDao().deleteOlderThan(thirtyDaysAgo)
        db.spO2Dao().deleteOlderThan(thirtyDaysAgo)
        db.sleepDao().deleteOlderThan(thirtyDaysAgo)
        db.notificationHistoryDao().deleteOlderThan(thirtyDaysAgo)
    }
}

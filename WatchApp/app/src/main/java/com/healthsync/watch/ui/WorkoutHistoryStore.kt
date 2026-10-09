package com.healthsync.watch.ui

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthsync.watch.data.WatchDatabaseHelper
import com.healthsync.watch.data.WorkoutPayload
import com.healthsync.watch.data.WorkoutSessionPayload

/** Reads database sessions and legacy sessions consistently. Call from a worker thread. */
internal object WorkoutHistoryStore {
    fun read(context: Context, limit: Int = 500): List<WorkoutSessionPayload> {
        val sessions = linkedMapOf<Long, WorkoutSessionPayload>()
        try {
            WatchDatabaseHelper.getInstance(context).getLatestWorkouts(limit).forEach {
                sessions[it.start_time] = it
            }
        } catch (error: Exception) {
            Log.w("WorkoutHistory", "Could not read workout database", error)
        }
        try {
            val json = context.getSharedPreferences("watch_workouts", Context.MODE_PRIVATE)
                .getString("history", "[]") ?: "[]"
            val type = object : TypeToken<List<WorkoutPayload>>() {}.type
            val legacy: List<WorkoutPayload?> = Gson().fromJson(json, type) ?: emptyList()
            legacy.filterNotNull().filter { it.startTime > 0 }.forEach { item ->
                if (!sessions.containsKey(item.startTime)) {
                    sessions[item.startTime] = WorkoutSessionPayload(
                        session_id = "W_${item.startTime}", activity_type = item.activityType,
                        start_time = item.startTime, end_time = item.endTime,
                        duration_sec = (item.durationSeconds.takeIf { it > 0 } ?: ((item.endTime - item.startTime).coerceAtLeast(0) / 1000)
                            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).coerceAtLeast(0),
                        distance_m = item.distanceMeters.coerceAtLeast(0f),
                        calories = item.calories.coerceAtLeast(0.0), step_count = item.steps.coerceAtLeast(0),
                        swings = item.swings.coerceAtLeast(0), avg_hr = item.avgHeartRate.coerceAtLeast(0)
                    )
                }
            }
        } catch (error: Exception) {
            Log.w("WorkoutHistory", "Could not read legacy history", error)
        }
        return sessions.values.sortedByDescending { it.start_time }.take(limit)
    }
}

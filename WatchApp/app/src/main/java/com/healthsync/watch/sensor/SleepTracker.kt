package com.healthsync.watch.sensor

import android.content.Context
import com.healthsync.watch.data.SleepPayload
import java.util.Calendar

/** Stores completed motion rest estimates; never invents sleep stages or first-run records. */
class SleepTracker(context: Context) {
    private val prefs = context.getSharedPreferences("healthsync_sleep_tracker", Context.MODE_PRIVATE)
    private val engine = ActigraphyRestEngine()
    var onSleepFinalized: ((SleepPayload) -> Unit)? = null

    init {
        // Earlier versions generated a baseline night and unsupported deep/REM classifications.
        // They have no capture provenance and must not reappear after upgrading.
        if (prefs.getInt("capture_schema", 0) != 2) prefs.edit().clear().putInt("capture_schema", 2).commit()
    }

    @Suppress("UNUSED_PARAMETER")
    fun processAccel(x: Float, y: Float, z: Float, nowElapsed: Long, nowWall: Long, currentBpm: Int = -1) {
        val hour = Calendar.getInstance().apply { timeInMillis = nowWall }.get(Calendar.HOUR_OF_DAY)
        val observed = engine.sample(x, y, z, nowElapsed, nowWall, hour >= 21 || hour < 11) ?: return
        val payload = SleepPayload(observed.startTime, observed.endTime, 0, 0, 0, 0,
            source = "WATCH_ACTIGRAPHY", estimatedMinutes = observed.estimatedMinutes, stagesAvailable = false)
        prefs.edit().putLong("rest_start_time", payload.startTime).putLong("rest_end_time", payload.endTime)
            .putInt("estimated_rest_minutes", payload.estimatedMinutes).putInt("capture_schema", 2).commit()
        onSleepFinalized?.invoke(payload)
    }

    fun getLatestPayload(): SleepPayload? {
        val start = prefs.getLong("rest_start_time", 0L)
        val end = prefs.getLong("rest_end_time", 0L)
        val minutes = prefs.getInt("estimated_rest_minutes", 0)
        if (prefs.getInt("capture_schema", 0) != 2 || start <= 0L || end <= start ||
            end > System.currentTimeMillis() || minutes !in 30..1080 || minutes.toLong() * 60_000L > end - start) return null
        return SleepPayload(start, end, 0, 0, 0, 0, "WATCH_ACTIGRAPHY", minutes, false)
    }

    fun getSleepScore(): Int? = null
    fun isCurrentlyAsleep(): Boolean = engine.isResting
}

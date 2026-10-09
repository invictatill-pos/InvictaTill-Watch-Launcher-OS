package com.healthsync.watch.service

/**
 * One automatic due time per sensor, using elapsed realtime milliseconds.
 *
 * Reapplying settings leaves an unchanged schedule alone. Every acquisition,
 * including a manual one, consumes a slot. A slow acquisition skips missed
 * slots instead of creating a queue of measurements with no sensor downtime.
 * The service owns serialization and the lifetime of each acquisition.
 */
internal class MeasurementSchedule {
    companion object {
        const val MIN_INTERVAL_MS = 30_000L
        const val MAX_INTERVAL_MS = 3_600_000L
    }

    private data class Schedule(val intervalMs: Long, var nextDueMs: Long)
    private val schedules = mutableMapOf<MeasurementKind, Schedule>()

    fun configure(kind: MeasurementKind, intervalMs: Long, nowMs: Long) {
        if (intervalMs <= 0L) {
            schedules.remove(kind)
            return
        }
        val normalized = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        if (schedules[kind]?.intervalMs == normalized) return
        schedules[kind] = Schedule(normalized, deadline(nowMs, normalized))
    }

    fun isEnabled(kind: MeasurementKind): Boolean = schedules.containsKey(kind)

    fun intervalMs(kind: MeasurementKind): Long? = schedules[kind]?.intervalMs

    fun dueAtMs(kind: MeasurementKind): Long? = schedules[kind]?.nextDueMs

    /** The caller verifies the boot identity before restoring an elapsed-realtime deadline. */
    fun restoreDue(kind: MeasurementKind, intervalMs: Long, dueMs: Long): Boolean {
        if (intervalMs <= 0L || dueMs <= 0L) return false
        val schedule = schedules[kind] ?: return false
        if (schedule.intervalMs != intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)) return false
        schedule.nextDueMs = dueMs
        return true
    }

    /** Null means automatic measurement is disabled; zero means the slot is due. */
    fun dueDelayMs(kind: MeasurementKind, nowMs: Long): Long? {
        val schedule = schedules[kind] ?: return null
        val now = nowMs.coerceAtLeast(0L)
        return if (schedule.nextDueMs <= now) 0L else schedule.nextDueMs - now
    }

    fun markStarted(kind: MeasurementKind, nowMs: Long) {
        val schedule = schedules[kind] ?: return
        schedule.nextDueMs = deadline(nowMs, schedule.intervalMs)
    }

    fun markFinished(kind: MeasurementKind, nowMs: Long) {
        val schedule = schedules[kind] ?: return
        if (schedule.nextDueMs <= nowMs.coerceAtLeast(0L)) {
            schedule.nextDueMs = deadline(nowMs, schedule.intervalMs)
        }
    }

    private fun deadline(nowMs: Long, intervalMs: Long): Long {
        val now = nowMs.coerceAtLeast(0L)
        return if (now > Long.MAX_VALUE - intervalMs) Long.MAX_VALUE else now + intervalMs
    }
}

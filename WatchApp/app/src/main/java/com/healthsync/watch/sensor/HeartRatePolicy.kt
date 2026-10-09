package com.healthsync.watch.sensor

import java.util.Locale

/** Only a named heart-rate metric qualifies; raw PPG, heartbeat and oxygen are different signals. */
object HeartRatePolicy {
    const val FRESH_MS = 120_000L
    const val DISPLAY_MS = 86_400_000L
    const val MAX_FRAME_AGE_NANOS = 10_000_000_000L
    fun isHeartRateSensor(type: Int, name: String, stringType: String): Boolean {
        if (type == 21) return true // Android TYPE_HEART_RATE
        if (type < 65_536) return false // No reinterpretation of other public sensor types.
        val label = "$name $stringType".lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ')
        if (listOf("ppg", "raw", "heartbeat", "heart beat", "oxygen", "spo2", "variability").any { it in label }) return false
        return label.contains("heart rate") || label.contains("heartrate")
    }
    fun validReading(value: Float, accuracy: Int) = value.isFinite() && value in 25f..240f && accuracy in 1..3
    fun reading(bpm: Int, measuredAt: Long, now: Long, maximumAge: Long): Int =
        if (bpm in 25..240 && measuredAt > 0 && now - measuredAt in 0L..maximumAge) bpm else -1
    fun reliableReading(bpm: Int, accuracy: Int, measuredAt: Long, now: Long, maximumAge: Long): Int =
        if (accuracy in 1..3) reading(bpm, measuredAt, now, maximumAge) else -1
    fun ageLabel(measuredAt: Long, now: Long): String {
        val age = (now - measuredAt).coerceAtLeast(0L)
        return when {
            measuredAt <= 0 -> "No reading yet"
            age < 60_000L -> "Last reading: just now"
            age < 3_600_000L -> "Last reading: ${age / 60_000L} min ago"
            else -> "Last reading: ${age / 3_600_000L} h ago"
        }
    }
    fun complicationUnit(measuredAt: Long, now: Long): String {
        val age = now - measuredAt
        return when {
            measuredAt <= 0 || age < 0 || age <= FRESH_MS -> "BPM"
            age < 3_600_000L -> "LAST ${age / 60_000L}m"
            else -> "LAST ${age / 3_600_000L}h"
        }
    }
    /** SensorEvent timestamps use elapsed realtime, including sleep, rather than delivery wall time. */
    fun capturedWallTime(eventNanos: Long, nowNanos: Long, nowWallMs: Long,
                         acquisitionStartedNanos: Long, lastAcceptedNanos: Long): Long? {
        if (timestampRejection(eventNanos, nowNanos, nowWallMs, acquisitionStartedNanos, lastAcceptedNanos) != null) return null
        val ageNanos = nowNanos - eventNanos
        val capturedWall = nowWallMs - ageNanos / 1_000_000L
        return capturedWall
    }
    fun validOxygen(value: Float, accuracy: Int) = value.isFinite() && value in 50f..100f && accuracy in 1..3

    internal fun frameRejection(value: Float?, accuracy: Int, eventNanos: Long, nowNanos: Long,
                               nowWallMs: Long, acquisitionStartedNanos: Long,
                               lastAcceptedNanos: Long,
                               observedTimestampIssue: HeartRateFrameRejection? = null): HeartRateFrameRejection? = when {
        accuracy == -1 -> HeartRateFrameRejection.NO_CONTACT
        accuracy !in 1..3 -> HeartRateFrameRejection.ACCURACY
        value == null || !value.isFinite() || value !in 25f..240f -> HeartRateFrameRejection.VALUE
        else -> timestampRejection(eventNanos, nowNanos, nowWallMs, acquisitionStartedNanos, lastAcceptedNanos)
            ?: observedTimestampIssue
    }

    internal fun timestampRejection(eventNanos: Long, nowNanos: Long, nowWallMs: Long,
                                   acquisitionStartedNanos: Long, lastAcceptedNanos: Long): HeartRateFrameRejection? = when {
        eventNanos <= 0L || acquisitionStartedNanos <= 0L || nowWallMs <= 0L -> HeartRateFrameRejection.TIMESTAMP_INVALID
        eventNanos < acquisitionStartedNanos -> HeartRateFrameRejection.TIMESTAMP_BEFORE_REQUEST
        eventNanos > nowNanos -> HeartRateFrameRejection.TIMESTAMP_FUTURE
        eventNanos <= lastAcceptedNanos -> HeartRateFrameRejection.TIMESTAMP_DUPLICATE_OR_REORDERED
        nowNanos - eventNanos > MAX_FRAME_AGE_NANOS -> HeartRateFrameRejection.TIMESTAMP_STALE
        nowWallMs - (nowNanos - eventNanos) / 1_000_000L <= 0L -> HeartRateFrameRejection.TIMESTAMP_INVALID
        else -> null
    }
}

internal enum class HeartRateFrameRejection {
    NO_CONTACT, ACCURACY, VALUE, TIMESTAMP_INVALID, TIMESTAMP_BEFORE_REQUEST,
    TIMESTAMP_FUTURE, TIMESTAMP_DUPLICATE_OR_REORDERED, TIMESTAMP_STALE
}

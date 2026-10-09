package com.healthsync.watch.sensor

/** An unreliable sensor value is feedback only, never an accepted health measurement. */
data class HeartRateSensorPreview(val bpm: Int, val capturedAt: Long) {
    fun displayBpm(now: Long) = HeartRatePolicy.reading(bpm, capturedAt, now, HeartRatePolicy.DISPLAY_MS)
    fun valueText(now: Long): String {
        val seconds = ((now - capturedAt) / 1000L).coerceAtLeast(0L)
        val age = when {
            seconds < 5L -> "just now"
            seconds < 60L -> "${seconds}s ago"
            seconds < 3600L -> "${seconds / 60L} min ago"
            else -> "${seconds / 3600L} h ago"
        }
        return "$bpm bpm\nSensor reading · $age"
    }
}

internal class HeartRatePreviewTracker {
    var preview: HeartRateSensorPreview? = null
        private set
    private var lastEventNanos = 0L

    fun reset() { preview = null; lastEventNanos = 0L }

    /** Validate shape/time independently of accuracy so diagnostics do not hide invalid zeros. */
    fun observe(value: Float?, accuracy: Int, eventNanos: Long, nowNanos: Long,
                nowWallMs: Long, startedNanos: Long): HeartRateFrameRejection? {
        val timestampIssue = HeartRatePolicy.timestampRejection(eventNanos, nowNanos, nowWallMs,
            startedNanos, lastEventNanos)
        if (eventNanos > lastEventNanos && eventNanos in startedNanos..nowNanos) lastEventNanos = eventNanos
        preview = if (accuracy == 0 && value != null && value.isFinite() && value in 25f..240f && timestampIssue == null) {
            HeartRateSensorPreview(value.toInt(), nowWallMs - (nowNanos - eventNanos) / 1_000_000L)
        } else null
        return timestampIssue
    }
}

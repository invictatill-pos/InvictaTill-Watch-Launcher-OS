package com.healthsync.watch.sensor

/** A plausible accuracy-zero saturation sample can be shown as feedback, never synced as a capture. */
data class OxygenSensorPreview(val percentage: Float, val capturedAt: Long) {
    fun display(now: Long): Float = OxygenCapturePolicy.displayReading(percentage, capturedAt, now, HeartRatePolicy.DISPLAY_MS)
    companion object {
        fun fromFrame(value: Float, accuracy: Int, eventNanos: Long, nowNanos: Long,
                      nowWall: Long, startedNanos: Long, lastEventNanos: Long): OxygenSensorPreview? {
            if (accuracy != 0 || !value.isFinite() || value !in 50f..100f) return null
            val captured = HeartRatePolicy.capturedWallTime(eventNanos, nowNanos, nowWall, startedNanos, lastEventNanos) ?: return null
            return OxygenSensorPreview(value, captured)
        }
    }
}

data class OxygenDisplayReading(val percentage: Float, val capturedAt: Long, val unverified: Boolean)

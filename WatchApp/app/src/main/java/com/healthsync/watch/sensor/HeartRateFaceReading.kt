package com.healthsync.watch.sensor

/** Display feedback has its own source and lifetime; it never changes recorded measurement quality. */
data class HeartRateFaceReading(val bpm: Int, val capturedAt: Long, val sensorReading: Boolean)

object HeartRateFacePolicy {
    fun select(recordedBpm: Int, recordedAccuracy: Int, recordedAt: Long,
               preview: HeartRateSensorPreview?, now: Long): HeartRateFaceReading {
        val reliable = HeartRatePolicy.reliableReading(recordedBpm, recordedAccuracy, recordedAt,
            now, HeartRatePolicy.DISPLAY_MS)
        val sensor = preview?.takeIf { it.displayBpm(now) > 0 }
        if (sensor != null && (reliable <= 0 || sensor.capturedAt > recordedAt)) {
            return HeartRateFaceReading(sensor.bpm, sensor.capturedAt, sensorReading = true)
        }
        if (reliable > 0) return HeartRateFaceReading(reliable, recordedAt, sensorReading = false)
        return HeartRateFaceReading(-1, 0L, sensorReading = false)
    }
}

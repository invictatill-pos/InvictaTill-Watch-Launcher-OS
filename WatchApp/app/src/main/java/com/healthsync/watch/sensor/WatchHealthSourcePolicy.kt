package com.healthsync.watch.sensor

object WatchHealthSourcePolicy {
    const val SENSOR = "WATCH_SENSOR"
    const val HEALTH_APP = "WATCH_HEALTH_APP"
    fun validAppIdentity(packageName: String, label: String): Boolean =
        packageName.length <= 255 && packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) &&
            label.isNotBlank() && label.length <= 160 && label.none { it.isISOControl() }

    fun heartRate(bpm: Int, accuracy: Int, capturedAt: Long, now: Long, maximumAge: Long,
                  source: String, packageName: String, label: String): Int = when {
        source == SENSOR -> HeartRatePolicy.reliableReading(bpm, accuracy, capturedAt, now, maximumAge)
        source == HEALTH_APP && accuracy in 0..3 && validAppIdentity(packageName, label) ->
            HeartRatePolicy.reading(bpm, capturedAt, now, maximumAge)
        else -> -1
    }
    fun oxygen(value: Float, capturedAt: Long, now: Long, maximumAge: Long,
               source: String, packageName: String, label: String): Float =
        if (source == SENSOR || (source == HEALTH_APP && validAppIdentity(packageName, label)))
            OxygenCapturePolicy.displayReading(value, capturedAt, now, maximumAge) else -1f
}

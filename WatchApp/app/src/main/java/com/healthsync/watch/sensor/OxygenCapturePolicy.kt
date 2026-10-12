package com.healthsync.watch.sensor

import java.util.Locale

/** Android has no public SpO2 type. Only an explicit vendor saturation metric qualifies. */
object OxygenCapturePolicy {
    fun isOxygenSensor(type: Int, name: String, stringType: String): Boolean {
        if (type < 65_536) return false
        val label = "$name $stringType".lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ')
        if (label.contains("raw") || label.contains("ppg") || label.contains("infrared") || label.contains("waveform")) return false
        return label.contains("spo2") || label.contains("oxygen saturation") || label.contains("blood oxygen")
    }

    fun displayReading(value: Float, capturedAt: Long, now: Long, maximumAge: Long): Float =
        if (value.isFinite() && value in 50f..100f && capturedAt > 0L && now - capturedAt in 0L..maximumAge) value else -1f
}

package com.healthsync.watch.sensor

/** A sensor observation can establish a real zero; app startup and date rollover cannot. */
internal object StepCapturePolicy {
    val sources = setOf("HARDWARE_COUNTER", "HARDWARE_DETECTOR", "MOTION_ESTIMATE", "MIXED_CAPTURE")
    fun capturedToday(capturedAt: Long, captureDay: String, currentDay: String, source: String, nowWall: Long): Boolean =
        capturedAt > 0L && capturedAt <= nowWall && captureDay.isNotBlank() &&
            captureDay == currentDay && source in sources

    fun visibleCapture(capturedToday: Boolean, status: String): Boolean = capturedToday && status == "captured"

    fun sourceAfterObservation(previous: String, observed: String): String = when {
        previous !in sources || previous == observed -> observed
        else -> "MIXED_CAPTURE"
    }
}

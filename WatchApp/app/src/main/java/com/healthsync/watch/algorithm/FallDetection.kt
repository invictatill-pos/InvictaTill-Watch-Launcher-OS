package com.healthsync.watch.algorithm

/** Free fall followed by impact. Wrist rotation alone never triggers an alert. */
class FallDetection(private val onFall: (String) -> Unit) {
    private var freeFallStart = -1L
    private var lastFreeFall = -1L
    private var armed = false
    private var lastAlert = -30_000L

    fun process(ax: Float, ay: Float, az: Float, nowMs: Long) {
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return
        val magnitude = kotlin.math.sqrt((ax * ax + ay * ay + az * az).toDouble())
        if (nowMs - lastAlert < 20_000L) return
        if (magnitude < 3.0) {
            if (freeFallStart < 0 || nowMs - lastFreeFall > 100L) freeFallStart = nowMs
            lastFreeFall = nowMs
            if (nowMs - freeFallStart >= 80L) armed = true
            return
        }
        if (armed && nowMs - lastFreeFall in 0L..800L && magnitude >= 20.0) {
            lastAlert = nowMs
            onFall(if (magnitude >= 35.0) "HIGH" else "LOW")
            reset()
        } else if (lastFreeFall >= 0 && nowMs - lastFreeFall > 800L) reset()
        else if (!armed) freeFallStart = -1L
    }
    fun reset() { freeFallStart = -1L; lastFreeFall = -1L; armed = false }
}

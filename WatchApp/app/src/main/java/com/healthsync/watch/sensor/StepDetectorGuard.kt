package com.healthsync.watch.sensor

/** Each Android TYPE_STEP_DETECTOR frame reports exactly one real detected step. */
internal class StepDetectorGuard {
    private var lastAccepted = -1L
    fun accept(value: Float, capturedAt: Long, nowElapsed: Long, registeredAt: Long): Boolean {
        if (value != 1f || registeredAt <= 0L || capturedAt < registeredAt || capturedAt <= lastAccepted ||
            nowElapsed - capturedAt !in 0L..120_000L) return false
        lastAccepted = capturedAt
        return true
    }
    fun reset() { lastAccepted = -1L }
}

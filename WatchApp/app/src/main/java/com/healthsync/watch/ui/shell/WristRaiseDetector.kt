package com.healthsync.watch.ui.shell

import kotlin.math.sqrt

/**
 * A conservative foreground wrist heuristic, independent of Android for deterministic checks.
 * It recognizes a changed pose that settles with the display facing upward. Sensor axes remain
 * unbiased between left and right wrists. It does not identify the wearer or wake a sleeping CPU.
 */
internal class WristRaiseDetector(private val accelerometer: Boolean = false) {
    private data class Vector(val x: Double, val y: Double, val z: Double) {
        val length: Double get() = sqrt(x * x + y * y + z * z)
        fun normalized(): Vector = length.let { if (it > .001) Vector(x / it, y / it, z / it) else this }
        fun blend(other: Vector, fraction: Double): Vector = Vector(
            x + (other.x - x) * fraction, y + (other.y - y) * fraction, z + (other.z - z) * fraction)
        fun dot(other: Vector): Double = x * other.x + y * other.y + z * other.z
    }

    private var initialized = false
    private var startedAt = 0L
    private var previousAt = 0L
    private var filtered = Vector(0.0, 0.0, 0.0)
    private var baseline = filtered
    private var previousPose = filtered
    private var candidateAt = -1L
    private var lastWakeAt = Long.MIN_VALUE

    fun reset() {
        initialized = false
        candidateAt = -1L
        lastWakeAt = Long.MIN_VALUE
    }

    /** Timestamp is the monotonic SensorEvent timestamp, in nanoseconds. */
    fun onSample(timestampNanos: Long, x: Float, y: Float, z: Float): Boolean {
        if (timestampNanos < 0 || !x.isFinite() || !y.isFinite() || !z.isFinite()) return false
        val now = timestampNanos / 1_000_000L
        val raw = Vector(x.toDouble(), y.toDouble(), z.toDouble())
        // Avoid treating free fall, impacts, zero-valued or malformed sensors as a wrist pose.
        if (raw.length !in 6.5..13.5) { candidateAt = -1L; return false }
        if (!initialized || now - previousAt > 2_000L) {
            initialized = true
            startedAt = now
            previousAt = now
            filtered = raw
            baseline = raw.normalized()
            previousPose = baseline
            candidateAt = -1L
            return false
        }
        if (now <= previousAt) return false
        val elapsed = (now - previousAt).toDouble()
        previousAt = now
        val filterMs = if (accelerometer) 250.0 else 100.0
        filtered = filtered.blend(raw, elapsed / (filterMs + elapsed))
        val pose = filtered.normalized()
        val oldPose = previousPose
        previousPose = pose
        val warm = now - startedAt >= 800L
        val cooled = lastWakeAt == Long.MIN_VALUE || now - lastWakeAt >= 3_000L
        // cos(28 degrees): a deliberate changed orientation, rather than tiny wrist jitter.
        val changedPose = baseline.dot(pose) < .883
        val displayUp = pose.z >= .15
        val eligible = warm && cooled && changedPose && displayUp
        if (eligible) {
            // The final pose must settle for two normal-rate sample intervals. Continuous
            // shaking or a single acceleration impulse cannot repeatedly illuminate the clock.
            if (candidateAt < 0L || oldPose.dot(pose) < .978) candidateAt = now
            if (now - candidateAt >= 400L) {
                lastWakeAt = now
                candidateAt = -1L
                baseline = pose
                return true
            }
        } else {
            candidateAt = -1L
            baseline = baseline.blend(pose, elapsed / (6_500.0 + elapsed)).normalized()
        }
        return false
    }
}

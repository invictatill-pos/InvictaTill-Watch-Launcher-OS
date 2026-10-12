package com.healthsync.watch.sensor

import kotlin.math.sqrt

/** Motion-only rest estimate. Motion cannot identify deep sleep, REM, or a sleep score. */
internal data class ObservedRest(val startTime: Long, val endTime: Long, val estimatedMinutes: Int)

internal class ActigraphyRestEngine {
    companion object {
        private const val EPOCH_MS = 60_000L
        private const val MAX_SAMPLE_GAP_MS = 5_000L
        private const val ONSET_EPOCHS = 20
        private const val WAKE_EPOCHS = 12
    }

    private var lastElapsed = -1L
    private var lastWall = 0L
    private var epochElapsed = -1L
    private var epochWall = 0L
    private var count = 0
    private var sum = 0.0
    private var sumSquared = 0.0
    private var candidateStart = 0L
    private var stillEpochs = 0
    private var restStart = 0L
    private var observedRestMinutes = 0
    private var wakeEpochs = 0
    private var wakeStart = 0L
    val isResting: Boolean get() = restStart > 0L

    fun sample(x: Float, y: Float, z: Float, elapsed: Long, wall: Long, nightWindow: Boolean): ObservedRest? {
        if (!listOf(x, y, z).all { it.isFinite() } || elapsed < 0L || wall <= 0L) return null
        if (lastElapsed >= 0L && elapsed <= lastElapsed) return null
        if (lastElapsed >= 0L && (elapsed - lastElapsed > MAX_SAMPLE_GAP_MS ||
                kotlin.math.abs((wall - lastWall) - (elapsed - lastElapsed)) > MAX_SAMPLE_GAP_MS)) reset()
        lastElapsed = elapsed
        lastWall = wall
        if (epochElapsed < 0L) { epochElapsed = elapsed; epochWall = wall }

        var completed: ObservedRest? = null
        if (elapsed - epochElapsed >= EPOCH_MS) {
            // Require real coverage. A couple of samples separated by a minute is not a rest epoch.
            if (count >= 30 && elapsed - epochElapsed <= EPOCH_MS + MAX_SAMPLE_GAP_MS) {
                val mean = sum / count
                val variance = (sumSquared / count - mean * mean).coerceAtLeast(0.0)
                completed = evaluate(variance, epochWall, nightWindow)
            } else resetSession()
            epochElapsed = elapsed; epochWall = wall; count = 0; sum = 0.0; sumSquared = 0.0
        }
        val magnitude = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
        count++; sum += magnitude; sumSquared += magnitude * magnitude
        return completed
    }

    private fun evaluate(variance: Double, epochStart: Long, nightWindow: Boolean): ObservedRest? {
        val still = variance < 0.15
        val movement = variance > 0.45
        if (!isResting) {
            if (still && nightWindow) {
                if (stillEpochs == 0) candidateStart = epochStart
                stillEpochs++
                if (stillEpochs >= ONSET_EPOCHS) {
                    restStart = candidateStart; observedRestMinutes = stillEpochs
                }
            } else { stillEpochs = 0; candidateStart = 0L }
            return null
        }
        if (epochStart - restStart > 18L * 3_600_000L) { resetSession(); return null }
        if (movement) {
            if (wakeEpochs == 0) wakeStart = epochStart
            wakeEpochs++
            if (wakeEpochs >= WAKE_EPOCHS) {
                val result = if (observedRestMinutes >= 30 && wakeStart > restStart)
                    ObservedRest(restStart, wakeStart, observedRestMinutes) else null
                resetSession()
                return result
            }
        } else {
            wakeEpochs = 0; wakeStart = 0L
            if (still) observedRestMinutes++
        }
        return null
    }

    fun reset() {
        lastElapsed = -1L; lastWall = 0L; epochElapsed = -1L; epochWall = 0L
        count = 0; sum = 0.0; sumSquared = 0.0; resetSession()
    }
    private fun resetSession() {
        candidateStart = 0L; stillEpochs = 0; restStart = 0L
        observedRestMinutes = 0; wakeEpochs = 0; wakeStart = 0L
    }
}

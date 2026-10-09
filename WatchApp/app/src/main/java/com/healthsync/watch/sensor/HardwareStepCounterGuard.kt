package com.healthsync.watch.sensor

/** Validate cumulative hardware counts without estimating steps or changing software gait detection. */
internal class HardwareStepCounterGuard(initialCount: Int = -1, initialTimeMs: Long = 0L) {
    var baselineCount = if (initialCount >= 0 && initialTimeMs > 0) initialCount else -1
        private set
    var baselineTimeMs = if (baselineCount >= 0) initialTimeMs else 0L
        private set
    private var lastSeenMs = baselineTimeMs
    private var resetCandidate: Int? = null

    /** null rejects a frame; zero establishes/confirms a baseline without adding any steps. */
    fun sample(raw: Float, maximumRange: Float, capturedAtMs: Long, nowElapsedMs: Long, registeredAtMs: Long): Int? {
        val value = raw.toDouble()
        if (!value.isFinite() || value < 0.0 || value > Int.MAX_VALUE.toDouble() || value != kotlin.math.floor(value) ||
            (maximumRange.isFinite() && maximumRange > 0f && raw > maximumRange) ||
            registeredAtMs <= 0 || capturedAtMs < registeredAtMs || capturedAtMs <= lastSeenMs ||
            nowElapsedMs - capturedAtMs !in 0L..5_000L) return null
        val count = raw.toInt()
        lastSeenMs = capturedAtMs
        if (baselineCount < 0) { rebase(count, capturedAtMs); return 0 }
        if (count < baselineCount) {
            // A single low glitch must not turn the following normal count into a giant delta.
            val candidate = resetCandidate
            if (candidate != null && count >= candidate) rebase(count, capturedAtMs)
            else resetCandidate = count
            return 0
        }
        resetCandidate = null
        val delta = count.toLong() - baselineCount
        val elapsed = capturedAtMs - baselineTimeMs
        // Generous invalid-jump guard, not a cadence estimate: 10 steps/sec plus 20 counts of
        // batching/quantization slack. Elapsed time preserves recovery over same-boot restarts.
        val maximumDelta = (elapsed / 1000L + 1L) * 10L + 20L
        if (delta > maximumDelta) return null
        rebase(count, capturedAtMs)
        return delta.toInt()
    }

    fun reset() { baselineCount = -1; baselineTimeMs = 0L; lastSeenMs = 0L; resetCandidate = null }
    private fun rebase(count: Int, timeMs: Long) {
        baselineCount = count; baselineTimeMs = timeMs; resetCandidate = null
    }
}

package com.healthsync.watch.sensor

/** Give each exposed metric sensor time to respond within one bounded acquisition. */
object SensorCandidateRetryPolicy {
    fun candidateWindowMs(totalWindowMs: Long, candidateCount: Int): Long =
        (totalWindowMs / candidateCount.coerceAtLeast(1)).coerceIn(5_000L, 20_000L)

    fun shouldAdvance(nowMs: Long, selectedAtMs: Long, totalWindowMs: Long,
                      candidateCount: Int, hasRemaining: Boolean): Boolean =
        hasRemaining && nowMs >= selectedAtMs &&
            nowMs - selectedAtMs >= candidateWindowMs(totalWindowMs, candidateCount)
}

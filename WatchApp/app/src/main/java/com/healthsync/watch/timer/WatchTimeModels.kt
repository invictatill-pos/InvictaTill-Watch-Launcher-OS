package com.healthsync.watch.timer

enum class TimerPhase { IDLE, RUNNING, PAUSED, FINISHED }

/** Monotonic time handles sleep and wall-clock corrections; wall time is only a reboot recovery. */
data class TimerCheckpoint(
    val phase: TimerPhase = TimerPhase.IDLE,
    val durationMs: Long = 0L,
    val remainingAtAnchorMs: Long = 0L,
    val anchorElapsedMs: Long = 0L,
    val anchorWallMs: Long = 0L,
    val bootCount: Int = -1
) {
    fun remaining(nowElapsedMs: Long, nowWallMs: Long, currentBootCount: Int): Long {
        if (phase != TimerPhase.RUNNING) return remainingAtAnchorMs.coerceIn(0, durationMs.coerceAtLeast(0))
        val sameBoot = if (bootCount >= 0 && currentBootCount >= 0) bootCount == currentBootCount else nowElapsedMs >= anchorElapsedMs
        val delta = if (sameBoot) nowElapsedMs - anchorElapsedMs else nowWallMs - anchorWallMs
        return (remainingAtAnchorMs - delta.coerceAtLeast(0)).coerceIn(0, durationMs.coerceAtLeast(0))
    }

    fun pause(nowElapsedMs: Long, nowWallMs: Long, currentBootCount: Int): TimerCheckpoint = copy(
        phase = TimerPhase.PAUSED,
        remainingAtAnchorMs = remaining(nowElapsedMs, nowWallMs, currentBootCount)
    )

    fun resume(nowElapsedMs: Long, nowWallMs: Long, currentBootCount: Int) = copy(
        phase = TimerPhase.RUNNING,
        anchorElapsedMs = nowElapsedMs,
        anchorWallMs = nowWallMs,
        bootCount = currentBootCount
    )

    companion object {
        fun start(durationMs: Long, nowElapsedMs: Long, nowWallMs: Long, bootCount: Int) = TimerCheckpoint(
            TimerPhase.RUNNING, durationMs.coerceIn(1_000L, 86_400_000L), durationMs.coerceIn(1_000L, 86_400_000L),
            nowElapsedMs, nowWallMs, bootCount
        )
    }
}

data class StopwatchCheckpoint(
    val accumulatedMs: Long = 0L,
    val anchorElapsedMs: Long = 0L,
    val running: Boolean = false,
    val bootCount: Int = -1,
    val laps: List<Long> = emptyList()
) {
    fun elapsed(nowElapsedMs: Long, currentBootCount: Int): Long {
        val sameBoot = if (bootCount >= 0 && currentBootCount >= 0) bootCount == currentBootCount else nowElapsedMs >= anchorElapsedMs
        return accumulatedMs.coerceAtLeast(0) + if (running && sameBoot) (nowElapsedMs - anchorElapsedMs).coerceAtLeast(0) else 0
    }

    fun pause(nowElapsedMs: Long, currentBootCount: Int) = copy(accumulatedMs = elapsed(nowElapsedMs, currentBootCount), running = false)
    fun resume(nowElapsedMs: Long, currentBootCount: Int) = copy(anchorElapsedMs = nowElapsedMs, running = true, bootCount = currentBootCount)
    fun checkpoint(nowElapsedMs: Long, currentBootCount: Int): StopwatchCheckpoint {
        val sameBoot = if (bootCount >= 0 && currentBootCount >= 0) bootCount == currentBootCount else nowElapsedMs >= anchorElapsedMs
        return copy(accumulatedMs = elapsed(nowElapsedMs, currentBootCount), anchorElapsedMs = nowElapsedMs,
            bootCount = currentBootCount, running = running && sameBoot)
    }
    fun lap(nowElapsedMs: Long, currentBootCount: Int) =
        if (laps.size >= 100) this else copy(laps = laps + elapsed(nowElapsedMs, currentBootCount))
}

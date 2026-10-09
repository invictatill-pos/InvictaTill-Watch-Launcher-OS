package com.healthsync.watch.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTimeModelsTest {
    @Test fun countdownUsesMonotonicTimeAcrossWallClockCorrections() {
        val timer = TimerCheckpoint.start(60_000, 10_000, 1_000_000, 4)
        assertEquals(45_000L, timer.remaining(25_000, 50_000_000, 4))
        assertEquals(0L, timer.remaining(80_000, 1_000_000, 4))
    }
    @Test fun pauseAndResumeDoNotCountPausedTime() {
        val paused = TimerCheckpoint.start(60_000, 1_000, 1_000_000, 4).pause(11_000, 1_010_000, 4)
        assertEquals(50_000L, paused.remaining(900_000, 1_900_000, 4))
        val resumed = paused.resume(900_000, 1_900_000, 4)
        assertEquals(40_000L, resumed.remaining(910_000, 1_910_000, 4))
    }
    @Test fun countdownRecoversRemainingAfterReboot() {
        val timer = TimerCheckpoint.start(60_000, 50_000, 1_000_000, 4)
        assertEquals(15_000L, timer.remaining(500, 1_045_000, 5))
        assertEquals(0L, timer.remaining(500, 1_100_000, 5))
    }
    @Test fun durationAndRemainingAreBounded() {
        assertEquals(1_000L, TimerCheckpoint.start(-1, 1, 1, 1).durationMs)
        assertEquals(86_400_000L, TimerCheckpoint.start(Long.MAX_VALUE, 1, 1, 1).durationMs)
        assertEquals(60_000L, TimerCheckpoint.start(60_000, 100, 100, 1).remaining(50, 50, 1))
    }
    @Test fun stopwatchCountsSleepButNotPause() {
        val started = StopwatchCheckpoint().resume(1_000, 4)
        assertEquals(30_000L, started.elapsed(31_000, 4))
        val paused = started.pause(31_000, 4)
        assertEquals(30_000L, paused.elapsed(100_000, 4))
        val resumed = paused.resume(100_000, 4)
        assertEquals(45_000L, resumed.elapsed(115_000, 4))
    }
    @Test fun stopwatchCheckpointKeepsRunningAcrossActivityRecreation() {
        val saved = StopwatchCheckpoint().resume(1_000, 4).checkpoint(10_000, 4)
        assertTrue(saved.running)
        assertEquals(25_000L, saved.elapsed(26_000, 4))
    }
    @Test fun stopwatchStopsAfterRebootAndPreservesCheckpoint() {
        val saved = StopwatchCheckpoint().resume(1_000, 4).checkpoint(10_000, 4)
        val restored = saved.checkpoint(2_000, 5)
        assertFalse(restored.running)
        assertEquals(9_000L, restored.elapsed(20_000, 5))
    }
    @Test fun stopwatchUnknownBootCountStopsIfElapsedClockResets() {
        val saved = StopwatchCheckpoint().resume(1_000, -1).checkpoint(10_000, -1)
        val restored = saved.checkpoint(2_000, -1)
        assertFalse(restored.running)
        assertEquals(9_000L, restored.elapsed(20_000, -1))
    }
    @Test fun lapsUseCumulativeElapsedWithoutDoubleCounting() {
        val stopwatch = StopwatchCheckpoint().resume(1_000, 4).lap(11_000, 4).lap(16_000, 4)
        assertEquals(listOf(10_000L, 15_000L), stopwatch.laps)
        assertEquals(5_000L, stopwatch.laps[1] - stopwatch.laps[0])
    }
    @Test fun lapLimitPreservesFirstLapBaseline() {
        var stopwatch = StopwatchCheckpoint().resume(1_000, 4)
        repeat(101) { stopwatch = stopwatch.lap(2_000L + it * 1_000L, 4) }
        assertEquals(100, stopwatch.laps.size)
        assertEquals(1_000L, stopwatch.laps.first())
        assertEquals(100_000L, stopwatch.laps.last())
    }
}

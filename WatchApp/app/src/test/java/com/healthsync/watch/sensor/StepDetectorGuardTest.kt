package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class StepDetectorGuardTest {
    @Test fun rejectsInvalidRepeatedReorderedStaleAndFutureStepFrames() {
        val guard = StepDetectorGuard()
        assertTrue(guard.accept(1f, 1000L, 2000L, 500L))
        assertFalse(guard.accept(1f, 1000L, 2000L, 500L))
        assertFalse(guard.accept(1f, 900L, 2000L, 500L))
        assertFalse(guard.accept(Float.NaN, 1100L, 2000L, 500L))
        assertFalse(guard.accept(98f, 1100L, 2000L, 500L))
        assertFalse(guard.accept(1f, 2100L, 2000L, 500L))
        assertFalse(guard.accept(1f, 1100L, 121_101L, 500L))
        assertFalse(guard.accept(1f, 1100L, 2000L, 1200L))
        assertTrue(guard.accept(1f, 1100L, 2000L, 500L))
    }
    @Test fun genuineScreenOffBatchRetainsCaptureTimeAndCountsEachFrameOnce() {
        val guard = StepDetectorGuard()
        assertTrue(guard.accept(1f, 1000L, 60_000L, 500L))
        assertTrue(guard.accept(1f, 2000L, 60_000L, 500L))
    }
}

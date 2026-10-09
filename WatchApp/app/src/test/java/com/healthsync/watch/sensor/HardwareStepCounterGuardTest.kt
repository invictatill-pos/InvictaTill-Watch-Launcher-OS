package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class HardwareStepCounterGuardTest {
    @Test fun ordinaryCountsAndSameBootRestartRecoveryUseElapsedTime() {
        val guard = HardwareStepCounterGuard()
        assertEquals(0, guard.sample(100f, 1_000_000f, 1000L, 1000L, 500L))
        assertEquals(2, guard.sample(102f, 1_000_000f, 2000L, 2000L, 500L))
        val restored = HardwareStepCounterGuard(102, 2000L)
        assertEquals(250, restored.sample(352f, 1_000_000f, 62_000L, 62_000L, 61_000L))
    }
    @Test fun firstSampleAfterRebootEstablishesBaselineWithoutCountingBootTotal() {
        val guard = HardwareStepCounterGuard()
        assertEquals(0, guard.sample(20f, 100_000f, 1000L, 1000L, 500L))
        assertEquals(1, guard.sample(21f, 100_000f, 2000L, 2000L, 500L))
    }
    @Test fun legacyBaselineWithoutCaptureTimeCannotCreditAnUnknownInterval() {
        val guard = HardwareStepCounterGuard(100, 0L)
        assertEquals(0, guard.sample(900_000f, 1_000_000f, 1000L, 1000L, 500L))
        assertEquals(1, guard.sample(900_001f, 1_000_000f, 2000L, 2000L, 500L))
    }
    @Test fun impossibleJumpCannotPoisonThePreviousValidBaseline() {
        val guard = HardwareStepCounterGuard(100, 1000L)
        assertNull(guard.sample(900_000f, 1_000_000f, 2000L, 2000L, 500L))
        assertEquals(100, guard.baselineCount)
        assertEquals(2, guard.sample(102f, 1_000_000f, 3000L, 3000L, 500L))
    }
    @Test fun oneLowGlitchDoesNotTurnTheNormalCounterIntoAFalseJump() {
        val guard = HardwareStepCounterGuard(100, 1000L)
        assertEquals(0, guard.sample(1f, 100_000f, 2000L, 2000L, 500L))
        assertEquals(100, guard.baselineCount)
        assertEquals(1, guard.sample(101f, 100_000f, 3000L, 3000L, 500L))
    }
    @Test fun twoOrderedLowSamplesConfirmCounterResetWithoutAddingResetValues() {
        val guard = HardwareStepCounterGuard(100, 1000L)
        assertEquals(0, guard.sample(1f, 100_000f, 2000L, 2000L, 500L))
        assertEquals(0, guard.sample(2f, 100_000f, 3000L, 3000L, 500L))
        assertEquals(2, guard.baselineCount)
        assertEquals(1, guard.sample(3f, 100_000f, 4000L, 4000L, 500L))
    }
    @Test fun invalidValuesHardwareRangeAndSensorFrameTimesAreRejected() {
        val guard = HardwareStepCounterGuard(100, 1000L)
        assertNull(guard.sample(Float.MAX_VALUE, Float.MAX_VALUE, 2000L, 2000L, 500L))
        assertNull(guard.sample(Float.NaN, 1000f, 2000L, 2000L, 500L))
        assertNull(guard.sample(-1f, 1000f, 2000L, 2000L, 500L))
        assertNull(guard.sample(101.5f, 1000f, 2000L, 2000L, 500L))
        assertNull(guard.sample(101f, 100f, 2000L, 2000L, 500L))
        assertNull(guard.sample(101f, 1000f, 1000L, 1000L, 500L))
        assertNull(guard.sample(101f, 1000f, 900L, 1000L, 500L))
        assertNull(guard.sample(101f, 1000f, 2001L, 2000L, 500L))
        assertNull(guard.sample(101f, 1000f, 2000L, 7001L, 500L))
        assertNull(guard.sample(101f, 1000f, 2000L, 2000L, 2001L))
        assertEquals(100, guard.baselineCount)
    }
}

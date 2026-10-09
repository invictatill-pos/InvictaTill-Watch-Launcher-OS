package com.healthsync.watch.ui.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WristRaiseDetectorTest {
    private fun sample(detector: WristRaiseDetector, ms: Long, y: Float = 9.81f, z: Float = 0f, x: Float = 0f) =
        detector.onSample(ms * 1_000_000L, x, y, z)

    private fun rest(detector: WristRaiseDetector, from: Long = 0L) {
        for (time in from..from + 1_400L step 200L) assertFalse(sample(detector, time))
    }

    @Test fun stationaryAndSmallJitterNeverWakeTheClock() {
        val detector = WristRaiseDetector()
        for (time in 0L..60_000L step 200L) {
            assertFalse(sample(detector, time, 9.81f, if (time % 400L == 0L) .35f else -.35f, .2f))
        }
    }

    @Test fun settledRaiseWakesOnceAndAStationaryRaisedWristDoesNotRepeat() {
        val detector = WristRaiseDetector()
        rest(detector)
        var wakes = 0
        for (time in 1_600L..12_000L step 200L) if (sample(detector, time, 4.905f, 8.495f)) wakes++
        assertTrue(wakes == 1)
    }

    @Test fun accelerometerFallbackRecognizesTheSameRaiseAfterSmoothing() {
        val detector = WristRaiseDetector(accelerometer = true)
        rest(detector)
        var woke = false
        for (time in 1_600L..3_200L step 200L) woke = sample(detector, time, 4.905f, 8.495f) || woke
        assertTrue(woke)
    }

    @Test fun briefBumpAndTurningTheDisplayDownDoNotWake() {
        val detector = WristRaiseDetector()
        rest(detector)
        assertFalse(sample(detector, 1_600L, 4.905f, 8.495f))
        for (time in 1_800L..3_200L step 200L) assertFalse(sample(detector, time))
        for (time in 3_400L..5_000L step 200L) assertFalse(sample(detector, time, 4.905f, -8.495f))
    }

    @Test fun invalidSamplesAndLargeSensorGapsCannotUseAStaleBaseline() {
        val detector = WristRaiseDetector()
        rest(detector)
        assertFalse(sample(detector, 1_600L, Float.NaN))
        assertFalse(sample(detector, 1_800L, 0f))
        assertFalse(sample(detector, 2_000L, 25f, 25f))
        assertFalse(sample(detector, 5_000L, 4.905f, 8.495f))
        for (time in 5_200L..7_000L step 200L) assertFalse(sample(detector, time, 4.905f, 8.495f))
    }

    @Test fun resetRearmsWithTheCurrentPoseWithoutAnImmediateWake() {
        val detector = WristRaiseDetector()
        rest(detector)
        detector.reset()
        for (time in 1_600L..3_600L step 200L) assertFalse(sample(detector, time, 4.905f, 8.495f))
    }
}

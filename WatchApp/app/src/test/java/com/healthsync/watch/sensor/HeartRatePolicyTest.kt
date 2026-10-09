package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class HeartRatePolicyTest {
    @Test fun originalCaptureTimeIsUsedRatherThanDelayedDeliveryTime() {
        assertEquals(998_000L, HeartRatePolicy.capturedWallTime(18_000_000_000L,
            20_000_000_000L, 1_000_000L, 10_000_000_000L, 0L))
    }
    @Test fun cachedDuplicateOutOfOrderFutureAndInvalidFramesAreRejected() {
        fun captured(event: Long, last: Long = 0L, started: Long = 10_000_000_000L) =
            HeartRatePolicy.capturedWallTime(event, 20_000_000_000L, 1_000_000L, started, last)
        assertNull(captured(9_999_999_999L)) // Before this request, even if relatively recent.
        assertNull(captured(18_000_000_000L, last = 18_000_000_000L))
        assertNull(captured(17_000_000_000L, last = 18_000_000_000L))
        assertNull(captured(20_000_000_001L))
        assertNull(captured(0L))
        assertNull(captured(-1L))
        assertNull(captured(18_000_000_000L, started = 0L))
        assertNull(HeartRatePolicy.capturedWallTime(18_000_000_000L, 20_000_000_000L,
            1_000L, 10_000_000_000L, 0L)) // Invalid resulting wall time.
    }
    @Test fun queuedFramesOlderThanTenSecondsAreRejectedEvenWithinAcquisition() {
        assertEquals(990_000L, HeartRatePolicy.capturedWallTime(10_000_000_000L,
            20_000_000_000L, 1_000_000L, 1L, 0L))
        assertNull(HeartRatePolicy.capturedWallTime(9_999_999_999L,
            20_000_000_000L, 1_000_000L, 1L, 0L))
    }
    @Test fun oxygenRejectsNoContactUnreliableAndInvalidValues() {
        assertTrue(HeartRatePolicy.validOxygen(98f, 3))
        assertFalse(HeartRatePolicy.validOxygen(98f, -1))
        assertFalse(HeartRatePolicy.validOxygen(98f, 0))
        assertFalse(HeartRatePolicy.validOxygen(98f, 4))
        assertFalse(HeartRatePolicy.validOxygen(98f, Int.MAX_VALUE))
        assertFalse(HeartRatePolicy.validOxygen(Float.NaN, 3))
        assertFalse(HeartRatePolicy.validOxygen(101f, 3))
        assertFalse(HeartRatePolicy.validOxygen(49f, 3))
    }
    @Test fun cachedHeartRateNeedsReliableStoredAccuracy() {
        assertEquals(72, HeartRatePolicy.reliableReading(72, 1, 1000L, 2000L, HeartRatePolicy.DISPLAY_MS))
        assertEquals(-1, HeartRatePolicy.reliableReading(72, 0, 1000L, 2000L, HeartRatePolicy.DISPLAY_MS))
        assertEquals(-1, HeartRatePolicy.reliableReading(72, -1, 1000L, 2000L, HeartRatePolicy.DISPLAY_MS))
        assertEquals(-1, HeartRatePolicy.reliableReading(72, 4, 1000L, 2000L, HeartRatePolicy.DISPLAY_MS))
    }
    @Test fun reliablePositiveSamplesOnly() {
        assertTrue(HeartRatePolicy.validReading(72f, 1))
        assertFalse(HeartRatePolicy.validReading(72f, 0))
        assertFalse(HeartRatePolicy.validReading(72f, -1))
        assertFalse(HeartRatePolicy.validReading(72f, 4))
        assertFalse(HeartRatePolicy.validReading(72f, Int.MAX_VALUE))
        assertFalse(HeartRatePolicy.validReading(Float.NaN, 3))
        assertFalse(HeartRatePolicy.validReading(0f, 3))
        assertFalse(HeartRatePolicy.validReading(241f, 3))
    }
    @Test fun onlyHeartRateMetricsAreDiscovered() {
        assertTrue(HeartRatePolicy.isHeartRateSensor(21, "", ""))
        assertTrue(HeartRatePolicy.isHeartRateSensor(65536, "Vendor Heart Rate", "vendor.heart_rate"))
        assertFalse(HeartRatePolicy.isHeartRateSensor(65536, "Raw heart rate PPG", ""))
        assertFalse(HeartRatePolicy.isHeartRateSensor(65536, "Heart rate variability", ""))
        assertFalse(HeartRatePolicy.isHeartRateSensor(31, "Heart beat", ""))
        assertFalse(HeartRatePolicy.isHeartRateSensor(1, "Heart rate", ""))
        assertFalse(HeartRatePolicy.isHeartRateSensor(65536, "SpO2", ""))
    }
    @Test fun savedReadingIsVisibleBetweenLongIntervalsWithoutBeingLive() {
        val measured = 1_000_000L
        val now = measured + 600_000L
        assertEquals(-1, HeartRatePolicy.reading(75, measured, now, HeartRatePolicy.FRESH_MS))
        assertEquals(75, HeartRatePolicy.reading(75, measured, now, HeartRatePolicy.DISPLAY_MS))
        assertEquals("Last reading: 10 min ago", HeartRatePolicy.ageLabel(measured, now))
        assertEquals("LAST 10m", HeartRatePolicy.complicationUnit(measured, now))
        assertEquals("BPM", HeartRatePolicy.complicationUnit(measured, measured + 30_000L))
        assertEquals("LAST 2h", HeartRatePolicy.complicationUnit(measured, measured + 7_200_000L))
        assertEquals(-1, HeartRatePolicy.reading(75, measured, measured - 1, HeartRatePolicy.DISPLAY_MS))
        assertEquals(-1, HeartRatePolicy.reading(75, measured, measured + HeartRatePolicy.DISPLAY_MS + 1, HeartRatePolicy.DISPLAY_MS))
    }
}

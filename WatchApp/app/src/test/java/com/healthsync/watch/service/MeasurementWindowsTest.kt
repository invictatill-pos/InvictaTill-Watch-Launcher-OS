package com.healthsync.watch.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementWindowsTest {
    @Test fun automaticThirtySecondRequestsCannotKeepOneMinuteAcquisitionRunningForever() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.HEART_RATE, 1_000L, 60_000L, extendActive = false)
        windows.request(MeasurementKind.HEART_RATE, 31_000L, 60_000L, extendActive = false)
        assertEquals(30_000L, windows.remainingMs(31_000L))
        assertFalse(windows.isActive(MeasurementKind.HEART_RATE, 61_000L))
        // A subsequent interval can start a new attempt once the old acquisition has finished.
        windows.clear(MeasurementKind.HEART_RATE)
        windows.request(MeasurementKind.HEART_RATE, 61_000L, 60_000L, extendActive = false)
        assertEquals(60_000L, windows.remainingMs(61_000L))
    }

    @Test fun opticalWarmupCanUseOneMinuteWithoutExtendingOxygen() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.OXYGEN, 1000L)
        windows.request(MeasurementKind.HEART_RATE, 1000L, durationMs = 60_000L)
        assertFalse(windows.isActive(MeasurementKind.OXYGEN, 31_000L))
        assertTrue(windows.isActive(MeasurementKind.HEART_RATE, 31_000L))
        assertEquals(30_000L, windows.remainingMs(31_000L))
    }
    @Test fun heartRateRequestDoesNotStartOxygen() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.HEART_RATE, 1000L)
        assertTrue(windows.isActive(MeasurementKind.HEART_RATE, 1000L))
        assertFalse(windows.isActive(MeasurementKind.OXYGEN, 1000L))
        assertEquals(30_000L, windows.remainingMs(1000L))
    }

    @Test fun oxygenRequestDoesNotStartHeartRate() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.OXYGEN, 1000L)
        assertTrue(windows.isActive(MeasurementKind.OXYGEN, 1000L))
        assertFalse(windows.isActive(MeasurementKind.HEART_RATE, 1000L))
    }

    @Test fun overlappingOxygenRequestDoesNotExtendHeartRate() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.HEART_RATE, 1000L)
        windows.request(MeasurementKind.OXYGEN, 6000L)
        assertFalse(windows.isActive(MeasurementKind.HEART_RATE, 31_000L))
        assertTrue(windows.isActive(MeasurementKind.OXYGEN, 31_000L))
        assertEquals(5000L, windows.remainingMs(31_000L))
        windows.clear(MeasurementKind.HEART_RATE)
        assertEquals(5000L, windows.remainingMs(31_000L))
    }

    @Test fun repeatedManualHeartRateRequestsCannotExtendOriginalMinuteDeadline() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.OXYGEN, 1000L)
        windows.request(MeasurementKind.HEART_RATE, 1000L, durationMs = 60_000L)
        windows.request(MeasurementKind.HEART_RATE, 21_000L, durationMs = 60_000L)
        windows.request(MeasurementKind.HEART_RATE, 60_999L, durationMs = 60_000L)
        assertFalse(windows.isActive(MeasurementKind.OXYGEN, 31_000L))
        assertTrue(windows.isActive(MeasurementKind.HEART_RATE, 31_000L))
        assertEquals(30_000L, windows.remainingMs(31_000L))
        assertEquals(1L, windows.remainingMs(MeasurementKind.HEART_RATE, 60_999L))
        assertFalse(windows.isActive(MeasurementKind.HEART_RATE, 61_000L))
        assertEquals(0L, windows.remainingMs(61_000L))
    }

    @Test fun repeatedManualOxygenRequestStillExtendsOnlyItsOwnWindow() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.OXYGEN, 1000L)
        windows.request(MeasurementKind.HEART_RATE, 1000L)
        windows.request(MeasurementKind.OXYGEN, 21_000L)
        assertFalse(windows.isActive(MeasurementKind.HEART_RATE, 31_000L))
        assertTrue(windows.isActive(MeasurementKind.OXYGEN, 31_000L))
        assertEquals(20_000L, windows.remainingMs(31_000L))
        assertEquals(0L, windows.remainingMs(51_000L))
    }

    @Test fun heartRateRequestAfterCompletedWindowStartsAnotherBoundedAttempt() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.HEART_RATE, 1000L, durationMs = 60_000L)
        windows.clear(MeasurementKind.HEART_RATE)
        windows.request(MeasurementKind.HEART_RATE, 31_000L, durationMs = 60_000L)
        assertEquals(60_000L, windows.remainingMs(MeasurementKind.HEART_RATE, 31_000L))
        assertFalse(windows.isActive(MeasurementKind.HEART_RATE, 91_000L))
    }

    @Test fun perSensorRemainingTimeDoesNotWaitForOverlappingOxygenWindow() {
        val windows = MeasurementWindows(30_000L)
        windows.request(MeasurementKind.HEART_RATE, 1000L)
        windows.request(MeasurementKind.OXYGEN, 21_000L)
        assertEquals(1L, windows.remainingMs(MeasurementKind.HEART_RATE, 30_999L))
        assertEquals(20_001L, windows.remainingMs(MeasurementKind.OXYGEN, 30_999L))
        assertEquals(0L, windows.remainingMs(MeasurementKind.HEART_RATE, 31_000L))
    }

    @Test fun noRegisteredWindowDoesNotNeedWakeLock() {
        val windows = MeasurementWindows(30_000L)
        assertEquals(0L, windows.remainingMs(1000L))
        windows.request(MeasurementKind.OXYGEN, 1000L)
        windows.clear(MeasurementKind.OXYGEN)
        assertEquals(0L, windows.remainingMs(1000L))
        assertFalse(windows.isActive(MeasurementKind.OXYGEN, 1000L))
    }
}

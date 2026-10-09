package com.healthsync.watch.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementScheduleTest {
    private val heartRate = MeasurementKind.HEART_RATE
    private val oxygen = MeasurementKind.OXYGEN

    @Test fun firstAutomaticRequestWaitsForTheChosenInterval() {
        listOf(30_000L, 60_000L, 300_000L, 600_000L, 1_800_000L, 3_600_000L).forEach { interval ->
            val schedule = MeasurementSchedule()
            schedule.configure(heartRate, interval, 1_000L)
            assertEquals(interval, schedule.dueDelayMs(heartRate, 1_000L))
            assertEquals(1L, schedule.dueDelayMs(heartRate, 999L + interval))
            assertEquals(0L, schedule.dueDelayMs(heartRate, 1_000L + interval))
        }
    }

    @Test fun repeatedSettingsProfileChangesAndReconnectsKeepTheOriginalDueTime() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 60_000L, 0L)
        schedule.configure(oxygen, 300_000L, 0L)
        listOf(2_000L, 5_000L, 20_000L, 59_000L).forEach { reconnectTime ->
            schedule.configure(heartRate, 60_000L, reconnectTime)
            schedule.configure(oxygen, 300_000L, reconnectTime)
        }
        assertEquals(1_000L, schedule.dueDelayMs(heartRate, 59_000L))
        assertEquals(241_000L, schedule.dueDelayMs(oxygen, 59_000L))
    }

    @Test fun changingOneIntervalDoesNotRestartTheOtherSensorSchedule() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 60_000L, 0L)
        schedule.configure(oxygen, 300_000L, 0L)
        schedule.configure(heartRate, 600_000L, 20_000L)
        assertEquals(600_000L, schedule.dueDelayMs(heartRate, 20_000L))
        assertEquals(280_000L, schedule.dueDelayMs(oxygen, 20_000L))
    }

    @Test fun manualMeasurementConsumesTheUpcomingAutomaticSlot() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 60_000L, 0L)
        schedule.markStarted(heartRate, 50_000L)
        schedule.markFinished(heartRate, 55_000L)
        assertEquals(50_000L, schedule.dueDelayMs(heartRate, 60_000L))
        assertEquals(0L, schedule.dueDelayMs(heartRate, 110_000L))
    }

    @Test fun quickReadingPreservesTheIntervalBetweenAcquisitionStarts() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 60_000L, 0L)
        schedule.markStarted(heartRate, 60_000L)
        schedule.markFinished(heartRate, 65_000L)
        assertEquals(55_000L, schedule.dueDelayMs(heartRate, 65_000L))
        assertEquals(0L, schedule.dueDelayMs(heartRate, 120_000L))
    }

    @Test fun thirtySecondIntervalLeavesSensorIdleAfterOneMinuteTimeout() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 30_000L, 0L)
        schedule.markStarted(heartRate, 30_000L)
        schedule.markFinished(heartRate, 90_000L)
        assertEquals(30_000L, schedule.dueDelayMs(heartRate, 90_000L))
        assertEquals(1L, schedule.dueDelayMs(heartRate, 119_999L))
    }

    @Test fun oneMinuteIntervalLeavesSensorIdleWhenTimeoutEqualsThePeriod() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 60_000L, 0L)
        schedule.markStarted(heartRate, 60_000L)
        schedule.markFinished(heartRate, 120_000L)
        assertEquals(60_000L, schedule.dueDelayMs(heartRate, 120_000L))
        assertEquals(1L, schedule.dueDelayMs(heartRate, 179_999L))
    }

    @Test fun delayedCleanupSkipsMissedSlotsWithoutRapidCatchUpRequests() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 30_000L, 0L)
        schedule.markStarted(heartRate, 30_000L)
        schedule.markFinished(heartRate, 300_000L)
        repeat(10) {
            schedule.configure(heartRate, 30_000L, 300_000L)
            schedule.markFinished(heartRate, 300_000L)
            assertEquals(30_000L, schedule.dueDelayMs(heartRate, 300_000L))
        }
    }

    @Test fun disablingOneSensorRemovesItsDueTimeAndCompletionCannotReenableIt() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 60_000L, 0L)
        schedule.configure(oxygen, 300_000L, 0L)
        schedule.markStarted(heartRate, 10_000L)
        schedule.configure(heartRate, -1L, 20_000L)
        schedule.markFinished(heartRate, 30_000L)
        schedule.markStarted(heartRate, 40_000L)
        assertFalse(schedule.isEnabled(heartRate))
        assertNull(schedule.intervalMs(heartRate))
        assertNull(schedule.dueDelayMs(heartRate, 40_000L))
        assertTrue(schedule.isEnabled(oxygen))
        assertEquals(260_000L, schedule.dueDelayMs(oxygen, 40_000L))
    }

    @Test fun reenableWaitsForAFullIntervalInsteadOfAnOldOverdueSlot() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 60_000L, 0L)
        schedule.configure(heartRate, 0L, 10_000L)
        schedule.configure(heartRate, 60_000L, 100_000L)
        assertEquals(60_000L, schedule.dueDelayMs(heartRate, 100_000L))
    }

    @Test fun positiveIntervalsAreBoundedAndEquivalentNormalizedSettingsPreserveDueTime() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 1L, 0L)
        schedule.configure(heartRate, 30_000L, 5_000L)
        schedule.configure(oxygen, Long.MAX_VALUE, 0L)
        assertEquals(30_000L, schedule.intervalMs(heartRate))
        assertEquals(25_000L, schedule.dueDelayMs(heartRate, 5_000L))
        assertEquals(3_600_000L, schedule.intervalMs(oxygen))
    }

    @Test fun deadlinesNearClockLimitSaturateWithoutWrappingToImmediateRequests() {
        val schedule = MeasurementSchedule()
        val now = Long.MAX_VALUE - 10_000L
        schedule.configure(heartRate, 60_000L, now)
        assertEquals(10_000L, schedule.dueDelayMs(heartRate, now))
        schedule.markStarted(heartRate, Long.MAX_VALUE - 5_000L)
        assertEquals(5_000L, schedule.dueDelayMs(heartRate, Long.MAX_VALUE - 5_000L))
        schedule.markFinished(heartRate, Long.MAX_VALUE)
        assertEquals(0L, schedule.dueDelayMs(heartRate, Long.MAX_VALUE))
    }
}

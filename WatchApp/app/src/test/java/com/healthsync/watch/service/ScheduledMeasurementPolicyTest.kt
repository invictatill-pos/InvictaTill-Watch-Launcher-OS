package com.healthsync.watch.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMeasurementPolicyTest {
    private val heartRate = MeasurementKind.HEART_RATE
    private val interval = 60_000L
    private val revision = 7L
    private val due = 120_000L

    private fun matches(alarmInterval: Long = interval, alarmRevision: Long = revision,
                        alarmDue: Long = due, currentInterval: Long = interval,
                        currentRevision: Long = revision, currentDue: Long = due,
                        now: Long = due) =
        ScheduledMeasurementPolicy.matches(alarmInterval, alarmRevision, alarmDue,
            currentInterval, currentRevision, currentDue, now)

    @Test fun delayedCurrentAlarmRemainsEligibleWithoutInventingCatchUpSlots() {
        assertTrue(matches())
        assertTrue(matches(now = due + 10L * interval))
    }

    @Test fun callbacksFromAnOldOrManuallyRebasedSlotAreRejected() {
        assertFalse(matches(currentDue = due + interval, now = due + interval))
        assertFalse(matches(alarmDue = due - interval))
        assertFalse(matches(currentDue = 0L))
    }

    @Test fun offAndInvalidIntervalsCannotAcquireFromAnAlarm() {
        for (disabledOrInvalid in listOf(-1L, 0L, 29_999L, 3_600_001L)) {
            assertFalse(matches(currentInterval = disabledOrInvalid))
            assertFalse(matches(alarmInterval = disabledOrInvalid))
            assertFalse(matches(alarmInterval = disabledOrInvalid, currentInterval = disabledOrInvalid))
        }
    }

    @Test fun settingsRevisionAndIntervalChangesRejectThePreviousAlarm() {
        assertFalse(matches(currentRevision = revision + 1L))
        assertFalse(matches(alarmRevision = revision + 1L))
        assertFalse(matches(currentInterval = 300_000L))
        assertFalse(matches(alarmInterval = 300_000L))
        assertFalse(matches(alarmRevision = -1L, currentRevision = -1L))
        assertTrue(matches(alarmRevision = 0L, currentRevision = 0L))
    }

    @Test fun earlyAndInvalidDueCallbacksAreRejected() {
        assertFalse(matches(now = due - 1L))
        assertFalse(matches(now = -1L))
        assertFalse(matches(alarmDue = 0L, currentDue = 0L))
        assertFalse(matches(alarmDue = -1L, currentDue = -1L))
    }

    @Test fun restoredOverdueSlotIsConsumedOnceInsteadOfReplayingMissedIntervals() {
        val schedule = MeasurementSchedule()
        val wakeTime = due + 10L * interval
        schedule.configure(heartRate, interval, wakeTime)
        assertTrue(schedule.restoreDue(heartRate, interval, due))
        assertEquals(due, schedule.dueAtMs(heartRate))
        assertEquals(0L, schedule.dueDelayMs(heartRate, wakeTime))
        assertTrue(matches(currentDue = schedule.dueAtMs(heartRate)!!, now = wakeTime))

        schedule.markStarted(heartRate, wakeTime)
        assertEquals(interval, schedule.dueDelayMs(heartRate, wakeTime))
        assertFalse(matches(currentDue = schedule.dueAtMs(heartRate)!!, now = wakeTime))
        schedule.markFinished(heartRate, wakeTime + 1_000L)
        assertEquals(interval - 1_000L, schedule.dueDelayMs(heartRate, wakeTime + 1_000L))
    }

    @Test fun restoredDueOnlyAppliesToTheCurrentEnabledInterval() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, interval, 0L)
        assertFalse(schedule.restoreDue(heartRate, 300_000L, due))
        assertFalse(schedule.restoreDue(heartRate, interval, 0L))
        assertFalse(schedule.restoreDue(heartRate, -1L, due))
        assertEquals(interval, schedule.dueAtMs(heartRate))

        schedule.configure(heartRate, -1L, 0L)
        assertFalse(schedule.restoreDue(heartRate, interval, due))
        assertFalse(schedule.isEnabled(heartRate))
    }

    @Test fun restoredIntervalUsesTheSameNormalizationAsConfiguration() {
        val schedule = MeasurementSchedule()
        schedule.configure(heartRate, 1L, 0L)
        assertTrue(schedule.restoreDue(heartRate, 30_000L, due))
        assertTrue(schedule.restoreDue(heartRate, 1L, due))
        assertEquals(due, schedule.dueAtMs(heartRate))
    }

    @Test fun timeoutAfterRestoredSlotSkipsMissedSlotsAndLeavesTheSensorIdle() {
        val schedule = MeasurementSchedule()
        val shortInterval = 30_000L
        schedule.configure(heartRate, shortInterval, 300_000L)
        assertTrue(schedule.restoreDue(heartRate, shortInterval, due))
        schedule.markStarted(heartRate, 300_000L)
        schedule.markFinished(heartRate, 360_000L)
        assertEquals(shortInterval, schedule.dueDelayMs(heartRate, 360_000L))
        assertEquals(390_000L, schedule.dueAtMs(heartRate))
        assertFalse(ScheduledMeasurementPolicy.matches(shortInterval, revision, due,
            shortInterval, revision, schedule.dueAtMs(heartRate)!!, 360_000L))
    }
}

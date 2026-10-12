package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class ActivityReminderPolicyTest {
    @Test fun absentSensorOrMissingMonitoringNeverImpliesInactivity() {
        assertFalse(shouldSendActivityReminder(10, 55, 0, 0L, true, -1))
        assertFalse(shouldSendActivityReminder(10, 55, 0, 45 * 60_000L, false, -1))
        assertEquals(0L, observedActivityMonitoringDelta(-1L, 1000L))
        assertEquals(0L, observedActivityMonitoringDelta(1000L, 3_000_000L))
        assertEquals(0L, observedActivityMonitoringDelta(1000L, 999L))
        assertEquals(20L, observedActivityMonitoringDelta(1000L, 1020L))
    }
    @Test fun reminderRequiresObservedHourAndFewActualStepsAndRunsOnlyOnce() {
        assertTrue(shouldSendActivityReminder(10, 55, 0, 45 * 60_000L, true, -1))
        assertFalse(shouldSendActivityReminder(10, 55, 100, 45 * 60_000L, true, -1))
        assertFalse(shouldSendActivityReminder(10, 55, 0, 45 * 60_000L, true, 10))
        assertFalse(shouldSendActivityReminder(2, 55, 0, 45 * 60_000L, true, -1))
    }
}

package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class SensorCandidateRetryPolicyTest {
    @Test fun stalledDefaultAdvancesWithinMeasurementBudget() {
        assertFalse(SensorCandidateRetryPolicy.shouldAdvance(19_999, 0, 60_000, 2, true))
        assertTrue(SensorCandidateRetryPolicy.shouldAdvance(20_000, 0, 60_000, 2, true))
        assertEquals(10_000L, SensorCandidateRetryPolicy.candidateWindowMs(30_000, 3))
        assertFalse(SensorCandidateRetryPolicy.shouldAdvance(60_000, 0, 60_000, 1, false))
    }
    @Test fun clockRollbackCannotAdvanceCandidate() {
        assertFalse(SensorCandidateRetryPolicy.shouldAdvance(5_000, 10_000, 60_000, 2, true))
    }
}

package com.healthsync.phone.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCountMergePolicyTest {
    private val saved = StepCountObservation(1234, 1_000_000L)

    @Test fun watchRestartBaselineDoesNotRefreshTheRetainedHistoricalCount() {
        assertEquals(saved, StepCountMergePolicy.merge(saved, StepCountObservation(0, 2_000_000L)))
        assertEquals(saved, StepCountMergePolicy.merge(saved, StepCountObservation(125, 3_000_000L)))
    }

    @Test fun delayedHigherCountRetainsItsActualEarlierCaptureTime() {
        val delayed = StepCountObservation(1500, 900_000L)
        assertEquals(delayed, StepCountMergePolicy.merge(saved, delayed))
        assertEquals(delayed, StepCountMergePolicy.merge(delayed, StepCountObservation(0, 2_000_000L)))
    }

    @Test fun equalCountRefreshesOnlyWithANewerObservation() {
        assertEquals(saved, StepCountMergePolicy.merge(saved, StepCountObservation(1234, 900_000L)))
        val refreshed = StepCountObservation(1234, 2_000_000L)
        assertEquals(refreshed, StepCountMergePolicy.merge(saved, refreshed))
        assertEquals(refreshed, StepCountMergePolicy.merge(refreshed, saved))
    }

    @Test fun capturedZeroAndNewHigherCountKeepTheirOwnObservationTimes() {
        val zero = StepCountObservation(0, 1_000_000L)
        assertEquals(zero, StepCountMergePolicy.merge(null, zero))
        val captured = StepCountObservation(1250, 2_000_000L)
        assertEquals(captured, StepCountMergePolicy.merge(saved, captured))
    }
}

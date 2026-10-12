package com.healthsync.watch.ui

import org.junit.Assert.*
import org.junit.Test

class WorkoutMeasurementLabelsTest {
    @Test fun unavailableDistanceDoesNotBecomeAZeroMeasuredDistance() {
        assertEquals("— km", WorkoutMeasurementLabels.distanceValue(0f, "UNAVAILABLE"))
        assertEquals("— km", WorkoutMeasurementLabels.distanceValue(Float.NaN, "LOCATION"))
        assertEquals("— km", WorkoutMeasurementLabels.distanceValue(-1f, "LOCATION"))
    }
    @Test fun estimatesAndUnknownHistoryStayClearlyIdentified() {
        assertTrue(WorkoutMeasurementLabels.distanceValue(1_000f, "STEP_ESTIMATE").endsWith(" est."))
        assertTrue(WorkoutMeasurementLabels.distanceValue(1_000f, "LOCATION_AND_STEP_ESTIMATE").endsWith(" est."))
        assertFalse(WorkoutMeasurementLabels.distanceValue(1_000f, "LOCATION").endsWith(" est."))
        assertEquals("Distance source unknown", WorkoutMeasurementLabels.distanceSource(null))
        assertEquals("Distance source unknown", WorkoutMeasurementLabels.distanceSource("vendor"))
    }
}

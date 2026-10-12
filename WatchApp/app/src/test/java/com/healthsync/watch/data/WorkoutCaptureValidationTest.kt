package com.healthsync.watch.data

import com.healthsync.watch.algorithm.workoutDistanceSource
import org.junit.Assert.*
import org.junit.Test

class WorkoutCaptureValidationTest {
    private fun session() = WorkoutSessionPayload("W_real", "WALK", 1_000_000L, 1_060_000L,
        60, 100f, 3.0, 150, 0, 80, "[]", "LOCATION", true)

    @Test fun rejectsCorruptVitalsChronologyAndUnknownMetricProvenance() {
        assertTrue(validCapturedWorkout(session()))
        assertFalse(validCapturedWorkout(session().copy(avg_hr = 400)))
        assertFalse(validCapturedWorkout(session().copy(distance_m = Float.NaN)))
        assertFalse(validCapturedWorkout(session().copy(calories = Double.POSITIVE_INFINITY)))
        assertFalse(validCapturedWorkout(session().copy(step_count = -1)))
        assertFalse(validCapturedWorkout(session().copy(end_time = 999_999L)))
        assertFalse(validCapturedWorkout(session().copy(duration_sec = 120)))
        assertFalse(validCapturedWorkout(session().copy(distance_source = "SYNTHETIC")))
        assertTrue(validCapturedWorkout(session().copy(avg_hr = 0)))
    }
    @Test fun distanceProvenanceNeverPresentsStepEstimateAsLocationCapture() {
        assertEquals("UNAVAILABLE", workoutDistanceSource(true, false, false))
        assertEquals("STEP_ESTIMATE", workoutDistanceSource(true, false, true))
        assertEquals("LOCATION_AND_STEP_ESTIMATE", workoutDistanceSource(true, true, true))
        assertEquals("LOCATION", workoutDistanceSource(true, true, false))
        assertEquals("NOT_APPLICABLE", workoutDistanceSource(false, false, false))
    }
}

package com.healthsync.watch.algorithm

import com.healthsync.watch.data.LatLngPoint
import org.junit.Assert.*
import org.junit.Test

class WorkoutLocationRulesTest {
    @Test fun everyOutdoorWorkoutUsesTheSamePermissionAndStatusRules() {
        listOf("Walk", "Run", "Cycling", "Basketball", "Cricket").forEach { assertTrue(workoutUsesLocation(it)) }
        listOf("Yoga", "Badminton", "Home Workout").forEach { assertFalse(workoutUsesLocation(it)) }
        assertTrue(workoutUsesStepDistance("Run"))
        assertFalse(workoutUsesStepDistance("Cycling"))
    }

    @Test fun disabledAndDeniedProvidersProduceActionableStatesInsteadOfEndlessSearching() {
        assertEquals(WorkoutLocationState.PERMISSION_REQUIRED, state(fine = false, coarse = false))
        assertEquals(WorkoutLocationState.PRECISE_REQUIRED, state(fine = false))
        assertEquals(WorkoutLocationState.NO_PROVIDER, state(available = emptySet(), enabled = emptySet()))
        assertEquals(WorkoutLocationState.DISABLED, state(enabled = emptySet()))
        assertEquals(WorkoutLocationState.GPS_DISABLED, state(available = setOf("gps", "network"), enabled = setOf("network"), registered = setOf("gps", "network")))
        assertEquals(WorkoutLocationState.UNAVAILABLE, state(registered = emptySet()))
        assertEquals(WorkoutLocationState.PAUSED, state(paused = true))
        assertEquals(WorkoutLocationState.SEARCHING, state(fix = -1))
    }

    @Test fun networkFixNeverClaimsGpsLockAndAnOldFixBecomesStale() {
        assertEquals(WorkoutLocationState.GPS_FIX, state(fix = 9_000, now = 10_000))
        assertEquals(WorkoutLocationState.LOCATION_FIX, state(fix = 9_000, now = 10_000, provider = "network"))
        assertEquals(WorkoutLocationState.STALE, state(fix = 9_000, now = 39_001))
        assertEquals(WorkoutLocationState.STALE, state(fix = 10_001, now = 10_000))
    }

    @Test fun staleUnknownAccuracyAndInvalidCoordinatesNeverEnterRoutes() {
        assertTrue(validWorkoutLocation(12.0, 77.0, 8f, 9_000, 10_000))
        assertFalse(validWorkoutLocation(12.0, 77.0, null, 9_000, 10_000))
        assertFalse(validWorkoutLocation(12.0, 77.0, 200f, 9_000, 10_000))
        assertFalse(validWorkoutLocation(12.0, 77.0, Float.NaN, 9_000, 10_000))
        assertFalse(validWorkoutLocation(91.0, 77.0, 8f, 9_000, 10_000))
        assertFalse(validWorkoutLocation(12.0, Double.NaN, 8f, 9_000, 10_000))
        assertFalse(validWorkoutLocation(12.0, 77.0, 8f, 9_000, 39_001))
    }

    @Test fun duplicateOutOfOrderAndImpossibleJumpsDoNotAddDistance() {
        assertEquals(RouteSegmentDecision.REJECT, workoutRouteSegmentDecision(0.0, 10f, 8f, 8f, false))
        assertEquals(RouteSegmentDecision.REJECT, workoutRouteSegmentDecision(-1.0, 10f, 8f, 8f, false))
        assertEquals(RouteSegmentDecision.REJECT, workoutRouteSegmentDecision(1.0, 200f, 8f, 8f, true))
        assertEquals(RouteSegmentDecision.STATIONARY, workoutRouteSegmentDecision(1.0, .8f, 20f, 20f, false))
        assertEquals(RouteSegmentDecision.MOVE, workoutRouteSegmentDecision(5.0, 10f, 8f, 8f, false))
        assertEquals(RouteSegmentDecision.ORIGIN, workoutRouteSegmentDecision(31.0, 200f, 8f, 8f, false))
    }

    @Test fun thinningPreservesRouteEndpointsAndEveryBreakWithoutJoiningMissingTrack() {
        val points = (0..5000).map { LatLngPoint(12.0, 77.0 + it / 100_000.0, it * 1000L, segmentStart = it == 0 || it == 101 || it == 3003) }
        val thinned = thinWorkoutRoute(points)
        assertTrue(thinned.size < points.size)
        assertEquals(points.first(), thinned.first())
        assertEquals(points.last(), thinned.last())
        points.filter { it.segmentStart }.forEach { assertTrue(thinned.contains(it)) }
        assertTrue(thinned.contains(points[100]))
        assertTrue(thinned.contains(points[3002]))
    }

    @Test fun cachedPreResumeFutureAndOutOfOrderTimestampsCannotBecomeFreshOrigins() {
        assertNull(workoutFixElapsedTime(0, 20_001, 10_000, 20_000))
        assertNull(workoutFixElapsedTime(-1, 20_000, 10_000, 20_000))
        assertNull(workoutFixElapsedTime(0, 0, 10_000, 20_000))
        assertEquals(9_000L, workoutFixElapsedTime(0, 19_000, 10_000, 20_000))
        assertFalse(validWorkoutLocation(12.0, 77.0, 8f, 10_001, 10_000))
        assertFalse(validWorkoutLocation(12.0, 77.0, 8f, 0, 10_000))
        assertFalse(validWorkoutFixOrder(9_000, registeredAt = 9_001, previousAccepted = -1))
        assertFalse(validWorkoutFixOrder(9_001, registeredAt = 9_000, previousAccepted = 9_001))
        assertFalse(validWorkoutFixOrder(9_000, registeredAt = 8_000, previousAccepted = 9_001))
        assertTrue(validWorkoutFixOrder(9_002, registeredAt = 9_000, previousAccepted = 9_001))
        assertEquals(19_000L, workoutRouteWallTime(9_000, 10_000, 20_000))
        assertNull(workoutRouteWallTime(10_001, 10_000, 20_000))
        assertNull(workoutRouteWallTime(9_000, 10_000, 1_000))
        assertEquals(RouteSegmentDecision.REJECT, workoutRouteSegmentDecision(1.0, 2f, Float.NaN, 8f, false))
        assertEquals(RouteSegmentDecision.REJECT, workoutRouteSegmentDecision(1.0, 2f, 8f, -1f, false))
    }

    @Test fun discardedNewTrackedFixBreaksDistanceContinuityButSecondaryOrOldDataDoesNot() {
        assertTrue(shouldBreakDiscardedWorkoutRoute("gps", "gps", 10_001, 10_000))
        assertTrue(shouldBreakDiscardedWorkoutRoute("gps", "gps", null, 10_000))
        assertFalse(shouldBreakDiscardedWorkoutRoute("network", "gps", 10_001, 10_000))
        assertFalse(shouldBreakDiscardedWorkoutRoute("gps", "gps", 9_999, 10_000))
        assertFalse(shouldBreakDiscardedWorkoutRoute("gps", "gps", 10_000, 10_000))
        assertFalse(shouldBreakDiscardedWorkoutRoute("gps", null, 10_001, 10_000))
    }

    @Test fun motionSamplesRespectHardwareRangeAndRegistrationTimeWithoutRejectingLegitimateImpact() {
        assertTrue(validWorkoutMotionSample(floatArrayOf(150f, 0f, 9.81f), 200f, 9_000, 10_000, 8_000, 8_500))
        assertFalse(validWorkoutMotionSample(floatArrayOf(201f, 0f, 9.81f), 200f, 9_000, 10_000, 8_000, 8_500))
        assertFalse(validWorkoutMotionSample(floatArrayOf(Float.MAX_VALUE, 0f, 0f), 200f, 9_000, 10_000, 8_000, 8_500))
        assertFalse(validWorkoutMotionSample(floatArrayOf(Float.NaN, 0f, 0f), 200f, 9_000, 10_000, 8_000, 8_500))
        assertFalse(validWorkoutMotionSample(floatArrayOf(0f, 0f, 9.81f), 200f, 10_001, 10_000, 8_000, 8_500))
        assertFalse(validWorkoutMotionSample(floatArrayOf(0f, 0f, 9.81f), 200f, 8_000, 10_000, 8_000, 8_500))
        assertFalse(validWorkoutMotionSample(floatArrayOf(0f, 0f, 9.81f), 200f, 9_000, 15_001, 8_000, 8_500))
    }

    @Test fun missingOrCorruptRangeMetadataCannotAllowSquaredMagnitudeOverflow() {
        val corrupt = floatArrayOf(Float.MAX_VALUE, 0f, 0f)
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE).forEach { range ->
            assertFalse(validWorkoutMotionSample(corrupt, range, 9_000, 10_000, 8_000, 8_500))
        }
        // Each square can be finite while their sum overflows a Float.
        assertFalse(validWorkoutMotionSample(floatArrayOf(1.5e19f, 1.5e19f, 0f), Float.MAX_VALUE, 9_000, 10_000, 8_000, 8_500))
        assertTrue(validWorkoutMotionSample(floatArrayOf(150f, 0f, 9.81f), Float.NaN, 9_000, 10_000, 8_000, 8_500))
    }

    private fun state(fine: Boolean = true, coarse: Boolean = true, paused: Boolean = false,
        available: Set<String> = setOf("gps"), enabled: Set<String> = setOf("gps"), registered: Set<String> = setOf("gps"),
        fix: Long = -1, now: Long = 10_000, provider: String = "gps") =
        workoutLocationState(true, paused, fine, coarse, available, enabled, registered, fix, now, provider)
}

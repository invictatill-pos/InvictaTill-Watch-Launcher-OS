package com.healthsync.phone.data

import com.healthsync.phone.data.model.LatLngPoint
import com.healthsync.phone.ui.workout.calculateCapturedSplits
import org.junit.Assert.*
import org.junit.Test

class WorkoutAnalyticsTest {
    private fun kilometreSegment(startTime: Long = 1_000): List<LatLngPoint> =
        (0..11).map { index -> LatLngPoint(0.0, index * 0.0009, startTime + index * 30_000) }

    @Test fun aSplitInterpolatesOnlyTheRecordedBoundary() {
        val split = calculateCapturedSplits(kilometreSegment()).single()
        assertEquals(1, split.kmNumber)
        assertEquals(300L, split.paceSecondsPerKm) // About 100.075 m every 30 seconds.
    }

    @Test fun routeGapsCannotBecomeRecordedDistanceOrTime() {
        val before = kilometreSegment().take(6)
        val after = kilometreSegment(1_000_000).take(6).map { it.copy(lng = it.lng + 0.1) }
        assertTrue(calculateCapturedSplits(before + after).isEmpty())
        val resumed = before + after.mapIndexed { i, p -> p.copy(timestamp = 181_000L + i * 30_000L, segmentStart = i == 0) }
        assertTrue(calculateCapturedSplits(resumed).isEmpty())
    }

    @Test fun summariesNeverCreateSplitsWithoutLocationSamples() {
        assertTrue(calculateCapturedSplits(emptyList()).isEmpty())
        assertTrue(calculateCapturedSplits(listOf(LatLngPoint(0.0, 0.0, 1_000))).isEmpty())
    }
}

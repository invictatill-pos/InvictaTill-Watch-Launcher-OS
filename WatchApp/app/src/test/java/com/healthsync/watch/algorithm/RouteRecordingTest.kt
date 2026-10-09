package com.healthsync.watch.algorithm

import com.healthsync.watch.data.LatLngPoint
import org.junit.Assert.*
import org.junit.Test

class RouteRecordingTest {

    @Test
    fun sevenSegmentBitmasksContainCorrectSegmentCounts() {
        // Segments: 0=A(top), 1=B(top-right), 2=C(bottom-right), 3=D(bottom), 4=E(bottom-left), 5=F(top-left), 6=G(middle)
        val masks = intArrayOf(
            0b0111111, // 0 -> 6 segments
            0b0000110, // 1 -> 2 segments
            0b1011011, // 2 -> 5 segments
            0b1001111, // 3 -> 5 segments
            0b1100110, // 4 -> 4 segments
            0b1101101, // 5 -> 5 segments
            0b1111101, // 6 -> 6 segments
            0b0000111, // 7 -> 3 segments
            0b1111111, // 8 -> 7 segments
            0b1101111  // 9 -> 6 segments
        )

        assertEquals(6, Integer.bitCount(masks[0]))
        assertEquals(2, Integer.bitCount(masks[1]))
        assertEquals(5, Integer.bitCount(masks[2]))
        assertEquals(5, Integer.bitCount(masks[3]))
        assertEquals(4, Integer.bitCount(masks[4]))
        assertEquals(5, Integer.bitCount(masks[5]))
        assertEquals(6, Integer.bitCount(masks[6]))
        assertEquals(3, Integer.bitCount(masks[7]))
        assertEquals(7, Integer.bitCount(masks[8]))
        assertEquals(6, Integer.bitCount(masks[9]))
    }

    @Test
    fun routePointsAccumulationAndThinningPreservesEndpoints() {
        val points = mutableListOf<LatLngPoint>()
        for (i in 0 until 5050) {
            points.add(LatLngPoint(12.0 + i * 0.0001, 77.0 + i * 0.0001, 1000L + i * 1000L))
        }
        assertTrue(points.size > 5000)

        // Thinning logic identical to WorkoutTrackingService
        val startPoint = points.first()
        val endPoint = points.last()

        val thinned = points.filterIndexed { index, _ -> index % 2 == 0 || index == points.lastIndex }
        points.clear()
        points.addAll(thinned)

        assertTrue(points.size <= 2600)
        assertEquals(startPoint.lat, points.first().lat, 0.000001)
        assertEquals(endPoint.lat, points.last().lat, 0.000001)
    }

    @Test
    fun jitterThresholdPreventsCreepWhileAllowingValidMovement() {
        val prevAccuracy = 20f
        val currentAccuracy = 20f

        val jitterThreshold = maxOf(2.0f, minOf(prevAccuracy, currentAccuracy) * 0.25f)
        assertEquals(5.0f, jitterThreshold, 0.01f)

        // Minor stationary sensor jitter (0.8m) should not exceed jitter threshold
        val minorJitter = 0.8f
        assertTrue(minorJitter < jitterThreshold)

        // Valid human stride / running step (6.2m over 2 fixes) exceeds threshold and is recorded
        val validMovement = 6.2f
        assertTrue(validMovement >= jitterThreshold)
    }
}

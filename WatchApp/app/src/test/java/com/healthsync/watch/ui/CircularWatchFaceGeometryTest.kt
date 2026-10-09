package com.healthsync.watch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircularWatchFaceGeometryTest {
    @Test fun circleAlwaysUsesSmallerDisplayDimension() {
        assertEquals(120f, CircularWatchFaceGeometry.radius(240f, 320f), 0f)
        assertEquals(120f, CircularWatchFaceGeometry.radius(320f, 240f), 0f)
        assertEquals(200f, CircularWatchFaceGeometry.radius(400f, 400f), 0f)
        assertEquals(0f, CircularWatchFaceGeometry.radius(-1f, 400f), 0f)
    }

    @Test fun rowWidthAccountsForTheWholeTextBandAndInset() {
        val halfWidth = CircularWatchFaceGeometry.halfWidthForBand(120f, -80f, -65f, 9f)
        assertTrue(halfWidth > 0f)
        // Both top corners and both bottom corners stay within the inset circle.
        for (y in listOf(-80f, -65f)) {
            assertTrue(halfWidth * halfWidth + y * y <= 111f * 111f + 0.01f)
        }
        assertEquals(halfWidth,
            CircularWatchFaceGeometry.halfWidthForBand(120f, 65f, 80f, 9f), 0.001f)
    }

    @Test fun rowsOutsideTheCircleHaveNoTextSpace() {
        assertEquals(0f, CircularWatchFaceGeometry.halfWidthForBand(100f, 95f, 105f), 0f)
        assertEquals(0f, CircularWatchFaceGeometry.halfWidthForBand(100f, 0f, 20f, 150f), 0f)
    }
}

package com.healthsync.watch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherGesturePolicyTest {
    @Test fun allFourDirectionsRequireDeliberateTravel() {
        assertEquals(LauncherGesturePolicy.Direction.UP, LauncherGesturePolicy.direction(2f, -60f, 40f, 250))
        assertEquals(LauncherGesturePolicy.Direction.DOWN, LauncherGesturePolicy.direction(2f, 60f, 40f, 250))
        assertEquals(LauncherGesturePolicy.Direction.LEFT, LauncherGesturePolicy.direction(-60f, 2f, 40f, 250))
        assertEquals(LauncherGesturePolicy.Direction.RIGHT, LauncherGesturePolicy.direction(60f, 2f, 40f, 250))
    }

    @Test fun accidentalDragsAndHoldsDoNotLaunchScreens() {
        assertNull(LauncherGesturePolicy.direction(30f, 2f, 40f, 250))
        assertNull(LauncherGesturePolicy.direction(60f, 60f, 40f, 250))
        assertNull(LauncherGesturePolicy.direction(0f, 80f, 40f, 1_201))
        assertNull(LauncherGesturePolicy.direction(Float.NaN, 80f, 40f, 250))
        assertNull(LauncherGesturePolicy.direction(0f, 80f, 0f, 250))
        assertNull(LauncherGesturePolicy.direction(0f, 80f, 40f, -1))
    }
}

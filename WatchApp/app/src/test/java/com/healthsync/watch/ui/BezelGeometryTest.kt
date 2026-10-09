package com.healthsync.watch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BezelGeometryTest {
    @Test fun apertureFitsBothPortraitAndLandscape() {
        assertEquals(200f, BezelGeometry.radius(400, 600, 100), 0f)
        assertEquals(200f, BezelGeometry.radius(600, 400, 100), 0f)
        assertEquals(170f, BezelGeometry.radius(400, 400, 85), 0f)
        assertEquals(200f, BezelGeometry.radius(400, 400, 110), 0f)
        assertEquals(0f, BezelGeometry.radius(-1, 400, 100), 0f)
    }
    @Test fun newerAndroidNeverGetsAnOpaqueTouchBlockingWindow() {
        assertEquals(1f, BezelGeometry.windowAlpha(26, .8f), 0f)
        assertEquals(.8f, BezelGeometry.windowAlpha(31, 1f), 0f)
        assertEquals(.6f, BezelGeometry.windowAlpha(34, .6f), 0f)
        assertEquals(.8f, BezelGeometry.windowAlpha(31, Float.NaN), 0f)
    }
}

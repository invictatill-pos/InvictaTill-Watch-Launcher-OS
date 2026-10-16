package com.healthsync.watch.ui.navigation

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PhoneMapLayoutTest {
    @Test fun controlsAndReadableMessageBoundsRemainInsideTinyRoundDisplays() {
        for (size in listOf(240, 320, 400)) {
            for (desiredTouch in listOf(44, 66, 88)) {
                val layout = phoneMapLayout(size, size, desiredTouch)
                val bounds = listOf(layout.toolbar, layout.actions, layout.status,
                    layout.quietStatus, layout.message, layout.help)
                for (b in bounds) {
                    for (x in listOf(b.left, b.right)) for (y in listOf(b.top, b.bottom)) {
                        val distance = hypot(x - size / 2.0, y - size / 2.0)
                        assertTrue("$size px corner ($x,$y) is outside the circle", distance <= size / 2.0)
                    }
                }
                assertTrue(layout.toolbar.top >= size * .115f)
                assertTrue(layout.actions.bottom <= size * .85f)
                assertTrue(layout.toolbar.height <= desiredTouch)
                assertTrue(layout.actions.width <= size * .645f)
            }
        }
    }

    @Test fun fortyFourPixelTargetsAreRetainedWhenTheyFitSmallWatchGeometry() {
        for (size in listOf(320, 400)) {
            val layout = phoneMapLayout(size, size, 44)
            assertEquals(44, layout.toolbar.height)
            assertEquals(44, layout.actions.height)
        }
    }

    @Test fun fullFrameFitIncludesAllPhoneCornersAndKeepsTheRealAspectRatio() {
        for (size in listOf(240, 320, 400)) {
            for ((imageWidth, imageHeight) in listOf(320 to 640, 640 to 320, 400 to 400)) {
                val scale = circlePhoneFrameScale(size, size, imageWidth, imageHeight)
                val drawnWidth = imageWidth * scale
                val drawnHeight = imageHeight * scale
                assertTrue(hypot(drawnWidth / 2.0, drawnHeight / 2.0) <= size / 2.0 + .001)
                assertTrue(abs(drawnWidth / drawnHeight - imageWidth.toFloat() / imageHeight) < .0001f)
            }
        }
    }

    @Test fun invalidViewportCannotProduceANonFiniteDrawingScale() {
        assertEquals(0f, circlePhoneFrameScale(0, 320, 320, 640), 0f)
        assertEquals(0f, circlePhoneFrameScale(320, 0, 320, 640), 0f)
        assertEquals(0f, circlePhoneFrameScale(320, 320, 0, 640), 0f)
        assertEquals(0f, circlePhoneFrameScale(320, 320, 320, -1), 0f)
    }
}

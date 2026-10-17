package com.healthsync.watch.ui.navigation

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class NavigationLayoutTest {
    @Test fun scrollViewportAndControlsFitInsideRoundDisplaysAtAllTargetSizes() {
        for ((width, height) in listOf(240 to 240, 320 to 320, 400 to 400, 320 to 400, 400 to 320)) {
            for (touch in listOf(44, 66, 88)) {
                val layout = navigationLayout(width, height, touch)
                val radius = minOf(width, height) / 2.0
                for (rect in listOf(layout.toolbar, layout.content)) {
                    assertTrue(rect.height > 0)
                    for (x in listOf(rect.left, rect.right)) for (y in listOf(rect.top, rect.bottom))
                        assertTrue("$width x $height corner $x, $y", hypot(x - width / 2.0, y - height / 2.0) <= radius)
                }
                assertTrue(layout.toolbar.bottom < layout.content.top)
                assertTrue(layout.content.height > minOf(width, height) * .5)
            }
        }
    }

    @Test fun unknownManeuversNeverShowAnInventedDirection() {
        assertEquals("?", navigationManeuverGlyph("UNKNOWN"))
        assertEquals("?", navigationManeuverGlyph("unsupported"))
        assertTrue(navigationManeuverDescription("UNKNOWN").contains("unavailable"))
        assertEquals("←", navigationManeuverGlyph("LEFT"))
        assertEquals("→", navigationManeuverGlyph("RIGHT"))
    }
}

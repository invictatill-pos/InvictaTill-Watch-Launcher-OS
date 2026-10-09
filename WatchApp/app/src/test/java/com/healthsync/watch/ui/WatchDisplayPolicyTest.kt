package com.healthsync.watch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDisplayPolicyTest {
    @Test fun clockTicksAlignToNextMinuteAndNeverSpinAtBoundary() {
        assertEquals(1L, WatchDisplayPolicy.nextTickDelay(119_999L, ambient = true))
        assertEquals(60_000L, WatchDisplayPolicy.nextTickDelay(120_000L, ambient = true))
        assertEquals(59_999L, WatchDisplayPolicy.nextTickDelay(120_001L, ambient = true))
        assertEquals(1L, WatchDisplayPolicy.nextTickDelay(120_999L, ambient = false))
        assertEquals(1_000L, WatchDisplayPolicy.nextTickDelay(121_000L, ambient = false))
    }

    @Test fun dimRequiresAnOptInAndVisibleInteractiveWindow() {
        for (enabled in listOf(false, true)) {
            for (resumed in listOf(false, true)) {
                for (focused in listOf(false, true)) {
                    assertEquals(enabled && resumed && focused,
                        WatchDisplayPolicy.shouldDim(enabled, resumed, focused))
                }
            }
        }
        assertFalse(WatchDisplayPolicy.shouldDim(false, true, true))
        assertTrue(WatchDisplayPolicy.shouldDim(true, true, true))
    }

    @Test fun burnInShiftMovesThroughNineBoundedPositionsAndRepeats() {
        val positions = (0L..8L).map { WatchDisplayPolicy.burnInOffset(it * 60_000L) }
        assertEquals(9, positions.toSet().size)
        assertTrue(positions.all { (x, y) -> x in -3..3 && y in -3..3 })
        assertEquals(positions.first(), WatchDisplayPolicy.burnInOffset(9L * 60_000L))
        assertEquals(positions[2], WatchDisplayPolicy.burnInOffset(179_999L))
    }
}

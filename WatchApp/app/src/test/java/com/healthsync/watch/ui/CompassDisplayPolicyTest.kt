package com.healthsync.watch.ui

import org.junit.Assert.*
import org.junit.Test

class CompassDisplayPolicyTest {
    @Test fun firstSampleIsNotBiasedTowardAnInventedNorthReading() {
        assertEquals(180f, CompassDisplayPolicy.smooth(null, 180f)!!, .001f)
        assertNull(CompassDisplayPolicy.smooth(null, Float.NaN))
        assertEquals(270f, CompassDisplayPolicy.smooth(null, -90f)!!, .001f)
    }
    @Test fun wraparoundTakesTheShortPathAcrossNorth() {
        assertEquals(359.5f, CompassDisplayPolicy.smooth(359f, 1f)!!, .001f)
        assertEquals("N", CompassDisplayPolicy.cardinal(359f))
        assertEquals("S", CompassDisplayPolicy.cardinal(180f))
        assertEquals("NW", CompassDisplayPolicy.cardinal(315f))
    }
    @Test fun staleFutureMissingAndMockLocationsCannotSupplyAltitude() {
        assertTrue(CompassDisplayPolicy.locationIsRecent(1_000, 121_000, false))
        assertFalse(CompassDisplayPolicy.locationIsRecent(1_000, 121_001, false))
        assertFalse(CompassDisplayPolicy.locationIsRecent(0, 1_000, false))
        assertFalse(CompassDisplayPolicy.locationIsRecent(2_000, 1_000, false))
        assertFalse(CompassDisplayPolicy.locationIsRecent(1_000, 1_000, true))
    }
}

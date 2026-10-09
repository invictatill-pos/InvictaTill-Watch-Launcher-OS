package com.healthsync.watch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFaceCatalogTest {
    @Test fun curatedFacesAreSelectableAndBothAreNative() {
        assertEquals(2, WatchFaceCatalog.styles.size)
        assertEquals(WatchFaceCatalog.styles.size, WatchFaceCatalog.styles.toSet().size)
        assertTrue(WatchFaceCatalog.isNative("orbit"))
        assertTrue(WatchFaceCatalog.isNative("classic"))
        assertFalse(WatchFaceCatalog.isNative("unknown"))
        assertEquals("orbit", WatchFaceCatalog.entry("unknown").id)
    }

    @Test fun everyFaceHasAConcreteSupportedAmbientComposition() {
        assertEquals(3, WatchFaceCatalog.ambientStyles.size)
        for (face in WatchFaceCatalog.styles) {
            val ambient = WatchFaceCatalog.ambientStyleForFace(face)
            assertTrue(ambient in WatchFaceCatalog.ambientStyles)
            assertFalse(ambient == "face")
        }
        assertEquals("retro", WatchFaceCatalog.ambientStyleForFace("classic"))
        assertEquals("digital", WatchFaceCatalog.ambientStyleForFace("orbit"))
    }

    @Test fun unsupportedSavedAmbientStylesRecoverToMatchingTheFace() {
        assertEquals("face", WatchFaceCatalog.normalizeAmbientStyle("removed-style"))
        for (style in WatchFaceCatalog.ambientStyles) {
            assertEquals(style, WatchFaceCatalog.normalizeAmbientStyle(style))
        }
    }

    @Test fun clockShortcutsCanBeToggledForCuratedFaces() {
        for (style in WatchFaceCatalog.styles) {
            assertFalse(WatchFaceCatalog.showsClockShortcuts(style, enabled = false))
            assertTrue(WatchFaceCatalog.showsClockShortcuts(style, enabled = true))
        }
    }
}

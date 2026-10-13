package com.healthsync.phone.data

import com.healthsync.phone.data.watchface.FaceLayer
import com.healthsync.phone.data.watchface.FaceStudioDocument
import com.healthsync.phone.data.watchface.watchSupportsStudioLayers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioTransferCompatibilityTest {
    @Test fun existingFacesRemainCompatibleWithEarlierWatches() {
        val face = FaceStudioDocument.blank("digital")
        assertTrue(watchSupportsStudioLayers(face, null))
        assertTrue(watchSupportsStudioLayers(face, 33))
    }

    @Test fun layeredFacesRequireAWatchThatCanRenderThem() {
        val face = FaceStudioDocument.blank().copy(layers = listOf(FaceLayer(id = "label")))
        assertFalse(watchSupportsStudioLayers(face, null))
        assertFalse(watchSupportsStudioLayers(face, 33))
        assertTrue(watchSupportsStudioLayers(face, 34))
        assertTrue(watchSupportsStudioLayers(face, 35))
    }
}

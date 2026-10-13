package com.healthsync.watch.ui

import com.google.gson.Gson
import com.healthsync.watch.data.watchface.BackgroundConfig
import com.healthsync.watch.data.watchface.FaceLayer
import com.healthsync.watch.data.watchface.HswfPackage
import com.healthsync.watch.data.watchface.MAX_FACE_LAYERS
import org.junit.Assert.*
import org.junit.Test

class StudioLayerSchemaTest {
    private val gson = Gson()

    @Test
    fun legacyPackagesKeepConstructorDefaultsWhenLayersAreAbsent() {
        val legacy = gson.fromJson(
            """{"id":"legacy","name":"Legacy face","background":{"type":"solid","colorHex":"#010203"}}""",
            HswfPackage::class.java
        )

        assertTrue(legacy.layers.isEmpty())
        assertEquals(0.25f, legacy.background.imageDimAmount, 0f)
        assertEquals(45f, legacy.background.gradientAngle, 0f)
        assertTrue(legacy.dial.showTicks)
        assertEquals(60, legacy.dial.tickCount)
        assertNotNull(legacy.hands)
        assertNotNull(legacy.complications)
        assertTrue(legacy.aod.hideSecondsHand)
    }

    @Test
    fun partialLayersRetainVisibleArtworkAndNonZeroOpacity() {
        val face = gson.fromJson(
            """{"id":"partial","name":"Partial","layers":[{"type":"text","text":"MADE BY ME"}]}""",
            HswfPackage::class.java
        )
        val layer = face.layers.single()

        assertTrue(layer.id.isNotBlank())
        assertTrue(layer.visible)
        assertFalse(layer.locked)
        assertFalse(layer.showInAmbient)
        assertEquals(1f, layer.opacity, 0f)
        assertEquals(0.5f, layer.xRatio, 0f)
        assertEquals(18f, layer.fontSizeSp, 0f)
    }

    @Test
    fun roundTripPreservesLayerOrderAndEditingProperties() {
        val face = HswfPackage(
            id = "custom",
            name = "Custom composition",
            background = BackgroundConfig(type = "image", imageDimAmount = 0f, gradientAngle = 120f),
            layers = listOf(
                FaceLayer(id = "panel", name = "Panel", type = "rectangle", cornerRadiusDp = 16f, opacity = 0.4f),
                FaceLayer(id = "label", text = "HEALTHSYNC", xRatio = 0.3f, yRatio = 0.6f,
                    isBold = true, fontFamily = "serif", rotationDegrees = 25f, locked = true, showInAmbient = true),
                FaceLayer(id = "accent", type = "line", colorHex = "#8038BDF8", widthRatio = 0.8f, heightRatio = 0.01f)
            )
        )

        val restored = gson.fromJson(gson.toJson(face), HswfPackage::class.java)

        assertEquals(face, restored)
        assertEquals(listOf("panel", "label", "accent"), restored.layers.map { it.id })
        assertEquals(0f, restored.background.imageDimAmount, 0f)
        assertTrue(restored.layers[1].locked)
        assertTrue(restored.layers[1].showInAmbient)
    }

    @Test
    fun normalizationRepairsNullListsAndIncompleteJsonArtwork() {
        val nullLayers = gson.fromJson("""{"layers":null,"background":null}""", HswfPackage::class.java).normalized()
        assertTrue(nullLayers.layers.isEmpty())
        assertEquals(0.25f, nullLayers.background.imageDimAmount, 0f)

        val face = gson.fromJson(
            """{"layers":[null,{"id":null,"name":null,"type":null,"text":null,"colorHex":null,"fontFamily":null}]}""",
            HswfPackage::class.java
        ).normalized()

        assertEquals(1, face.layers.size)
        assertTrue(face.layers.single().id.isNotBlank())
        assertEquals("Text layer", face.layers.single().name)
        assertEquals("text", face.layers.single().type)
        assertEquals("", face.layers.single().text)
        assertEquals("sans", face.layers.single().fontFamily)
        assertEquals("#FFFFFF", face.layers.single().colorHex)
    }

    @Test
    fun normalizationBoundsInvalidArtworkAndPreservesIntentionalZeroOpacity() {
        val invalid = FaceLayer(
            id = "invalid", type = "unsupported", xRatio = Float.NaN, yRatio = 2f,
            widthRatio = Float.POSITIVE_INFINITY, heightRatio = -3f, fontSizeSp = 500f,
            rotationDegrees = 725f, opacity = 0f, colorHex = "broken", fontFamily = "unknown",
            cornerRadiusDp = Float.NaN
        ).normalized()

        assertEquals("text", invalid.type)
        assertEquals(0.5f, invalid.xRatio, 0f)
        assertEquals(1f, invalid.yRatio, 0f)
        assertEquals(0.4f, invalid.widthRatio, 0f)
        assertEquals(0.005f, invalid.heightRatio, 0f)
        assertEquals(128f, invalid.fontSizeSp, 0f)
        assertEquals(5f, invalid.rotationDegrees, 0f)
        assertEquals(0f, invalid.opacity, 0f)
        assertEquals("#FFFFFF", invalid.colorHex)
        assertEquals("sans", invalid.fontFamily)
        assertEquals(0f, invalid.cornerRadiusDp, 0f)
    }

    @Test
    fun normalizationCapsWorkAndRepairsDuplicateIdsWithoutReordering() {
        val face = HswfPackage(
            id = "crowded", name = "Crowded",
            background = BackgroundConfig(imageDimAmount = -1f, gradientAngle = Float.NaN),
            layers = List(MAX_FACE_LAYERS + 5) { FaceLayer(id = "duplicate", text = "Layer $it") }
        ).normalized()

        assertEquals(MAX_FACE_LAYERS, face.layers.size)
        assertEquals(MAX_FACE_LAYERS, face.layers.map { it.id }.toSet().size)
        assertEquals("Layer 0", face.layers.first().text)
        assertEquals("Layer ${MAX_FACE_LAYERS - 1}", face.layers.last().text)
        assertEquals(0f, face.background.imageDimAmount, 0f)
        assertEquals(45f, face.background.gradientAngle, 0f)
    }
}

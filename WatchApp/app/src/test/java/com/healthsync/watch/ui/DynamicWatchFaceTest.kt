package com.healthsync.watch.ui

import com.google.gson.Gson
import com.healthsync.watch.data.watchface.DynamicFaceStore
import com.healthsync.watch.data.watchface.HswfPackage
import org.junit.Assert.*
import org.junit.Test

class DynamicWatchFaceTest {

    private val gson = Gson()

    @Test
    fun testNeonCyberpunkSampleSerialization() {
        val sample = DynamicFaceStore.createNeonCyberpunkSample()
        assertEquals("neon_cyberpunk", sample.id)
        assertEquals("hybrid", sample.type)
        assertNotNull(sample.hands)
        assertNotNull(sample.digitalClock)
        assertTrue(sample.dial.showTicks)
        assertEquals(60, sample.dial.tickCount)

        // Serialize to JSON
        val json = gson.toJson(sample)
        assertTrue(json.contains("neon_cyberpunk"))
        assertTrue(json.contains("Neon Cyberpunk"))

        // Deserialize back
        val restored = gson.fromJson(json, HswfPackage::class.java)
        assertEquals(sample.id, restored.id)
        assertEquals(sample.name, restored.name)
        assertEquals(sample.hands?.hourHand?.shape, restored.hands?.hourHand?.shape)
        assertEquals(sample.complications.heartRate.colorHex, restored.complications.heartRate.colorHex)
        assertEquals(sample.aod.mode, restored.aod.mode)
    }

    @Test
    fun testBauhausMinimalSampleSerialization() {
        val sample = DynamicFaceStore.createBauhausMinimalSample()
        assertEquals("bauhaus_minimal", sample.id)
        assertEquals("analog", sample.type)
        assertNull(sample.digitalClock)
        assertEquals(12, sample.dial.tickCount)
        assertEquals("baton", sample.hands?.hourHand?.shape)

        // Serialize to JSON
        val json = gson.toJson(sample)
        assertTrue(json.contains("bauhaus_minimal"))

        // Deserialize back
        val restored = gson.fromJson(json, HswfPackage::class.java)
        assertEquals(sample.id, restored.id)
        assertEquals("minimal_cardinal", restored.dial.numberType)
    }

    @Test
    fun testAnimationAndInteractiveSchemaSerialization() {
        val dynamicPkg = HswfPackage(
            id = "test_animated_face",
            name = "Test Animated Face",
            author = "Online Store",
            type = "digital",
            background = com.healthsync.watch.data.watchface.BackgroundConfig(type = "solid", colorHex = "#000000"),
            dial = com.healthsync.watch.data.watchface.DialConfig(showTicks = false),
            animation = com.healthsync.watch.data.watchface.AnimationConfig(
                type = "radar_sweep",
                speedRatio = 1.0f,
                colorHex = "#00FF66"
            ),
            interactive = com.healthsync.watch.data.watchface.InteractiveConfig(
                tapEffect = "palette_cycle",
                colorPalettes = listOf("#00FF66", "#00F0FF"),
                hapticFeedback = true
            )
        )

        val json = gson.toJson(dynamicPkg)
        val restored = gson.fromJson(json, HswfPackage::class.java)
        assertEquals("test_animated_face", restored.id)
        assertNotNull(restored.animation)
        assertEquals("radar_sweep", restored.animation?.type)
        assertNotNull(restored.interactive)
        assertEquals("palette_cycle", restored.interactive?.tapEffect)
        assertEquals(2, restored.interactive?.colorPalettes?.size)
    }

    @Test
    fun testCatalogDynamicRecognition() {
        assertTrue(WatchFaceCatalog.isDynamic("dynamic_neon_cyberpunk"))
        assertTrue(WatchFaceCatalog.isDynamic("dynamic_custom_user_dial"))
        assertTrue(WatchFaceCatalog.isDynamic("dynamic_quantum_sonar_hud"))
        assertFalse(WatchFaceCatalog.isDynamic("orbit"))
        assertFalse(WatchFaceCatalog.isDynamic("chrono"))
        assertFalse(WatchFaceCatalog.isDynamic("classic"))
    }
}

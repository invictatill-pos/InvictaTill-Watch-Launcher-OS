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
    fun testQuantumSonarSampleSerialization() {
        val sample = DynamicFaceStore.createQuantumSonarSample()
        assertEquals("quantum_sonar_hud", sample.id)
        assertNotNull(sample.animation)
        assertEquals("radar_sweep", sample.animation?.type)
        assertNotNull(sample.interactive)
        assertEquals("palette_cycle", sample.interactive?.tapEffect)
        assertTrue(sample.interactive?.colorPalettes?.isNotEmpty() == true)

        val json = gson.toJson(sample)
        val restored = gson.fromJson(json, HswfPackage::class.java)
        assertEquals(sample.id, restored.id)
        assertEquals("radar_sweep", restored.animation?.type)
        assertEquals("palette_cycle", restored.interactive?.tapEffect)
    }

    @Test
    fun testCyberEcgSampleSerialization() {
        val sample = DynamicFaceStore.createCyberEcgSample()
        assertEquals("cyberpunk_pulse_ecg", sample.id)
        assertNotNull(sample.animation)
        assertEquals("pulse_ecg", sample.animation?.type)

        val json = gson.toJson(sample)
        val restored = gson.fromJson(json, HswfPackage::class.java)
        assertEquals(sample.id, restored.id)
        assertEquals("pulse_ecg", restored.animation?.type)
    }

    @Test
    fun testTourbillonSampleSerialization() {
        val sample = DynamicFaceStore.createTourbillonSample()
        assertEquals("kinetic_tourbillon_haute", sample.id)
        assertNotNull(sample.animation)
        assertEquals("tourbillon_spin", sample.animation?.type)
        assertEquals("mode_toggle", sample.interactive?.tapEffect)

        val json = gson.toJson(sample)
        val restored = gson.fromJson(json, HswfPackage::class.java)
        assertEquals(sample.id, restored.id)
        assertEquals("tourbillon_spin", restored.animation?.type)
        assertEquals("mode_toggle", restored.interactive?.tapEffect)
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

package com.healthsync.phone.data

import com.google.gson.Gson
import com.healthsync.phone.data.model.*
import com.healthsync.phone.data.watchface.OnlineWatchFaceCatalog
import org.junit.Assert.*
import org.junit.Test

class OnlineWatchFaceCatalogTest {
    private val gson = Gson()

    @Test
    fun catalogContainsAllCuratedWatchFaces() {
        val items = OnlineWatchFaceCatalog.items
        assertTrue("Catalog should contain at least 60 dials", items.size >= 60)

        val categories = items.map { it.category }.distinct()
        assertTrue("Contains Luxury category", categories.contains("Luxury"))
        assertTrue("Contains Sports category", categories.contains("Sports"))
        assertTrue("Contains Minimal category", categories.contains("Minimal"))
        assertTrue("Contains Cyberpunk category", categories.contains("Cyberpunk"))
        assertTrue("Contains Retro category", categories.contains("Retro"))
        assertTrue("Contains Art category", categories.contains("Art"))

        // Verify animated and interactive dials
        val animatedDials = items.filter { it.pkg.animation != null && it.pkg.animation?.type != "none" }
        assertTrue("Catalog contains animated dials", animatedDials.size >= 8)

        val interactiveDials = items.filter { it.pkg.interactive != null }
        assertTrue("Catalog contains interactive dials", interactiveDials.size >= 8)

        // Verify specific flagship innovative faces
        val sonar = items.firstOrNull { it.pkg.id == "quantum_sonar_hud" }
        assertNotNull("Quantum Sonar HUD present", sonar)
        assertEquals("radar_sweep", sonar?.pkg?.animation?.type)
        assertEquals("palette_cycle", sonar?.pkg?.interactive?.tapEffect)

        val ecg = items.firstOrNull { it.pkg.id == "cyberpunk_pulse_ecg" }
        assertNotNull("Cyberpunk Pulse ECG present", ecg)
        assertEquals("pulse_ecg", ecg?.pkg?.animation?.type)

        val tourbillon = items.firstOrNull { it.pkg.id == "kinetic_tourbillon_haute" }
        assertNotNull("Kinetic Tourbillon present", tourbillon)
        assertEquals("tourbillon_spin", tourbillon?.pkg?.animation?.type)
        assertEquals("mode_toggle", tourbillon?.pkg?.interactive?.tapEffect)
    }

    @Test
    fun allWatchFacesSerializeAndDeserializeCleanly() {
        for (item in OnlineWatchFaceCatalog.items) {
            val json = gson.toJson(item.pkg)
            val deserialized = gson.fromJson(json, com.healthsync.phone.data.watchface.HswfPackage::class.java)

            assertEquals("ID matches", item.pkg.id, deserialized.id)
            assertEquals("Name matches", item.pkg.name, deserialized.name)
            assertNotNull("Dial config present", deserialized.dial)
            assertNotNull("Complications config present", deserialized.complications)

            // Test encapsulation in WatchFaceInstallPayload
            val payload = WatchFaceInstallPayload(
                id = item.pkg.id,
                name = item.pkg.name,
                jsonContent = json,
                setActive = true
            )
            val wireMsg = SyncMessage(MessageType.WATCH_FACE_INSTALL, payload = gson.toJson(payload))
            val decodedMsg = gson.fromJson(gson.toJson(wireMsg), SyncMessage::class.java)
            val decodedPayload = gson.fromJson(decodedMsg.payload, WatchFaceInstallPayload::class.java)

            assertEquals(item.pkg.id, decodedPayload.id)
            assertTrue(decodedPayload.setActive)
        }
    }

    @Test
    fun otaChunkAndDeviceInfoSurviveTransport() {
        val otaChunk = OtaChunkPayload(
            chunkIndex = 5,
            totalChunks = 50,
            dataBase64 = "dGVzdC1kYXRh",
            offset = 40960L,
            chunkSize = 8192
        )
        val chunkMsg = SyncMessage(MessageType.OTA_CHUNK, payload = gson.toJson(otaChunk))
        val decodedChunk = gson.fromJson(gson.fromJson(gson.toJson(chunkMsg), SyncMessage::class.java).payload, OtaChunkPayload::class.java)
        assertEquals(5, decodedChunk.chunkIndex)
        assertEquals(50, decodedChunk.totalChunks)
        assertEquals(40960L, decodedChunk.offset)
        assertEquals(8192, decodedChunk.chunkSize)

        val devInfo = DeviceInfoPayload(
            versionName = "2.5.0",
            versionCode = 22,
            model = "Kolabee U8 Ultra",
            batteryPercent = 92
        )
        val devMsg = SyncMessage(MessageType.DEVICE_INFO, payload = gson.toJson(devInfo))
        val decodedDev = gson.fromJson(gson.fromJson(gson.toJson(devMsg), SyncMessage::class.java).payload, DeviceInfoPayload::class.java)
        assertEquals("2.5.0", decodedDev.versionName)
        assertEquals(22, decodedDev.versionCode)
        assertEquals("Kolabee U8 Ultra", decodedDev.model)
        assertEquals(92, decodedDev.batteryPercent)
    }
}

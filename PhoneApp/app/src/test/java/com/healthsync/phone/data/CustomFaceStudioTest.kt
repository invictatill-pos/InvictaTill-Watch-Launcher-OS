package com.healthsync.phone.data

import com.google.gson.Gson
import com.healthsync.phone.data.model.MessageType
import com.healthsync.phone.data.model.SyncMessage
import com.healthsync.phone.data.model.WatchFaceInstallPayload
import com.healthsync.phone.data.watchface.*
import org.junit.Assert.*
import org.junit.Test

class CustomFaceStudioTest {
    private val gson = Gson()

    @Test
    fun photoDialEncapsulationAndSerialization() {
        val dummyPhotoBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        val photoPackage = HswfPackage(
            id = "custom_photo_1001",
            name = "Family Vacation",
            author = "You",
            type = "hybrid",
            background = BackgroundConfig(
                type = "image",
                imageBase64 = dummyPhotoBase64
            ),
            dial = DialConfig(
                showTicks = true,
                tickCount = 12,
                showNumbers = true,
                numberType = "minimal_cardinal"
            ),
            digitalClock = DigitalClockConfig(
                enabled = true,
                format = "HH:mm",
                colorHex = "#FFFFFF"
            ),
            hands = HandsConfig(
                hourHand = HandStyle(shape = "baton", colorHex = "#FFFFFF"),
                minuteHand = HandStyle(shape = "baton", colorHex = "#00E5FF")
            ),
            complications = ComplicationsConfig(
                heartRate = ComplicationWidget(enabled = true, style = "text_only", colorHex = "#FF3366")
            )
        )

        val json = gson.toJson(photoPackage)
        val deserialized = gson.fromJson(json, HswfPackage::class.java)

        assertEquals("custom_photo_1001", deserialized.id)
        assertEquals("image", deserialized.background.type)
        assertEquals(dummyPhotoBase64, deserialized.background.imageBase64)
        assertTrue(deserialized.dial.showTicks)
        assertTrue(deserialized.digitalClock?.enabled == true)

        // Test wire encapsulation into Bluetooth install message
        val payload = WatchFaceInstallPayload(
            id = photoPackage.id,
            name = photoPackage.name,
            jsonContent = json,
            setActive = true
        )
        val syncMsg = SyncMessage(type = MessageType.WATCH_FACE_INSTALL, payload = gson.toJson(payload))
        val wireString = gson.toJson(syncMsg)

        val decodedSyncMsg = gson.fromJson(wireString, SyncMessage::class.java)
        assertEquals(MessageType.WATCH_FACE_INSTALL, decodedSyncMsg.type)
        val decodedPayload = gson.fromJson(decodedSyncMsg.payload, WatchFaceInstallPayload::class.java)
        assertEquals("Family Vacation", decodedPayload.name)
        assertTrue(decodedPayload.setActive)

        val unpackedPkg = gson.fromJson(decodedPayload.jsonContent, HswfPackage::class.java)
        assertEquals(dummyPhotoBase64, unpackedPkg.background.imageBase64)
    }

    @Test
    fun studioTemplatesAreValidAndDistinct() {
        val templates = listOf(
            HswfPackage(id = "tmpl_1", name = "Photo Memories", background = BackgroundConfig(type = "solid")),
            HswfPackage(id = "tmpl_2", name = "Executive Chrono", background = BackgroundConfig(type = "gradient_radial")),
            HswfPackage(id = "tmpl_3", name = "Cyber Neon", background = BackgroundConfig(type = "gradient_linear")),
            HswfPackage(id = "tmpl_4", name = "Minimal Bauhaus", background = BackgroundConfig(type = "solid"))
        )

        assertEquals(4, templates.size)
        val ids = templates.map { it.id }.distinct()
        assertEquals(4, ids.size)

        for (tmpl in templates) {
            val serialized = gson.toJson(tmpl)
            val deserialized = gson.fromJson(serialized, HswfPackage::class.java)
            assertEquals(tmpl.name, deserialized.name)
        }
    }
}

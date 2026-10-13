package com.healthsync.phone.data

import com.google.gson.Gson
import com.healthsync.phone.data.model.*
import org.junit.Assert.*
import org.junit.Test

class SyncProtocolTest {
    private val gson = Gson()
    @Test fun notificationIdentityAndReplyCapabilitySurviveTransport() {
        val notification = NotificationPayload("chat.app", "Chat", "A person", "Message", canReply = true, notificationKey = "unique-conversation")
        val frame = SyncMessage(MessageType.NOTIFICATION, 1234, gson.toJson(notification))
        val decoded = gson.fromJson(gson.toJson(frame), SyncMessage::class.java)
        val payload = gson.fromJson(decoded.payload, NotificationPayload::class.java)
        assertEquals(MessageType.NOTIFICATION, decoded.type)
        assertEquals(1234L, decoded.timestamp)
        assertTrue(payload.canReply)
        assertEquals("unique-conversation", payload.notificationKey)
    }
    @Test fun settingsCarryTheSameGoalAsTheDashboard() {
        val settings = WatchSettingsPayload(60_000, -1, 8000)
        assertEquals(settings, gson.fromJson(gson.toJson(settings), WatchSettingsPayload::class.java))
    }
    @Test fun acknowledgementsRetainOnlyTheirRecordIdentity() {
        val ack = SyncAckPayload(listOf("session-A", "session-B"), listOf(300_000, 600_000))
        assertEquals(ack, gson.fromJson(gson.toJson(ack), SyncAckPayload::class.java))
    }
    @Test fun phoneControlsRejectSystemCallAndMalformedActions() {
        assertNull(validatedPhoneControlAction("ANSWER"))
        assertNull(validatedPhoneControlAction("REJECT"))
        assertNull(validatedPhoneControlAction("FIND_START;ANSWER"))
        assertNull(validatedPhoneControlAction(" ".repeat(100) + "NEXT"))
        assertNull(validatedPhoneControlAction(null))
        assertEquals("NEXT", validatedPhoneControlAction(" next "))
        assertEquals("FIND_STOP", validatedPhoneControlAction("find_stop"))
    }
    @Test fun phoneControlReplyKeepsCorrelationAndUnknownBatteryWithoutInventingPlayback() {
        val wire = """{"requestId":4821,"title":"A track","artist":"An artist","batteryPercent":-1,"findingPhone":true}"""
        val response = gson.fromJson(wire, PhoneControlStatePayload::class.java)
        assertEquals(4821L, response.requestId)
        assertEquals(-1, response.batteryPercent)
        assertTrue(response.findingPhone)
        assertFalse(response.playing)
        assertFalse(response.available)
    }

    @Test fun deviceInfoPayloadTransportsWatchBatteryAndFirmware() {
        val info = DeviceInfoPayload(
            versionName = "2.8.3",
            versionCode = 283,
            model = "Kolabee U8 Ultra",
            batteryPercent = 78,
            isCharging = true,
            powerSaveMode = "NORMAL",
            storageFreeMb = 12400L,
            storageTotalMb = 16384L,
            androidVersion = "Android 8.1.0",
            manufacturer = "Kolabee",
            hasHeartRateSensor = true,
            hasSpO2Sensor = true,
            hasStepSensor = true
        )
        val wire = gson.toJson(info)
        val decoded = gson.fromJson(wire, DeviceInfoPayload::class.java)
        assertEquals("2.8.3", decoded.versionName)
        assertEquals(283, decoded.versionCode)
        assertEquals(78, decoded.batteryPercent)
        assertTrue(decoded.isCharging)
        assertEquals("NORMAL", decoded.powerSaveMode)
        assertEquals(12400L, decoded.storageFreeMb)
        assertTrue(decoded.hasSpO2Sensor)

        val connInfo = ConnectionInfo(
            isConnected = true,
            deviceName = "Kolabee U8 Ultra",
            batteryPercent = decoded.batteryPercent,
            isCharging = decoded.isCharging,
            powerSaveMode = decoded.powerSaveMode,
            storageFreeMb = decoded.storageFreeMb,
            storageTotalMb = decoded.storageTotalMb,
            androidVersion = decoded.androidVersion,
            manufacturer = decoded.manufacturer,
            hasHeartRateSensor = decoded.hasHeartRateSensor,
            hasSpO2Sensor = decoded.hasSpO2Sensor,
            hasStepSensor = decoded.hasStepSensor
        )
        assertEquals(78, connInfo.batteryPercent)
        assertTrue(connInfo.isCharging)
        assertEquals(12400L, connInfo.storageFreeMb)
        assertTrue(connInfo.hasHeartRateSensor)
        assertTrue(connInfo.isConnected)
    }

    @Test fun hydrationPayloadSerializationAndRoundtrip() {
        val payload = HydrationPayload(
            date = "2026-10-13",
            amountMl = 1750,
            goalMl = 2500,
            timestamp = 1_000_000L
        )
        val json = gson.toJson(payload)
        val decoded = gson.fromJson(json, HydrationPayload::class.java)
        assertEquals("2026-10-13", decoded.date)
        assertEquals(1750, decoded.amountMl)
        assertEquals(2500, decoded.goalMl)
        assertEquals(1_000_000L, decoded.timestamp)
    }

    @Test fun wellnessSettingsPayloadRoundtrip() {
        val payload = WellnessSettingsPayload(
            dailyWaterGoalMl = 2500,
            hydrationReminderEnabled = true,
            hydrationReminderIntervalMinutes = 45,
            standReminderEnabled = true
        )
        val json = gson.toJson(payload)
        val decoded = gson.fromJson(json, WellnessSettingsPayload::class.java)
        assertEquals(2500, decoded.dailyWaterGoalMl)
        assertTrue(decoded.hydrationReminderEnabled)
        assertEquals(45, decoded.hydrationReminderIntervalMinutes)
        assertTrue(decoded.standReminderEnabled)
    }

    @Test fun batchedSyncPayloadIncludesHeartRateSpO2AndHydration() {
        val hr = listOf(HeartRateRecordPayload(bpm = 74, accuracy = 2, timestamp = 100_000L))
        val spo2 = listOf(SpO2RecordPayload(percentage = 98.2f, timestamp = 200_000L))
        val hydration = HydrationPayload(date = "2026-10-13", amountMl = 750, goalMl = 2000, timestamp = 300_000L)
        val batch = BatchedSyncPayload(
            heart_rates = hr,
            spo2_readings = spo2,
            hydration = hydration
        )
        val json = gson.toJson(batch)
        val decoded = gson.fromJson(json, BatchedSyncPayload::class.java)
        assertEquals(1, decoded.heart_rates.size)
        assertEquals(74, decoded.heart_rates[0].bpm)
        assertEquals(1, decoded.spo2_readings.size)
        assertEquals(98.2f, decoded.spo2_readings[0].percentage, 0.01f)
        assertNotNull(decoded.hydration)
        assertEquals(750, decoded.hydration?.amountMl)

        val ack = SyncAckPayload(
            hr_timestamps = listOf(100_000L),
            spo2_timestamps = listOf(200_000L)
        )
        val ackJson = gson.toJson(ack)
        val decodedAck = gson.fromJson(ackJson, SyncAckPayload::class.java)
        assertEquals(listOf(100_000L), decodedAck.hr_timestamps)
        assertEquals(listOf(200_000L), decodedAck.spo2_timestamps)
    }
}

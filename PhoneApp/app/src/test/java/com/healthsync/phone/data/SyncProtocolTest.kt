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
}

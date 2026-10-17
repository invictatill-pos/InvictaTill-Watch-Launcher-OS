package com.healthsync.phone.data
import com.healthsync.phone.data.model.*

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class NavigationProtocolTest {
    private val active = NavigationPayload(status = "ACTIVE", streamId = "stream-1", sequence = 1,
        sessionId = "maps-route", instruction = "Turn left onto Main Road", distance = "200 m",
        road = "Main Road", summary = "12 min · 4 km · 18:30", maneuver = "LEFT")

    @Test fun activeStateRequiresActualInstructionAndSession() {
        assertTrue(NavigationProtocol.validState(active))
        assertFalse(NavigationProtocol.validState(active.copy(instruction = "  ")))
        assertFalse(NavigationProtocol.validState(active.copy(sessionId = "")))
        assertFalse(NavigationProtocol.validState(active.copy(sequence = 0)))
        assertFalse(NavigationProtocol.validState(active.copy(streamId = "bad stream")))
        assertFalse(NavigationProtocol.validState(active.copy(maneuver = "GUESS")))
    }

    @Test fun inactiveStateCannotRetainOldDirectionsOrIcons() {
        for (status in NavigationProtocol.STATUSES - "ACTIVE") {
            val state = NavigationPayload(status, "stream-1", 2)
            assertTrue(NavigationProtocol.validState(state))
            assertFalse(NavigationProtocol.validState(state.copy(instruction = "Old turn")))
            assertFalse(NavigationProtocol.validState(state.copy(distance = "100 m")))
            assertFalse(NavigationProtocol.validState(state.copy(road = "Old road")))
            assertFalse(NavigationProtocol.validState(state.copy(summary = "Old ETA")))
            assertFalse(NavigationProtocol.validState(state.copy(iconBase64 = "abcd")))
            assertFalse(NavigationProtocol.validState(state.copy(maneuver = "LEFT")))
        }
    }

    @Test fun textAndEncodedImageBoundsRejectBeforeRendering() {
        assertTrue(NavigationProtocol.validState(active.copy(instruction = "a".repeat(300),
            distance = "m".repeat(40), road = "r".repeat(300), summary = "s".repeat(300),
            iconBase64 = "a".repeat(NavigationProtocol.MAX_ICON_BASE64))))
        assertFalse(NavigationProtocol.validState(active.copy(instruction = "a".repeat(301))))
        assertFalse(NavigationProtocol.validState(active.copy(distance = "a".repeat(41))))
        assertFalse(NavigationProtocol.validState(active.copy(sessionId = "a".repeat(513))))
        assertFalse(NavigationProtocol.validState(active.copy(iconBase64 = "a".repeat(NavigationProtocol.MAX_ICON_BASE64 + 1))))
    }

    @Test fun nonEnglishDirectionsArePreservedWithoutInventingManeuver() {
        val hindi = active.copy(instruction = "बाईं ओर मुड़ें", road = "एमजी रोड", maneuver = "UNKNOWN")
        assertTrue(NavigationProtocol.validState(hindi))
        assertEquals(hindi, Gson().fromJson(Gson().toJson(hindi), NavigationPayload::class.java))
    }

    @Test fun requestEmptyAndNullFieldsCannotBecomeActiveState() {
        val gson = Gson()
        assertFalse(NavigationProtocol.validState(NavigationPayload()))
        assertFalse(NavigationProtocol.validState(gson.fromJson("{}", NavigationPayload::class.java)))
        val malformed = gson.fromJson("""{"status":"ACTIVE","streamId":null,"sequence":1,"instruction":null}""",
            NavigationPayload::class.java)
        assertFalse(NavigationProtocol.validState(malformed))
        assertFalse(gson.fromJson("""{"versionName":"2.10.0","versionCode":38}""", DeviceInfoPayload::class.java).supportsNavigation)
    }
}

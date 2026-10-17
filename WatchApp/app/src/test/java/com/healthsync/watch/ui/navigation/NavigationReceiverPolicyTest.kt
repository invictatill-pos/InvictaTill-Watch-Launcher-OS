package com.healthsync.watch.ui.navigation

import com.healthsync.watch.data.NavigationPayload
import com.healthsync.watch.data.NavigationProtocol
import org.junit.Assert.*
import org.junit.Test

class NavigationReceiverPolicyTest {
    private fun active(sequence: Long = 1L, stream: String = "phone-stream-1") = NavigationPayload(
        status = "ACTIVE", streamId = stream, sequence = sequence, sessionId = "maps-route-1",
        instruction = "Turn left onto Park Road", distance = "200 m", road = "Park Road", maneuver = "LEFT")
    private fun ready() = NavigationReceiverPolicy().apply { connectionChanged(true) }

    @Test fun routeRequiresConnectionValidPayloadAndIncreasingSequence() {
        val receiver = NavigationReceiverPolicy()
        assertFalse(receiver.receive(active(), 100).accepted)
        assertEquals(NavigationReceiverPolicy.Status.DISCONNECTED, receiver.snapshot(100).status)
        receiver.connectionChanged(true)
        assertEquals(NavigationReceiverPolicy.Status.WAITING, receiver.snapshot(100).status)
        assertFalse(receiver.receive(active().copy(status = "REQUEST"), 100).accepted)
        assertFalse(receiver.receive(active().copy(sequence = 0), 100).accepted)
        assertFalse(receiver.receive(active().copy(instruction = ""), 100).accepted)
        assertTrue(receiver.receive(active(3), 100).accepted)
        assertFalse(receiver.receive(active(3), 200).accepted)
        assertFalse(receiver.receive(active(2), 200).accepted)
        assertEquals(100L, receiver.snapshot(200).receivedAt)
        assertTrue(receiver.receive(active(4), 200).accepted)
    }

    @Test fun duplicateCannotExtendFreshnessAndStaleDirectionsDisappearAtTwentySeconds() {
        val receiver = ready()
        assertTrue(receiver.receive(active(), 1_000).accepted)
        assertEquals(NavigationReceiverPolicy.Status.ACTIVE, receiver.snapshot(20_999).status)
        assertNotNull(receiver.snapshot(20_999).payload)
        assertFalse(receiver.receive(active(), 20_999).accepted)
        assertEquals(NavigationReceiverPolicy.Status.STALE, receiver.snapshot(21_000).status)
        assertNull(receiver.snapshot(21_000).payload)
        assertEquals(NavigationReceiverPolicy.Status.STALE, receiver.snapshot(999).status)
        assertFalse(receiver.receive(active(2), 21_001).maneuverChanged)
        assertEquals(NavigationReceiverPolicy.Status.ACTIVE, receiver.snapshot(21_001).status)
    }

    @Test fun heartbeatRefreshesWithoutRepeatingAHaptic() {
        val receiver = ready()
        assertFalse(receiver.receive(active(), 100).maneuverChanged)
        assertFalse(receiver.receive(active(2), 5_100).maneuverChanged)
        assertFalse(receiver.receive(active(3).copy(distance = "100 m"), 10_100).maneuverChanged)
        assertEquals(NavigationReceiverPolicy.Status.ACTIVE, receiver.snapshot(29_999).status)
        assertEquals("100 m", receiver.snapshot(29_999).payload?.distance)
    }

    @Test fun meaningfulManeuverAndRoadChangesAlertOnceOnlyWithinTheSameFreshRoute() {
        val receiver = ready()
        receiver.receive(active(), 100)
        assertTrue(receiver.receive(active(2).copy(maneuver = "RIGHT"), 200).maneuverChanged)
        assertFalse(receiver.receive(active(3).copy(maneuver = "RIGHT"), 300).maneuverChanged)
        assertTrue(receiver.receive(active(4).copy(maneuver = "RIGHT", road = "Lake Road", instruction = "Turn right onto Lake Road"), 400).maneuverChanged)
        assertFalse(receiver.receive(active(5).copy(road = "Lake Road", maneuver = "UNKNOWN"), 500).maneuverChanged)
        assertFalse(receiver.receive(active(6).copy(sessionId = "new-route"), 600).maneuverChanged)
    }

    @Test fun disconnectedGuidanceIsErasedAndReconnectWaitsForFreshPhoneState() {
        val receiver = ready()
        receiver.receive(active(5), 100)
        receiver.connectionChanged(false)
        assertEquals(NavigationReceiverPolicy.Status.DISCONNECTED, receiver.snapshot(101).status)
        assertNull(receiver.snapshot(101).payload)
        assertNull(receiver.snapshot(101).receivedAt)
        receiver.connectionChanged(true)
        assertEquals(NavigationReceiverPolicy.Status.WAITING, receiver.snapshot(102).status)
        assertNull(receiver.snapshot(102).payload)
        assertFalse(receiver.receive(active(5), 103).accepted)
        assertFalse(receiver.receive(active(6).copy(maneuver = "RIGHT"), 104).maneuverChanged)
        assertEquals(NavigationReceiverPolicy.Status.ACTIVE, receiver.snapshot(104).status)
    }

    @Test fun duplicateConnectionCallbackDoesNotBlankANewRoute() {
        val receiver = ready()
        receiver.receive(active(), 100)
        receiver.connectionChanged(true)
        assertEquals(NavigationReceiverPolicy.Status.ACTIVE, receiver.snapshot(101).status)
    }

    @Test fun everyNonactiveStateImmediatelyRemovesAllGuidanceAndPreventsDelayedReplay() {
        for (status in listOf("IDLE", "DISABLED", "UNAVAILABLE")) {
            val receiver = ready()
            receiver.receive(active(), 100)
            val stopped = NavigationPayload(status = status, streamId = "phone-stream-1", sequence = 2,
                message = "Source status message")
            assertTrue(receiver.receive(stopped, 101).accepted)
            assertEquals(status, receiver.snapshot(102).status.name)
            assertEquals("", receiver.snapshot(102).payload?.instruction)
            assertEquals("", receiver.snapshot(102).payload?.distance)
            assertEquals("", receiver.snapshot(102).payload?.road)
            assertEquals("", receiver.snapshot(102).payload?.iconBase64)
            assertEquals("Source status message", receiver.snapshot(102).payload?.message)
            assertFalse(receiver.receive(active(), 103).accepted)
            assertFalse(receiver.receive(active(3), 104).maneuverChanged)
        }
    }

    @Test fun retiredProcessStreamsCannotResurrectAnEarlierRoute() {
        val receiver = ready()
        receiver.receive(active(10), 100)
        assertTrue(receiver.receive(active(1, "new-phone-process"), 101).accepted)
        assertFalse(receiver.receive(active(11), 102).accepted)
        assertEquals("new-phone-process", receiver.snapshot(102).payload?.streamId)
        assertEquals(1L, receiver.snapshot(102).payload?.sequence)
    }

    @Test fun oversizedFieldsAndMalformedNullFromWireAreRejectedBeforeDecode() {
        val receiver = ready()
        assertFalse(receiver.receive(active().copy(iconBase64 = "x".repeat(NavigationProtocol.MAX_ICON_BASE64 + 1)), 100).accepted)
        assertFalse(receiver.receive(active().copy(distance = "x".repeat(41)), 100).accepted)
        assertFalse(receiver.receive(active().copy(instruction = "x".repeat(301)), 100).accepted)
        assertFalse(receiver.receive(active().copy(maneuver = "invented-right"), 100).accepted)
        assertFalse(receiver.receive(active().copy(streamId = "../bad"), 100).accepted)
        val malformed = com.google.gson.Gson().fromJson("{\"status\":\"ACTIVE\"}", NavigationPayload::class.java)
        assertFalse(receiver.receive(malformed, 100).accepted)
    }

    @Test fun hapticComparisonIgnoresInstructionFormattingUnknownDirectionAndNewRoadWithoutPriorRoad() {
        val first = active()
        assertFalse(meaningfulNavigationChange(first, first.copy(instruction = "200 m: Turn left onto Park Road")))
        assertFalse(meaningfulNavigationChange(first, first.copy(maneuver = "UNKNOWN")))
        assertFalse(meaningfulNavigationChange(first.copy(road = ""), first))
        assertFalse(meaningfulNavigationChange(null, first))
        assertFalse(meaningfulNavigationChange(first, first.copy(streamId = "phone-stream-2")))
    }

    @Test fun unknownDirectionsAndChangingEtaOrDistanceInSourceMetadataRemainSilent() {
        val unknown = active().copy(maneuver = "UNKNOWN")
        assertFalse(meaningfulNavigationChange(unknown, unknown.copy(road = "Lake Road")))
        val first = active().copy(instruction = "Turn left", road = "10 min · 5 km · 12:30")
        assertFalse(meaningfulNavigationChange(first, first.copy(road = "9 min · 4 km · 12:29")))
        assertFalse(meaningfulNavigationChange(first, first.copy(road = "200 m")))
        assertFalse(meaningfulNavigationChange(active(), active().copy(road = "10 min · 5 km")))
        assertTrue(meaningfulNavigationChange(active(), active().copy(road = "Lake Road", instruction = "Turn left onto Lake Road")))
    }
}

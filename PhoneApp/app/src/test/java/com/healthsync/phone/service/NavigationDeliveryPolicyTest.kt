package com.healthsync.phone.service

import com.google.gson.Gson
import com.healthsync.phone.data.model.MessageType
import com.healthsync.phone.data.model.NavigationPayload
import com.healthsync.phone.data.model.NavigationProtocol
import com.healthsync.phone.data.model.SyncMessage
import org.junit.Assert.*
import org.junit.Test

class NavigationDeliveryPolicyTest {
    private val gson = Gson()
    private fun snapshot(status: String, sequence: Long) = SyncMessage(MessageType.NAVIGATION,
        payload = gson.toJson(NavigationPayload(status = status, streamId = "phone-stream", sequence = sequence,
            sessionId = if (status == "ACTIVE") "route" else "",
            instruction = if (status == "ACTIVE") "Turn left" else "")))

    @Test fun disablingOrEndingRouteDiscardsOlderQueuedTurns() {
        val firstTurn = snapshot("ACTIVE", 1)
        val secondTurn = snapshot("ACTIVE", 2)
        for (status in listOf("DISABLED", "IDLE", "UNAVAILABLE")) {
            val clear = snapshot(status, 3)
            val queued = listOf(firstTurn, secondTurn, clear)
            assertEquals(listOf(clear), queued.filter { navigationSnapshotCanSend(it, clear, 100, 110) })
        }
    }

    @Test fun queuedTurnExpiresEvenWhenNothingNewHasBeenQueued() {
        val turn = snapshot("ACTIVE", 1)
        assertTrue(navigationSnapshotCanSend(turn, turn, 100, 100 + NavigationProtocol.HEARTBEAT_MS))
        assertFalse(navigationSnapshotCanSend(turn, turn, 100, 101 + NavigationProtocol.HEARTBEAT_MS))
        assertFalse(navigationSnapshotCanSend(turn, turn, 100, 99))
        assertFalse(navigationSnapshotCanSend(turn, null, 100, 100))
    }

    @Test fun distanceUpdatesCoalesceToTheLatestOfferedSnapshot() {
        val updates = (1L..100L).map { snapshot("ACTIVE", it) }
        val latest = updates.last()
        assertEquals(listOf(latest), updates.filter { navigationSnapshotCanSend(it, latest, 100, 120) })
    }
}

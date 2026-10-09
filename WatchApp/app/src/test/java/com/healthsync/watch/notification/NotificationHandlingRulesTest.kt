package com.healthsync.watch.notification

import com.healthsync.watch.data.NotificationPayload
import org.junit.Assert.*
import org.junit.Test

class NotificationHandlingRulesTest {
    @Test fun takeoverRequiresPermissionSettingAndDismissibleOrdinaryAlert() {
        assertTrue(canTakeOverWatchNotification(true, true, false, false, false, false, false))
        assertFalse(canTakeOverWatchNotification(false, true, false, false, false, false, false))
        assertFalse(canTakeOverWatchNotification(true, false, false, false, false, false, false))
        assertFalse(canTakeOverWatchNotification(true, true, true, false, false, false, false))
        assertFalse(canTakeOverWatchNotification(true, true, false, true, false, false, false))
    }

    @Test fun takeoverDoesNotDismissTransfersRepliesOrFullScreenPromptsWithSideEffects() {
        assertFalse(canTakeOverWatchNotification(true, true, false, false, true, false, false))
        assertFalse(canTakeOverWatchNotification(true, true, false, false, false, true, false))
        assertFalse(canTakeOverWatchNotification(true, true, false, false, false, false, true))
    }

    @Test fun customImageOnlyNotificationsKeepTheirOriginalWhenContentCannotBeMirrored() {
        assertFalse(canTakeOverWatchNotification(true, true, false, false, false, false, false, hasReadableContent = false))
        assertTrue(canTakeOverWatchNotification(true, true, false, false, false, false, false, hasReadableContent = true))
    }

    @Test fun retainedActionsNeverCrossSourcePackageKeyOrMessageRevision() {
        val entry = watchEntry()
        assertTrue(matchesWatchNotificationRevision(entry, entry.payload))
        assertFalse(matchesWatchNotificationRevision(entry.copy(source = "phone"), entry.payload))
        assertFalse(matchesWatchNotificationRevision(entry, entry.payload.copy(packageName = "other.app")))
        assertFalse(matchesWatchNotificationRevision(entry, entry.payload.copy(notificationKey = "other-key")))
        assertFalse(matchesWatchNotificationRevision(entry, entry.payload.copy(title = "New title")))
        assertFalse(matchesWatchNotificationRevision(entry, entry.payload.copy(text = "New body")))
        assertFalse(matchesWatchNotificationRevision(entry, entry.payload.copy(time = 101)))
        assertFalse(matchesWatchNotificationRevision(entry.copy(payload = entry.payload.copy(notificationKey = "")), entry.payload.copy(notificationKey = "")))
    }

    @Test fun clearAllRetainsOnlyMatchingProtectedWatchAlertsAndAlwaysClearsPhoneCopies() {
        val entry = watchEntry()
        val protected = listOf(entry.payload)
        assertFalse(canClearInboxEntry(entry, protected))
        assertTrue(canClearInboxEntry(entry.copy(source = "phone"), protected))
        assertTrue(canClearInboxEntry(entry.copy(payload = entry.payload.copy(text = "Old history")), protected))
        assertTrue(canClearInboxEntry(entry.copy(payload = entry.payload.copy(time = 99)), protected))
        assertTrue(canClearInboxEntry(entry, emptyList()))
    }

    @Test fun clearAllBlocksQueuedOlderRevisionsButRetainsUpdatesAndNewSourceKeys() {
        val payload = watchEntry().payload
        val gate = NotificationDismissalGate()
        gate.mark(payload)
        assertTrue(gate.shouldIgnore(payload))
        assertTrue(gate.shouldIgnore(payload.copy(time = 99)))
        assertFalse(gate.shouldIgnore(payload.copy(time = 101)))
        assertFalse(gate.shouldIgnore(payload.copy(text = "A new transfer in the same millisecond")))
        assertFalse(gate.shouldIgnore(payload.copy(notificationKey = "new-transfer-key")))
        assertFalse(gate.shouldIgnore(payload.copy(packageName = "another.app")))
        gate.mark(payload.copy(time = 99))
        assertTrue(gate.shouldIgnore(payload))
        gate.remove(payload.packageName, payload.notificationKey)
        assertFalse(gate.shouldIgnore(payload))
    }

    @Test fun capturedActionsExpireAndClockChangesCannotExtendLifetime() {
        val entry = watchEntry()
        assertTrue(retainedWatchActionIsUsable(entry, entry.payload, 500, 500))
        assertTrue(retainedWatchActionIsUsable(entry, entry.payload, 500, 500 + RETAINED_WATCH_ACTION_LIFETIME_MS))
        assertFalse(retainedWatchActionIsUsable(entry, entry.payload, 500, 501 + RETAINED_WATCH_ACTION_LIFETIME_MS))
        assertFalse(retainedWatchActionIsUsable(entry, entry.payload, 500, 499))
        assertFalse(retainedWatchActionIsUsable(entry, entry.payload.copy(text = "New revision"), 500, 600))
    }

    private fun watchEntry() = NotificationInboxEntry(1,
        NotificationPayload("bluetooth.app", "Bluetooth", "File received", "photo.jpg", time = 100, notificationKey = "transfer-key"),
        receivedAt = 100, isRead = false, replyRequested = false, source = "watch")
}

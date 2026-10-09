package com.healthsync.watch.notification

import com.google.gson.Gson
import com.healthsync.watch.data.NotificationPayload
import org.junit.Assert.*
import org.junit.Test

class NotificationAlertRulesTest {
    private val message = NotificationPayload("chat.app", "Chat", "Alice", "Hello", time = 100,
        notificationKey = "conversation", canReply = true)

    @Test fun legacyPhonePayloadKeepsReplyActiveAndNormalizesAddedStrings() {
        val parsed = parseNotificationPayload("""{"packageName":"chat.app","appLabel":"Chat","title":"Alice","text":"Hi","canReply":true,"notificationKey":"key"}""", Gson())
        assertTrue(parsed.isActive)
        assertEquals("", parsed.category)
        assertEquals("", parsed.conversationTitle)
        assertTrue(canReplyToInboxEntry(NotificationInboxEntry(1, parsed, 100, false, false), 200))
        val inactive = parseNotificationPayload("""{"packageName":"chat.app","appLabel":"Chat","title":"Alice","text":"Hi","isActive":false}""", Gson())
        assertFalse(inactive.isActive)
    }

    @Test fun timestampAndReplyCapabilityRefreshesDoNotAlertTwice() {
        assertTrue(sameNotificationContent(message, message.copy(time = 200, canReply = false, isSilent = true)))
        assertFalse(sameNotificationContent(message, message.copy(text = "Next message")))
        assertFalse(sameNotificationContent(message, message.copy(conversationTitle = "Another group")))
        assertFalse(sameNotificationContent(message, message.copy(notificationKey = "other")))
    }

    @Test fun reconnectsSilentProgressAndCallsNeverInterrupt() {
        assertTrue(shouldInterruptForNotification(message, changed = true, callActive = false))
        assertFalse(shouldInterruptForNotification(message, changed = false, callActive = false))
        assertFalse(shouldInterruptForNotification(message.copy(isSilent = true), true, false))
        assertFalse(shouldInterruptForNotification(message.copy(isOngoing = true), true, false))
        assertFalse(shouldInterruptForNotification(message.copy(isActive = false), true, false))
        assertTrue(shouldInterruptForNotification(message.copy(category = "call", isOngoing = true), true, false))
        assertFalse(shouldInterruptForNotification(message.copy(category = "call"), true, false, NotificationInboxStore.SOURCE_WATCH))
        assertFalse(shouldInterruptForNotification(message, true, true))
    }

    @Test fun legacyWatchPopupRequiresUnlockedAllowedPreference() {
        assertTrue(canAutomaticallyShowNotification(26, false, false, false, true, true))
        assertFalse(canAutomaticallyShowNotification(26, false, false, true, true, true))
        assertFalse(canAutomaticallyShowNotification(26, false, false, false, true, false))
        assertFalse(canAutomaticallyShowNotification(26, false, false, false, false, true))
    }

    @Test fun modernWatchUsesHeadsUpUnlessAnActivityIsInteractive() {
        assertFalse(canAutomaticallyShowNotification(34, false, false, false, true, true))
        assertFalse(canAutomaticallyShowNotification(34, true, false, false, true, true))
        assertTrue(canAutomaticallyShowNotification(34, true, true, false, true, true))
    }

    @Test fun removedPhoneAlertAndUnconfirmedReplyCannotBeRepliedAgain() {
        val saved = NotificationInboxEntry(1, message, 100, false, false)
        assertFalse(canReplyToInboxEntry(saved.copy(payload = message.copy(isActive = false)), 200))
        val pending = saved.copy(replyRequested = true, replyState = "pending", replyRequestId = 7)
        assertFalse(canReplyToInboxEntry(pending, 200))
        assertFalse(canReplyToInboxEntry(pending.copy(replyState = "unknown"), 200))
        assertEquals("Reply not confirmed. Check your phone.", notificationReplyStatus(pending.copy(replyState = "unknown")))
        assertEquals("Reply sent to app", notificationReplyStatus(pending.copy(replyState = "sent")))
    }

    @Test fun confirmationsMatchOnlyTheirOriginalPhoneReplyAttempt() {
        val pending = NotificationInboxEntry(1, message, 100, false, true, replyState = "pending", replyRequestId = 7)
        assertTrue(replyResultMatches(pending, 7, "conversation"))
        assertTrue(replyResultMatches(pending.copy(replyState = "unknown"), 7, "conversation"))
        assertFalse(replyResultMatches(pending, 8, "conversation"))
        assertFalse(replyResultMatches(pending, 7, "other"))
        assertFalse(replyResultMatches(pending.copy(replyState = "sent"), 7, "conversation"))
        assertFalse(replyResultMatches(pending.copy(source = "watch"), 7, "conversation"))
    }
}

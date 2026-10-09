package com.healthsync.watch.notification

import com.healthsync.watch.data.NotificationPayload
import org.junit.Assert.*
import org.junit.Test

class NotificationInboxRulesTest {
    @Test fun versionOnePhoneIdentityRemainsStableAfterSourceMigration() {
        // Frozen v1 identity: existing persisted rows must remain update targets after adding source.
        assertEquals("2693e380376935dd5872845d77a7e2a0568ff4982553ecc77f3a69315bc41d3c",
            notificationInboxIdentity("chat.app", "conversation-1", "Alice", "A new message", 500, "phone"))
    }

    @Test fun notificationUpdatesKeepIdentityButDifferentAppsRemainSeparate() {
        val original = notificationInboxIdentity("chat.app", "conversation-1", "Alice", "Hello", 100)
        assertEquals(original, notificationInboxIdentity("chat.app", "conversation-1", "Alice", "New message", 200))
        assertNotEquals(original, notificationInboxIdentity("other.app", "conversation-1", "Alice", "Hello", 100))
    }

    @Test fun legacyNotificationsWithSameTitleAreNotMergedAcrossMessages() {
        val original = notificationInboxIdentity("chat.app", "", "Alice", "Hello", 100)
        assertNotEquals(original, notificationInboxIdentity("chat.app", "", "Alice", "Hello", 200))
        assertNotEquals(original, notificationInboxIdentity("chat.app", "", "Alice", "Different message", 100))
    }

    @Test fun offlineHistoryCannotProvideAmbiguousOrExpiredReplyTargets() {
        val entry = NotificationInboxEntry(1, NotificationPayload("chat.app", "Chat", "Alice", "Hi",
            time = 100, canReply = true, notificationKey = "exact-phone-key"), 100, false, false)
        assertTrue(canReplyToInboxEntry(entry, 200))
        assertFalse(canReplyToInboxEntry(entry.copy(payload = entry.payload.copy(notificationKey = "")), 200))
        assertFalse(canReplyToInboxEntry(entry.copy(replyRequested = true), 200))
        assertFalse(canReplyToInboxEntry(entry, 100 + 24 * 60 * 60 * 1000L + 1))
        assertFalse(canReplyToInboxEntry(entry, 99))
    }

    @Test fun sameKeyWithANewMessageCannotReuseAnOldReplyScreen() {
        val displayed = NotificationInboxEntry(1, NotificationPayload("chat.app", "Chat", "Alice", "Hi",
            time = 100, canReply = true, notificationKey = "conversation-key"), 100, false, false)
        assertTrue(isCurrentInboxReplyTarget(displayed, displayed.copy(isRead = true), 200))
        assertFalse(isCurrentInboxReplyTarget(displayed, null, 200))
        assertFalse(isCurrentInboxReplyTarget(displayed, displayed.copy(payload = displayed.payload.copy(text = "New question")), 200))
        assertFalse(isCurrentInboxReplyTarget(displayed, displayed.copy(payload = displayed.payload.copy(time = 150)), 200))
        assertFalse(isCurrentInboxReplyTarget(displayed, displayed.copy(replyRequested = true), 200))
        assertFalse(isCurrentInboxReplyTarget(displayed, displayed.copy(id = 2), 200))
    }

    @Test fun nativeAndPhoneNotificationsWithTheSameKeyNeverShareIdentity() {
        val phone = notificationInboxIdentity("chat.app", "conversation", "Alice", "Hi", 100)
        assertEquals(phone, notificationInboxIdentity("chat.app", "conversation", "Alice", "New", 200, "phone"))
        val watch = notificationInboxIdentity("chat.app", "conversation", "Alice", "Hi", 100, "watch")
        assertNotEquals(phone, watch)
        assertEquals(watch, notificationInboxIdentity("chat.app", "conversation", "Alice", "New", 200, "watch"))
    }

    @Test fun localWatchNotificationCanNeverBecomeABluetoothReplyTarget() {
        val phone = NotificationInboxEntry(1, NotificationPayload("chat.app", "Chat", "Alice", "Hi",
            time = 100, canReply = true, notificationKey = "shared-key"), 100, false, false)
        val watch = phone.copy(source = "watch")
        assertFalse(canReplyToInboxEntry(watch, 200))
        assertFalse(isCurrentInboxReplyTarget(phone, watch, 200))
        assertFalse(isCurrentInboxReplyTarget(watch, phone, 200))
    }
}

package com.healthsync.phone.service

import org.junit.Assert.*
import org.junit.Test

class NotificationMirrorPolicyTest {
    @Test fun latestMessagingStyleMessageKeepsSenderAndConversation() {
        val content = NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(
            title = "Study group", text = "2 new messages", conversationTitle = "Study group",
            messages = listOf(NotificationMirrorPolicy.Message("Earlier", "Alice"),
                NotificationMirrorPolicy.Message("Meet at six", "Bob"))), "Messages")!!
        assertEquals("Bob", content.title)
        assertEquals("Meet at six", content.text)
        assertEquals("Study group", content.conversationTitle)
    }

    @Test fun expandedTextWinsAndBlankExpandedTextFallsBack() {
        assertEquals("Whole message", NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(
            title = "Mail", bigText = "Whole message", text = "Preview"), "Mail")!!.text)
        assertEquals("Preview", NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(
            title = "Mail", bigText = "  ", text = "Preview"), "Mail")!!.text)
    }

    @Test fun inboxLinesUseRecentMessagesAndTickerIsLastFallback() {
        val source = NotificationMirrorPolicy.Source(lines = (1..15).map { "Message $it" })
        val content = NotificationMirrorPolicy.extract(source, "Mail")!!
        assertTrue(content.text.startsWith("Message 4\n"))
        assertTrue(content.text.endsWith("Message 15"))
        assertEquals("Delivery ready", NotificationMirrorPolicy.extract(
            NotificationMirrorPolicy.Source(ticker = "Delivery ready"), "Shop")!!.text)
    }

    @Test fun summaryAndEmptyNoiseAreNotMirrored() {
        assertNull(NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(title = "3 new messages",
            groupSummary = true), "Messages"))
        assertNull(NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(text = "\u0000  "), "App"))
        assertNull(NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(
            messages = listOf(NotificationMirrorPolicy.Message("\u0000"))), "Messages"))
    }

    @Test fun attachmentOnlyMessageIsNotLost() {
        val content = NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(
            messages = listOf(NotificationMirrorPolicy.Message(null, "Alice", attachment = true))), "Messages")!!
        assertEquals("Alice", content.title)
        assertEquals("Attachment", content.text)
    }

    @Test fun boundedTextDoesNotSplitEmojiOrKeepControlCharacters() {
        assertEquals("Hello", NotificationMirrorPolicy.bounded("Hello\u0000", 20))
        assertEquals("a", NotificationMirrorPolicy.bounded("a\uD83D\uDE00", 2))
        val content = NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(title = "t".repeat(700),
            bigText = "b".repeat(8_000)), "Mail")!!
        assertEquals(500, content.title.length)
        assertEquals(5_000, content.text.length)
    }

    @Test fun identicalUpdatesAreDroppedButReplyAvailabilityRefreshesSilently() {
        val previous = NotificationMirrorPolicy.Snapshot("Message", "reply=true")
        assertFalse(decide(previous, previous).forward)
        val refreshed = decide(previous, previous.copy(metadata = "reply=false"))
        assertTrue(refreshed.forward)
        assertTrue(refreshed.silent)
        assertFalse(refreshed.saveHistory)
    }

    @Test fun changedConversationAlertsAndOngoingUpdatesStayQuiet() {
        val previous = NotificationMirrorPolicy.Snapshot("Earlier", "reply=true")
        val current = previous.copy(content = "New message")
        assertFalse(decide(previous, current).silent)
        assertTrue(decide(previous, current).saveHistory)
        assertTrue(decide(previous, current, ongoing = true).silent)
        assertTrue(decide(previous, current, onlyAlertOnce = true).silent)
        assertTrue(decide(null, current, sourceSilent = true).silent)
    }

    @Test fun reconnectReplaysIdenticalActiveContentWithoutAlertOrHistoryDuplicate() {
        val current = NotificationMirrorPolicy.Snapshot("Message", "reply=true")
        val decision = decide(current, current, replay = true)
        assertTrue(decision.forward)
        assertTrue(decision.silent)
        assertFalse(decision.saveHistory)
        assertEquals(setOf("ended", "filtered"), NotificationMirrorPolicy.staleKeys(
            setOf("active", "ended", "filtered"), setOf("active")))
    }

    @Test fun repliesRequireValidSourceAndIdentifiableLiveConversation() {
        assertNull(NotificationMirrorPolicy.replyError("chat.app", "Yes", "", "key"))
        assertNotNull(NotificationMirrorPolicy.replyError("chat.app", "Yes", "", ""))
        assertNotNull(NotificationMirrorPolicy.replyError("chat.app", " ", "Alice", ""))
        assertNotNull(NotificationMirrorPolicy.replyError("chat.app", "a".repeat(2_001), "Alice", "key"))
        assertNull(NotificationMirrorPolicy.replyError("chat.app", "Yes", "Alice", "k".repeat(2_048)))
        assertNotNull(NotificationMirrorPolicy.replyError("chat.app", "Yes", "Alice", "k".repeat(2_049)))
    }

    @Test fun canonicalCallRemovesDuplicateDialerAlertButKeepsVoipAndPermissionFallback() {
        assertFalse(NotificationMirrorPolicy.shouldMirrorDialerCall(true, true, true))
        assertTrue(NotificationMirrorPolicy.shouldMirrorDialerCall(true, false, true))
        assertTrue(NotificationMirrorPolicy.shouldMirrorDialerCall(true, true, false))
        assertTrue(NotificationMirrorPolicy.shouldMirrorDialerCall(false, true, true))
    }

    private fun decide(previous: NotificationMirrorPolicy.Snapshot?, current: NotificationMirrorPolicy.Snapshot,
                       replay: Boolean = false, sourceSilent: Boolean = false, ongoing: Boolean = false,
                       onlyAlertOnce: Boolean = false) = NotificationMirrorPolicy.decide(
        previous, current, replay, sourceSilent, ongoing, onlyAlertOnce)
}

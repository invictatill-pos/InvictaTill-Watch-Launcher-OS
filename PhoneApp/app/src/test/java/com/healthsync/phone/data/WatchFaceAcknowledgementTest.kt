package com.healthsync.phone.data

import com.healthsync.phone.data.model.WatchFaceAckPayload
import com.healthsync.phone.data.watchface.faceAcknowledgementMatches
import org.junit.Assert.*
import org.junit.Test

class WatchFaceAcknowledgementTest {
    @Test fun aLateReplyCannotConfirmANewInstallation() {
        val ack = WatchFaceAckPayload("face", true, requestId = "first-request")
        assertFalse(faceAcknowledgementMatches("face", "second-request", 26, ack))
        assertTrue(faceAcknowledgementMatches("face", "first-request", 26, ack))
        assertFalse(faceAcknowledgementMatches("other-face", "first-request", 26, ack))
    }

    @Test fun legacyReplyRequiresKnownLegacyVersion() {
        val ack = WatchFaceAckPayload("face", true)
        assertTrue(faceAcknowledgementMatches("face", "request", 25, ack))
        assertFalse(faceAcknowledgementMatches("face", "request", 26, ack))
        assertFalse(faceAcknowledgementMatches("face", "request", null, ack))
        assertFalse(faceAcknowledgementMatches("face", "request", 0, ack))
    }
}

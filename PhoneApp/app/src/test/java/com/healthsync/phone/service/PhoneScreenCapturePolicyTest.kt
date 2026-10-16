package com.healthsync.phone.service

import com.healthsync.phone.data.model.PhoneScreenProtocol
import org.junit.Assert.*
import org.junit.Test

class PhoneScreenCapturePolicyTest {
    @Test fun canceledGenerationRejectsDelayedActivationAfterConsentWasConsumed() {
        val consent = PhoneScreenConsentPolicy()
        val session = PhoneScreenSessionPolicy()
        consent.begin("approval", "watch-a")
        val generationBeforeConsume = session.snapshotGeneration()
        assertTrue(consent.consume("approval", "watch-a"))
        session.invalidate()
        assertFalse(session.activate("old-capture", "watch-a", generationBeforeConsume))
        assertFalse(session.isActive("old-capture", true, "watch-a"))
    }

    @Test fun consentKeepsOriginalEpochIfStopInvalidatesSessionBeforeConsentCleanup() {
        val consent = PhoneScreenConsentPolicy()
        val session = PhoneScreenSessionPolicy()
        consent.begin("old-approval", "watch-a", session.snapshotGeneration())
        session.invalidate() // Stop's immediate gate revocation occurs before pending-consent cleanup.
        val approvedGeneration = consent.generationFor("old-approval")!!
        assertTrue(consent.consume("old-approval", "watch-a"))
        assertFalse(session.activate("old-capture", "watch-a", approvedGeneration))
        assertFalse(session.isActive("old-capture", true, "watch-a"))
        consent.begin("fresh-approval", "watch-a", session.snapshotGeneration())
        val freshGeneration = consent.generationFor("fresh-approval")!!
        assertTrue(consent.consume("fresh-approval", "watch-a"))
        assertTrue(session.activate("fresh-capture", "watch-a", freshGeneration))
    }

    @Test fun revocationRejectsFinishedEncodingAndSamePeerReconnectWithoutFreshApproval() {
        val session = PhoneScreenSessionPolicy()
        val originalGeneration = session.snapshotGeneration()
        assertTrue(session.activate("old-capture", "watch-a", originalGeneration))
        assertTrue(session.isActive("old-capture", true, "watch-a"))
        session.invalidate() // Stop/disconnect while the worker still encodes the old frame.
        assertFalse(session.isActive("old-capture", true, "watch-a"))
        assertFalse(session.activate("old-capture", "watch-a", originalGeneration))
        assertFalse(session.isActive("old-capture", true, "watch-b"))
    }

    @Test fun activeSessionRequiresItsOriginalConnectedPeer() {
        val session = PhoneScreenSessionPolicy()
        assertFalse(session.activate("capture", null, session.snapshotGeneration()))
        assertTrue(session.activate("capture", "watch-a", session.snapshotGeneration()))
        assertFalse(session.isActive("capture", false, "watch-a"))
        assertFalse(session.isActive("capture", true, "watch-b"))
        assertFalse(session.isActive("capture", true, null))
        assertFalse(session.isActive("another-capture", true, "watch-a"))
        assertTrue(session.isActive("capture", true, "watch-a"))
    }

    @Test fun lateOldStopOrCleanupCannotClearANewerApprovedSession() {
        val session = PhoneScreenSessionPolicy()
        assertTrue(session.activate("old", "watch-a", session.snapshotGeneration()))
        session.invalidate()
        assertTrue(session.activate("new", "watch-b", session.snapshotGeneration()))
        assertFalse(session.deactivateOwned("old"))
        assertTrue(session.isActive("new", true, "watch-b"))
        assertTrue(session.deactivateOwned("new"))
        assertFalse(session.isActive("new", true, "watch-b"))
    }

    @Test fun consentCannotSurviveStopDisconnectPeerChangeOrASecondAttempt() {
        val consent = PhoneScreenConsentPolicy()
        consent.begin("old", "watch-a")
        assertFalse(consent.consume("old", "watch-b"))
        consent.invalidate()
        assertFalse(consent.consume("old", "watch-a"))
        consent.begin("new", "watch-a")
        assertFalse(consent.consume("old", "watch-a"))
        assertTrue(consent.consume("new", "watch-a"))
        assertFalse(consent.consume("new", "watch-a"))
    }

    @Test fun handshakeAndFrameAcksBoundTransferToOneImage() {
        val gate = PhoneScreenFlowControl("one", 100L)
        assertNull(gate.reserveFrame(2_000L))
        assertFalse(gate.acknowledge("old-session", 0L, 1_000L))
        assertFalse(gate.acknowledge("one", 1L, 1_000L))
        assertTrue(gate.acknowledge("one", 0L, 1_000L))
        assertEquals(1L, gate.reserveFrame(2_000L))
        assertNull(gate.reserveFrame(4_000L))
        assertFalse(gate.acknowledge("one", 0L, 3_000L))
        assertTrue(gate.acknowledge("one", 1L, 3_000L))
        assertEquals(2L, gate.reserveFrame(4_000L))
        assertFalse(gate.acknowledge("one", 1L, 4_001L))
    }

    @Test fun handshakeAndUnacknowledgedImageExpire() {
        val gate = PhoneScreenFlowControl("one", 10L)
        assertFalse(gate.timedOut(8_009L))
        assertTrue(gate.timedOut(8_010L))
        assertFalse(gate.acknowledge("one", 0L, 8_010L))
        assertNull(gate.reserveFrame(100_000L))
        val image = PhoneScreenFlowControl("two", 10L)
        assertTrue(image.acknowledge("two", 0L, 100L))
        assertFalse(image.timedOut(100_000L))
        image.reserveFrame(100_000L)
        assertFalse(image.timedOut(107_999L))
        assertTrue(image.timedOut(108_000L))
        assertFalse(image.acknowledge("two", 1L, 108_000L))
        assertNull(image.reserveFrame(109_000L))
    }

    @Test fun fastAcksStillRespectFrameInterval() {
        val gate = PhoneScreenFlowControl("one", 0L)
        gate.acknowledge("one", 0L, 10L)
        assertNull(gate.reserveFrame(1_499L))
        assertEquals(1L, gate.reserveFrame(1_500L))
        gate.acknowledge("one", 1L, 1_600L)
        assertNull(gate.reserveFrame(2_999L))
        assertEquals(2L, gate.reserveFrame(3_000L))
    }

    @Test fun portraitLandscapeAndExtremeSizesHaveBoundedSurfaces() {
        for ((width, height) in listOf(1080 to 2400, 2400 to 1080, 4096 to 4096, 1 to Int.MAX_VALUE, 480 to 320)) {
            val size = PhoneScreenCapturePolicy.boundedSize(width, height)
            assertTrue(size.width in 1..PhoneScreenProtocol.MAX_EDGE)
            assertTrue(size.height in 1..PhoneScreenProtocol.MAX_EDGE)
            assertTrue(size.width.toLong() * size.height <= PhoneScreenProtocol.MAX_PIXELS)
            assertTrue(size.width <= width && size.height <= height)
        }
        assertEquals(PhoneCaptureSize(480, 320), PhoneScreenCapturePolicy.boundedSize(480, 320))
        assertEquals(PhoneCaptureSize(432, 960), PhoneScreenCapturePolicy.boundedSize(1080, 2400))
        assertEquals(PhoneCaptureSize(960, 432), PhoneScreenCapturePolicy.boundedSize(2400, 1080))
    }
}

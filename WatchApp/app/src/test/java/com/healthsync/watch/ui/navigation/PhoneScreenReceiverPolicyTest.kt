package com.healthsync.watch.ui.navigation

import com.healthsync.watch.data.PhoneScreenPayload
import com.healthsync.watch.data.PhoneScreenProtocol
import org.junit.Assert.*
import org.junit.Test

class PhoneScreenReceiverPolicyTest {
    private val session = "10000000-0000-0000-0000-000000000001"
    private val other = "10000000-0000-0000-0000-000000000002"
    private fun start(id: String = session) = PhoneScreenPayload(kind = "start", sessionId = id)
    private fun frame(sequence: Long = 1L, id: String = session) = PhoneScreenPayload(
        kind = "frame", sessionId = id, sequence = sequence, width = 320, height = 640, imageBase64 = "jpeg")
    private fun ready() = PhoneScreenReceiverPolicy().apply { connectionChanged(true); assertTrue(start(start())) }

    @Test fun framesRequireActiveConnectedSessionAndMonotonicSequence() {
        val policy = PhoneScreenReceiverPolicy()
        assertFalse(policy.start(start()))
        assertNull(policy.beginFrame(frame()))
        policy.connectionChanged(true)
        assertTrue(policy.start(start()))
        assertNull(policy.beginFrame(frame(id = other)))
        assertNull(policy.beginFrame(frame(0)))
        val first = requireNotNull(policy.beginFrame(frame()))
        assertTrue(policy.commitFrame(first, 100))
        assertNull(policy.beginFrame(frame()))
        assertNull(policy.beginFrame(frame(0)))
        assertNotNull(policy.beginFrame(frame(2)))
    }

    @Test fun duplicateStartAcknowledgesWithoutMakingOldPixelsFreshOrRewindingSequence() {
        val policy = ready()
        assertTrue(policy.commitFrame(requireNotNull(policy.beginFrame(frame(4))), 1_000))
        assertTrue(policy.start(start()))
        assertEquals(4L, policy.lastSequence)
        assertEquals(1_000L, policy.frameReceivedAt)
        assertFalse(policy.isFresh(1_000 + PhoneScreenProtocol.STALE_AFTER_MS))
    }

    @Test fun stopDuringDecodePreventsResurrectionAndDelayedStart() {
        val policy = ready()
        val inFlight = requireNotNull(policy.beginFrame(frame()))
        assertTrue(policy.stop(PhoneScreenPayload(kind = "stop", sessionId = session)))
        assertFalse(policy.commitFrame(inFlight, 100))
        assertNull(policy.sessionId)
        assertNull(policy.frameReceivedAt)
        assertFalse(policy.start(start()))
    }

    @Test fun disconnectDuringDecodeErasesSessionAndRequiresNewConsentSession() {
        val policy = ready()
        val inFlight = requireNotNull(policy.beginFrame(frame()))
        policy.connectionChanged(false)
        assertFalse(policy.commitFrame(inFlight, 100))
        assertNull(policy.sessionId)
        policy.connectionChanged(true)
        assertFalse(policy.start(start()))
        assertTrue(policy.start(start(other)))
        assertFalse(policy.commitFrame(inFlight, 200))
    }

    @Test fun switchingSessionRejectsOldInFlightFrameAndForeignStop() {
        val policy = ready()
        val inFlight = requireNotNull(policy.beginFrame(frame()))
        assertTrue(policy.start(start(other)))
        assertFalse(policy.commitFrame(inFlight, 100))
        assertFalse(policy.stop(PhoneScreenPayload(kind = "stop", sessionId = session)))
        assertEquals(other, policy.sessionId)
        assertNull(policy.beginFrame(frame(2)))
        assertNotNull(policy.beginFrame(frame(1, other)))
    }

    @Test fun localStopAlwaysClearsStateEvenWithoutTransport() {
        val policy = ready()
        assertTrue(policy.commitFrame(requireNotNull(policy.beginFrame(frame())), 100))
        assertEquals(session, policy.stopLocally())
        assertNull(policy.sessionId)
        assertFalse(policy.isFresh(101))
        assertNull(policy.stopLocally())
    }

    @Test fun freshFrameExpiresAtSixSecondsAndClockReversalCannotExtendIt() {
        val policy = ready()
        assertFalse(policy.isFresh(100))
        policy.commitFrame(requireNotNull(policy.beginFrame(frame())), 1_000)
        assertTrue(policy.isFresh(1_000))
        assertTrue(policy.isFresh(6_999))
        assertFalse(policy.isFresh(7_000))
        assertFalse(policy.isFresh(999))
    }

    @Test fun malformedMetadataIsRejectedBeforeDecode() {
        val policy = ready()
        assertNull(policy.beginFrame(frame().copy(width = 0)))
        assertNull(policy.beginFrame(frame().copy(width = PhoneScreenProtocol.MAX_EDGE + 1)))
        assertNull(policy.beginFrame(frame().copy(width = 960, height = 960)))
        assertNull(policy.beginFrame(frame().copy(imageBase64 = "x".repeat(PhoneScreenProtocol.MAX_BASE64_CHARS + 1))))
        assertNull(policy.beginFrame(frame().copy(imageBase64 = "")))
        assertFalse(policy.start(start().copy(sequence = 2)))
        assertFalse(policy.start(start().copy(sessionId = "broken")))
    }

    @Test fun onlyBoundedCompleteJpegBytesCanReachBoundsDecode() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        assertTrue(validPhoneJpegBytes(jpeg))
        assertFalse(validPhoneJpegBytes(byteArrayOf()))
        assertFalse(validPhoneJpegBytes(jpeg.copyOf(3)))
        assertFalse(validPhoneJpegBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertFalse(validPhoneJpegBytes(jpeg.copyOf(PhoneScreenProtocol.MAX_IMAGE_BYTES + 1)))
    }

    @Test fun decodedBoundsMustMatchDeclaredFrameAndJpegMimeBeforePixelAllocation() {
        assertTrue(validPhoneFrameBounds(320, 640, 320, 640, "image/jpeg"))
        assertFalse(validPhoneFrameBounds(320, 640, 640, 320, "image/jpeg"))
        assertFalse(validPhoneFrameBounds(320, 640, 320, 640, "image/png"))
        assertFalse(validPhoneFrameBounds(320, 640, 320, 640, null))
        assertFalse(validPhoneFrameBounds(0, 640, 0, 640, "image/jpeg"))
        assertFalse(validPhoneFrameBounds(960, 960, 960, 960, "image/jpeg"))
        assertFalse(validPhoneFrameBounds(961, 1, 961, 1, "image/jpeg"))
    }
}

package com.healthsync.watch.data

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class PhoneScreenProtocolTest {
    private val session = "01234567-89ab-cdef-0123-456789abcdef"
    private fun frame() = PhoneScreenPayload("frame", session, 1L, 480, 800, "a".repeat(64))

    @Test fun imageDimensionsAndEncodedDataAreBoundedBeforeDecode() {
        assertTrue(PhoneScreenProtocol.validFrame(frame()))
        assertTrue(PhoneScreenProtocol.validFrame(frame().copy(width = 960, height = 666,
            imageBase64 = "a".repeat(PhoneScreenProtocol.MAX_BASE64_CHARS))))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(width = 960, height = 667)))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(width = 961)))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(height = 0)))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(imageBase64 = "")))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(imageBase64 = "a".repeat(PhoneScreenProtocol.MAX_BASE64_CHARS + 1))))
    }

    @Test fun frameNeedsPositiveSequenceAndValidSession() {
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(sequence = 0)))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(sequence = -1)))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(sessionId = "")))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(sessionId = "other-session")))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(kind = "ack")))
        assertFalse(PhoneScreenProtocol.validFrame(frame().copy(message = "a".repeat(161))))
    }

    @Test fun controlCannotSmuggleImageBytesOrUnknownCommands() {
        val start = PhoneScreenPayload("start", session)
        assertTrue(PhoneScreenProtocol.validControl(start))
        assertTrue(PhoneScreenProtocol.validControl(start.copy(kind = "ack", sequence = 1)))
        assertTrue(PhoneScreenProtocol.validControl(start.copy(kind = "stop")))
        assertFalse(PhoneScreenProtocol.validControl(start.copy(kind = "launch")))
        assertFalse(PhoneScreenProtocol.validControl(start.copy(imageBase64 = "abcd")))
        assertFalse(PhoneScreenProtocol.validControl(start.copy(width = 1)))
        assertFalse(PhoneScreenProtocol.validControl(start.copy(sequence = -1)))
        assertFalse(PhoneScreenProtocol.validControl(start.copy(sessionId = "")))
    }

    @Test fun requestCanReconcileExistingSessionWithoutStartingCapture() {
        assertTrue(PhoneScreenProtocol.validControl(PhoneScreenPayload("request")))
        assertTrue(PhoneScreenProtocol.validControl(PhoneScreenPayload("request", session)))
        assertFalse(PhoneScreenProtocol.validControl(PhoneScreenPayload("request", "bad")))
    }

    @Test fun wirePayloadRoundTripsAndNullFieldsAreRejected() {
        val gson = Gson()
        assertEquals(frame(), gson.fromJson(gson.toJson(frame()), PhoneScreenPayload::class.java))
        val malformed = gson.fromJson("""{"kind":"frame","sessionId":null,"imageBase64":null}""", PhoneScreenPayload::class.java)
        assertFalse(PhoneScreenProtocol.validFrame(malformed))
        val empty = gson.fromJson("{}", PhoneScreenPayload::class.java)
        assertFalse(PhoneScreenProtocol.validFrame(empty))
        assertFalse(PhoneScreenProtocol.validControl(empty))
    }

    @Test fun legacyDeviceInfoCannotEnableScreenSharing() {
        val legacy = Gson().fromJson("""{"versionName":"2.9.4","versionCode":32}""", DeviceInfoPayload::class.java)
        assertFalse(legacy.supportsPhoneScreenShare)
    }
}

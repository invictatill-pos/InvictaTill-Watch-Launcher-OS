package com.healthsync.phone.data

import com.healthsync.phone.data.model.HeartRateStatusPayload
import org.junit.Assert.*
import org.junit.Test

class HeartRateFeedbackTest {
    @Test fun noEventsAndRejectedSamplesRemainFeedbackWithoutARecordedValue() {
        val now = 1_000_000L
        for (status in listOf("no_events", "invalid_samples")) {
            assertEquals(status, validatedHeartRateStatus(HeartRateStatusPayload(status), now))
        }
        assertEquals("Sensor sent no data · open Health on watch",
            heartRateFeedbackText(HeartRateFeedback("no_events", now), true, null, now))
        assertEquals("Sensor data rejected · check measurement details",
            heartRateFeedbackText(HeartRateFeedback("invalid_samples", now), true, null, now))
    }

    @Test fun diagnosticReportIsOptionalBoundedAndCannotCarryControlCharacters() {
        assertNull(validatedHeartRateDiagnosticReport(null)) // Older watch protocol.
        assertNull(validatedHeartRateDiagnosticReport("  "))
        assertEquals("Model: U8\nNo events", validatedHeartRateDiagnosticReport(" Model: U8\nNo events "))
        assertEquals(8192, validatedHeartRateDiagnosticReport("x".repeat(8192))?.length)
        assertNull(validatedHeartRateDiagnosticReport("x".repeat(8193)))
        assertNull(validatedHeartRateDiagnosticReport("Model\u0000U8"))
        assertNull(validatedHeartRateDiagnosticReport("Model\u007fU8"))
        // A malformed diagnostic does not invalidate the independent device status.
        assertEquals("no_events", validatedHeartRateStatus(HeartRateStatusPayload("no_events",
            diagnosticReport = "bad\u0000report"), 1_000_000L))
    }

    @Test fun statusCannotManufactureAReadingOrAcceptCorruptDeviceData() {
        val now = 1_000_000L
        assertNull(validatedHeartRateStatus(HeartRateStatusPayload("reading", -1, 0L), now))
        assertNull(validatedHeartRateStatus(HeartRateStatusPayload("reading", 75, now + 900_000L), now))
        assertNull(validatedHeartRateStatus(HeartRateStatusPayload("unknown", 75, now), now))
        assertEquals("reading", validatedHeartRateStatus(HeartRateStatusPayload("reading", 75, now - 300_000L), now))
        assertEquals("permission_required", validatedHeartRateStatus(HeartRateStatusPayload("permission_required"), now))
    }

    @Test fun pendingRequestsTimeOutAndDisconnectDoesNotPretendMeasurementIsRunning() {
        val feedback = HeartRateFeedback("requesting", 100_000L)
        assertEquals("Request sent · waiting for watch", heartRateFeedbackText(feedback, true, null, 101_000L))
        assertEquals("No response · check Health on watch", heartRateFeedbackText(feedback, true, null, 200_000L))
        assertEquals("Connect watch to measure", heartRateFeedbackText(feedback, false, null, 101_000L))
    }

    @Test fun savedMeasurementKeepsItsOriginalAgeAndNoContactHasAnActionableReason() {
        assertEquals("Last reading · 5 min ago", heartRateFeedbackText(HeartRateFeedback("reading", 999_000L), true, 700_000L, 1_000_000L))
        assertEquals("No reading · adjust watch and retry", heartRateFeedbackText(HeartRateFeedback("no_contact", 999_000L), true, null, 1_000_000L))
    }

    @Test fun oldStatusPayloadsHaveNoPreviewAndMissingOptionalFieldsKeepTheirDefaults() {
        val now = 1_000_000L
        assertEquals(HeartRateFeedback("measuring", now),
            heartRateFeedbackFromJson("""{"status":"measuring"}""", now))
        assertEquals(HeartRateFeedback("reading", now),
            heartRateFeedbackFromJson("""{"status":"reading","bpm":75,"readingTime":900000}""", now))
        assertNull(heartRateFeedbackFromJson("""{"status":"unreliable_accuracy"}""", now).sensorPreview)
    }

    @Test fun unverifiedSensorValueShowsItsOwnAgeAndCannotPassHistoryValidation() {
        val now = 1_000_000L
        val feedback = heartRateFeedbackFromJson(
            """{"status":"unreliable_accuracy","sensorBpm":75,"sensorReadingTime":990000}""", now)
        assertEquals(UnverifiedHeartRatePreview(75, 990_000L), feedback.sensorPreview)
        assertEquals("Sensor reading · 10 sec ago", heartRateFeedbackText(feedback, true, 700_000L, now))
        val details = heartRateMeasurementDetailsText(feedback, true, now)!!
        assertTrue(details.contains("75 bpm · unverified · 10 sec ago"))
        assertTrue(details.contains("accuracy 0"))
        assertTrue(details.contains("not saved to history or used in workouts"))
        assertFalse(SyncValidation.validHeartRate(feedback.sensorPreview!!.bpm, 0, now, now))
    }

    @Test fun malformedPreviewIsIgnoredWithoutLosingIndependentDeviceStatus() {
        val now = 100_000_000L
        for (value in listOf("24", "241", "75.5", "\"75\"", "null", "true", "2147483648", "-4294967221")) {
            val feedback = heartRateFeedbackFromJson(
                """{"status":"unreliable_accuracy","sensorBpm":$value,"sensorReadingTime":$now}""", now)
            assertEquals("unreliable_accuracy", feedback.status)
            assertNull("Malformed BPM $value", feedback.sensorPreview)
        }
        for (time in listOf("0", "-1", "${now - 86_400_001L}", "${now + 5001L}", "100000000.5", "\"100000000\"", "null", "9223372036854775808")) {
            val feedback = heartRateFeedbackFromJson(
                """{"status":"unreliable_accuracy","sensorBpm":75,"sensorReadingTime":$time}""", now)
            assertEquals("unreliable_accuracy", feedback.status)
            assertNull("Malformed time $time", feedback.sensorPreview)
        }
        for (json in listOf("not json", "null", "[]", """{"status":null}""",
            """{"status":"reading","bpm":"75","readingTime":1000000}""")) {
            assertEquals(HeartRateFeedback("invalid_reading", now), heartRateFeedbackFromJson(json, now))
        }
    }

    @Test fun previewExpiresFromCaptureTimeAndDoesNotResurrectAfterClockChangeOrReconnect() {
        val capturedAt = 1_000_000L
        val feedback = HeartRateFeedback("unreliable_accuracy", capturedAt + 30_000L,
            sensorPreview = UnverifiedHeartRatePreview(75, capturedAt))
        assertNotNull(activeHeartRateSensorPreview(feedback, true, capturedAt + 86_400_000L))
        assertNull(activeHeartRateSensorPreview(feedback, true, capturedAt + 86_400_001L))
        assertNull(activeHeartRateSensorPreview(feedback, false, capturedAt))
        val expired = currentHeartRateFeedback(feedback, true, capturedAt + 86_400_001L)
        assertNull(expired?.sensorPreview)
        assertNull(currentHeartRateFeedback(expired, true, capturedAt)?.sensorPreview)
        val disconnected = currentHeartRateFeedback(feedback, false, capturedAt)
        assertNull(disconnected)
        assertNull(currentHeartRateFeedback(disconnected, true, capturedAt))
        assertEquals("Connect watch to measure", heartRateFeedbackText(feedback, false, null, capturedAt))
        assertFalse(heartRateMeasurementDetailsText(expired, true, capturedAt + 86_400_001L)!!.contains("75 bpm"))
    }

    @Test fun lastSensorReadingRemainsVisibleBetweenMeasurementsButMissingOrConfirmedSourceClearsIt() {
        val now = 1_000_000L
        for (status in listOf("measuring", "no_contact", "invalid_samples", "sensor_error", "disabled")) {
            val feedback = heartRateFeedbackFromJson(
                """{"status":"$status","sensorBpm":75,"sensorReadingTime":1000000}""", now)
            assertNotNull(feedback.sensorPreview)
        }
        val clear = heartRateFeedbackFromJson(
            """{"status":"unreliable_accuracy","sensorBpm":-1,"sensorReadingTime":0}""", now)
        assertNull(clear.sensorPreview)
        val confirmed = heartRateFeedbackFromJson(
            """{"status":"reading","bpm":80,"readingTime":1000000,"sensorBpm":75,"sensorReadingTime":1000000}""", now)
        assertNull(confirmed.sensorPreview)
        assertNull(activeHeartRateSensorPreview(HeartRateFeedback("requesting", now), true, now))
        assertNotNull(validatedHeartRateSensorPreview(
            HeartRateStatusPayload("unreliable_accuracy", sensorBpm = 75, sensorReadingTime = now + 5_000L), now))
        assertNull(validatedHeartRateSensorPreview(
            HeartRateStatusPayload("unreliable_accuracy", sensorBpm = 75, sensorReadingTime = now + 5_001L), now))
    }
}

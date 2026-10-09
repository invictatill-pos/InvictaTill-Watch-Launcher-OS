package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class HeartRateAttemptDiagnosticsTest {
    @Test fun unreliableFramesStillExposeIndependentValueAndTimestampFailuresWithoutPulseValues() {
        val diagnostic = attempt()
        diagnostic.frame(rejection(accuracy = 0), 0, value = 73f, unverifiedPreview = true)
        diagnostic.frame(rejection(value = 0f, accuracy = 0), 0, value = 0f)
        diagnostic.frame(rejection(value = Float.NaN, accuracy = 0), 0, value = Float.NaN)
        diagnostic.frame(rejection(value = null, accuracy = 0), 0)
        diagnostic.frame(rejection(accuracy = 0), 0, value = 73f,
            timestampIssue = HeartRateFrameRejection.TIMESTAMP_STALE)
        assertEquals("unreliable_accuracy", diagnostic.finalStatus(hasUnverifiedPreview = true))
        assertEquals("invalid_samples", diagnostic.finalStatus())
        val report = diagnostic.report("unreliable_accuracy")
        assertTrue(report.contains("Frames received: 5; accepted: 0"))
        assertTrue(report.contains("Rejected accuracy: 5; value: 0"))
        assertTrue(report.contains("in BPM range=2; missing=1; nonfinite=1; out of range=1"))
        assertTrue(report.contains("Unverified sensor previews: 1"))
        assertTrue(report.contains("checked_timestamp_stale: 1"))
        assertFalse(report.contains("73"))
    }

    private fun attempt() = HeartRateAttemptDiagnostics(1_000L, listOf("Default heart rate sensor"), true)

    private fun rejection(value: Float? = 72f, accuracy: Int = 3, event: Long = 18_000_000_000L,
                          previous: Long = 0L, started: Long = 10_000_000_000L) =
        HeartRatePolicy.frameRejection(value, accuracy, event, 20_000_000_000L,
            1_000_000L, started, previous)

    @Test fun noEventsIsDifferentFromNoContactAndMalformedFrames() {
        assertEquals("no_events", attempt().finalStatus())
        val contact = attempt()
        contact.frame(rejection(accuracy = -1), -1)
        assertEquals("no_contact", contact.finalStatus())
        val unreliable = attempt()
        unreliable.frame(rejection(accuracy = 0), 0)
        assertEquals("invalid_samples", unreliable.finalStatus())
        contact.frame(rejection(value = Float.NaN), 3)
        assertEquals("invalid_samples", contact.finalStatus())
    }

    @Test fun rejectionCategoriesExposeFirmwareAccuracyValueAndTimestampFailures() {
        assertEquals(HeartRateFrameRejection.NO_CONTACT, rejection(value = null, accuracy = -1))
        assertEquals(HeartRateFrameRejection.ACCURACY, rejection(accuracy = 0))
        assertEquals(HeartRateFrameRejection.ACCURACY, rejection(accuracy = 4))
        assertEquals(HeartRateFrameRejection.VALUE, rejection(value = null))
        assertEquals(HeartRateFrameRejection.VALUE, rejection(value = Float.POSITIVE_INFINITY))
        assertEquals(HeartRateFrameRejection.VALUE, rejection(value = 241f))
        assertEquals(HeartRateFrameRejection.TIMESTAMP_INVALID, rejection(event = 0L))
        assertEquals(HeartRateFrameRejection.TIMESTAMP_BEFORE_REQUEST, rejection(event = 9_999_999_999L))
        assertEquals(HeartRateFrameRejection.TIMESTAMP_FUTURE, rejection(event = 20_000_000_001L))
        assertEquals(HeartRateFrameRejection.TIMESTAMP_DUPLICATE_OR_REORDERED,
            rejection(previous = 18_000_000_000L))
        assertEquals(HeartRateFrameRejection.TIMESTAMP_STALE, rejection(event = 9_999_999_999L, started = 1L))
    }

    @Test fun rejectedFramesDoNotPreventLaterGenuineReadingOrTurnIntoMeasurements() {
        val diagnostic = attempt()
        diagnostic.frame(rejection(accuracy = 0), 0)
        diagnostic.frame(rejection(event = 9_999_999_999L), 3)
        assertEquals(0, diagnostic.accepted)
        assertEquals("invalid_samples", diagnostic.finalStatus())
        assertNull(rejection())
        diagnostic.frame(rejection(), 3)
        assertEquals("reading", diagnostic.finalStatus())
        val report = diagnostic.report(diagnostic.finalStatus())
        assertTrue(report.contains("Frames received: 3; accepted: 1"))
        assertTrue(report.contains("Rejected accuracy: 1"))
        assertTrue(report.contains("timestamp_before_request: 1"))
        assertFalse(report.contains("72"))
    }

    @Test fun metadataIsBoundedAndSelectedFallbackIsStillReportedAfterManyFailures() {
        val longMetadata = "x".repeat(20_000)
        val diagnostic = HeartRateAttemptDiagnostics(1_000L, List(100) { longMetadata }, true)
        repeat(100) { diagnostic.registration(longMetadata, longMetadata) }
        diagnostic.registration("Selected fallback heart rate", "registered / selected", selected = true)
        diagnostic.frame(HeartRateFrameRejection.TIMESTAMP_FUTURE, 3)
        val report = diagnostic.report(diagnostic.finalStatus())
        assertTrue(report.length <= 6_500)
        assertTrue(report.contains("Candidates at acquisition: 100"))
        assertTrue(report.contains("Registration attempts: 101"))
        assertTrue(report.contains("Selected sensor: Selected fallback heart rate"))
        assertTrue(report.contains("timestamp_future: 1"))
        assertTrue(report.contains("Frames received: 1; accepted: 0"))
    }

    @Test fun rejectedCaptureTimeCanIdentifyWrongClockDomainWithoutIncludingPulseValues() {
        val diagnostic = attempt()
        diagnostic.frame(HeartRateFrameRejection.TIMESTAMP_BEFORE_REQUEST, 3,
            1_000_000_000L, 20_000_000_000L, 10_000_000_000L)
        val report = diagnostic.report(diagnostic.finalStatus())
        assertTrue(report.contains("Last event elapsed nanos: 1000000000"))
        assertTrue(report.contains("observed elapsed nanos: 20000000000"))
        assertTrue(report.contains("Acquisition start elapsed nanos: 10000000000"))
        assertTrue(report.contains("Last frame age: 19000 ms; capture offset from request: -9000 ms"))
        diagnostic.frame(HeartRateFrameRejection.TIMESTAMP_INVALID, 3,
            Long.MIN_VALUE, Long.MAX_VALUE, 1L)
        assertTrue(diagnostic.report(diagnostic.finalStatus()).contains("invalid / overflow"))
    }
}

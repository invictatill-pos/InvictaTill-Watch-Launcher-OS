package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class HeartRateSensorPreviewTest {
    private val tracker = HeartRatePreviewTracker()
    private fun observe(value: Float? = 73f, accuracy: Int = 0, event: Long = 18_000_000_000L,
                        nowNanos: Long = 20_000_000_000L) =
        tracker.observe(value, accuracy, event, nowNanos, 1_000_000L, 10_000_000_000L)

    @Test fun plausibleUnreliableValueIsLabeledAndNeverPassesReliablePolicy() {
        assertNull(observe())
        val preview = tracker.preview!!
        assertEquals(73, preview.bpm)
        assertEquals(998_000L, preview.capturedAt)
        assertEquals("73 bpm\nSensor reading · just now", preview.valueText(1_000_000L))
        assertFalse(HeartRatePolicy.validReading(73f, 0))
        assertEquals(-1, HeartRatePolicy.reliableReading(preview.bpm, 0, preview.capturedAt,
            1_000_000L, HeartRatePolicy.FRESH_MS))
    }

    @Test fun noContactUnknownAccuracyMissingZeroOrInvalidValuesClearPreviousPreview() {
        for ((value, accuracy) in listOf(73f to -1, 73f to 4, 73f to 1, null to 0, 0f to 0,
            24f to 0, 241f to 0, Float.NaN to 0, Float.POSITIVE_INFINITY to 0)) {
            tracker.reset()
            observe()
            assertNotNull(tracker.preview)
            observe(value, accuracy, event = 19_000_000_000L)
            assertNull(tracker.preview)
        }
    }

    @Test fun rejectedTimestampsCannotProvideOrRefreshAPreview() {
        for (event in listOf(0L, -1L, 9_999_999_999L, 20_000_000_001L)) {
            tracker.reset()
            assertNotNull(observe(event = event))
            assertNull(tracker.preview)
        }
        tracker.reset()
        assertNull(observe())
        assertEquals(HeartRateFrameRejection.TIMESTAMP_DUPLICATE_OR_REORDERED, observe())
        assertNull(tracker.preview)
        assertEquals(HeartRateFrameRejection.TIMESTAMP_DUPLICATE_OR_REORDERED,
            observe(event = 17_000_000_000L))
        assertNull(tracker.preview)
        tracker.reset()
        assertEquals(HeartRateFrameRejection.TIMESTAMP_STALE, observe(nowNanos = 28_000_000_001L))
        assertNull(tracker.preview)
    }

    @Test fun futureFrameDoesNotPoisonSubsequentValidCaptureAndNewAttemptResetsDuplicates() {
        observe(event = Long.MAX_VALUE)
        assertNull(observe())
        assertNotNull(tracker.preview)
        tracker.reset()
        assertNull(tracker.preview)
        assertNull(observe())
        assertNotNull(tracker.preview)
    }

    @Test fun changingAccuracyCannotTurnADuplicateUnverifiedCaptureIntoARecordedMeasurement() {
        observe()
        for (event in listOf(18_000_000_000L, 17_000_000_000L)) {
            val timestampIssue = observe(accuracy = 1, event = event)
            assertEquals(HeartRateFrameRejection.TIMESTAMP_DUPLICATE_OR_REORDERED,
                HeartRatePolicy.frameRejection(73f, 1, event, 20_000_000_000L,
                    1_000_000L, 10_000_000_000L, 0L, timestampIssue))
            assertNull(tracker.preview)
        }
        val timestampIssue = observe(accuracy = 1, event = 19_000_000_000L)
        assertNull(HeartRatePolicy.frameRejection(73f, 1, 19_000_000_000L, 20_000_000_000L,
            1_000_000L, 10_000_000_000L, 0L, timestampIssue))
    }

    @Test fun sensorDisplayRetainsOriginalCaptureBetweenLongIntervalsAndExpiresAfterOneDay() {
        observe()
        val preview = tracker.preview!!
        assertEquals(73, preview.displayBpm(preview.capturedAt + HeartRatePolicy.DISPLAY_MS))
        assertEquals(-1, preview.displayBpm(preview.capturedAt + HeartRatePolicy.DISPLAY_MS + 1))
        assertEquals(-1, preview.displayBpm(preview.capturedAt - 1))
        assertEquals("73 bpm\nSensor reading · 1 min ago", preview.valueText(preview.capturedAt + 60_000L))
    }
}

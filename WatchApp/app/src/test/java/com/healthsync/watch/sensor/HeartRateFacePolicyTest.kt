package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class HeartRateFacePolicyTest {
    private val now = 1_000_000L
    private fun face(bpm: Int = -1, accuracy: Int = 0, capturedAt: Long = 0L,
                     preview: HeartRateSensorPreview? = HeartRateSensorPreview(75, now - 5_000L)) =
        HeartRateFacePolicy.select(bpm, accuracy, capturedAt, preview, now)

    @Test fun workingSensorReadingReachesFaceWithoutBecomingReliableWorkoutData() {
        val display = face()
        assertEquals(75, display.bpm)
        assertEquals(now - 5_000L, display.capturedAt)
        assertTrue(display.sensorReading)
        assertEquals("BPM", HeartRatePolicy.complicationUnit(display.capturedAt, now))
        assertEquals(-1, HeartRatePolicy.reliableReading(display.bpm, 0, display.capturedAt,
            now, HeartRatePolicy.FRESH_MS))
    }

    @Test fun sourceCanChangeWithoutTheBpmChanging() {
        val sensor = face(bpm = 75, accuracy = 3, capturedAt = now - 60_000L)
        assertEquals(75, sensor.bpm)
        assertTrue(sensor.sensorReading)
        val confirmed = face(bpm = 75, accuracy = 1, capturedAt = now)
        assertEquals(75, confirmed.bpm)
        assertFalse(confirmed.sensorReading)
        assertEquals(now, confirmed.capturedAt)
    }

    @Test fun ReliableReadingWinsWhenBothSourcesHaveTheSameCaptureTime() {
        val display = face(bpm = 80, accuracy = 2, capturedAt = now - 5_000L)
        assertEquals(80, display.bpm)
        assertFalse(display.sensorReading)
    }

    @Test fun invalidExpiredAndFutureSensorReadingsFallBackToRecordedCaptureAndAge() {
        for (preview in listOf(null, HeartRateSensorPreview(75, now - HeartRatePolicy.DISPLAY_MS - 1L),
            HeartRateSensorPreview(75, now + 1L), HeartRateSensorPreview(0, now),
            HeartRateSensorPreview(241, now), HeartRateSensorPreview(75, 0L))) {
            val display = face(80, 3, now - 300_000L, preview)
            assertEquals(80, display.bpm)
            assertEquals(now - 300_000L, display.capturedAt)
            assertFalse(display.sensorReading)
            assertEquals("LAST 5m", HeartRatePolicy.complicationUnit(display.capturedAt, now))
        }
    }

    @Test fun retainedSensorValueDisplaysItsOriginalAgeBetweenMeasurements() {
        val display = face(preview = HeartRateSensorPreview(75, now - 300_000L))
        assertTrue(display.sensorReading)
        assertEquals("LAST 5m", HeartRatePolicy.complicationUnit(display.capturedAt, now))
        assertEquals(-1, face(preview = HeartRateSensorPreview(75, now - HeartRatePolicy.DISPLAY_MS - 1L)).bpm)
        val laterNow = now + HeartRatePolicy.DISPLAY_MS
        assertEquals(80, HeartRateFacePolicy.select(80, 3, now, null, laterNow).bpm)
        assertEquals(-1, HeartRateFacePolicy.select(80, 3, now, null, laterNow + 1L).bpm)
    }

    @Test fun missingPreviewCannotExposeStoredUnreliableNumbersOnTheFace() {
        for (accuracy in listOf(-1, 0, 4)) {
            assertEquals(HeartRateFaceReading(-1, 0L, false), face(75, accuracy, now, preview = null))
        }
    }
}

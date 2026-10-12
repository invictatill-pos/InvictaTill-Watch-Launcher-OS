package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class OxygenSensorPreviewTest {
    private fun frame(value: Float = 97f, accuracy: Int = 0, event: Long = 18_000_000_000L,
                      lastEvent: Long = 0L) = OxygenSensorPreview.fromFrame(value, accuracy, event,
        20_000_000_000L, 1_000_000, 10_000_000_000L, lastEvent)
    @Test fun incomingUnreliableSaturationRetainsItsTimeWithoutBecomingAccepted() {
        val preview = frame()!!
        assertEquals(998_000L, preview.capturedAt)
        assertEquals(97f, preview.display(1_000_000), 0f)
        assertFalse(HeartRatePolicy.validOxygen(preview.percentage, 0))
        assertEquals(-1f, preview.display(preview.capturedAt - 1), 0f)
        assertEquals(-1f, preview.display(preview.capturedAt + HeartRatePolicy.DISPLAY_MS + 1), 0f)
    }
    @Test fun noContactBadRangesAndUnverifiedTimestampsCannotProvideFeedback() {
        assertNull(frame(accuracy = -1))
        assertNull(frame(accuracy = 4))
        assertNull(frame(value = Float.NaN))
        assertNull(frame(value = 0.97f))
        assertNull(frame(event = 9_999_999_999L))
        assertNull(frame(event = 20_000_000_001L))
        assertNull(frame(lastEvent = 18_000_000_000L))
    }
}

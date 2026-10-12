package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class StepCapturePolicyTest {
    @Test fun startupWithoutObservationAndDateRolloverStayUnknown() {
        assertFalse(StepCapturePolicy.capturedToday(0L, "2026-10-12", "2026-10-12", "HARDWARE_COUNTER", 1000L))
        assertFalse(StepCapturePolicy.capturedToday(500L, "2026-10-11", "2026-10-12", "HARDWARE_COUNTER", 1000L))
        assertFalse(StepCapturePolicy.capturedToday(500L, "2026-10-12", "2026-10-12", "NONE", 1000L))
        assertFalse(StepCapturePolicy.capturedToday(1001L, "2026-10-12", "2026-10-12", "MOTION_ESTIMATE", 1000L))
    }

    @Test fun actualBaselineZeroIsDisplayableOnlyWithRealSameDayCaptureAndAvailableMonitoring() {
        val captured = StepCapturePolicy.capturedToday(500L, "2026-10-12", "2026-10-12", "HARDWARE_COUNTER", 1000L)
        assertTrue(captured)
        assertTrue(StepCapturePolicy.visibleCapture(captured, "captured"))
        for (status in listOf("starting", "missing_sensor", "permission_required", "monitoring_unavailable"))
            assertFalse(StepCapturePolicy.visibleCapture(captured, status))
    }

    @Test fun mixedSourceCountsCannotBeLabelledAsEntirelyHardwareMeasured() {
        assertEquals("MOTION_ESTIMATE", StepCapturePolicy.sourceAfterObservation("NONE", "MOTION_ESTIMATE"))
        assertEquals("HARDWARE_COUNTER", StepCapturePolicy.sourceAfterObservation("HARDWARE_COUNTER", "HARDWARE_COUNTER"))
        assertEquals("MIXED_CAPTURE", StepCapturePolicy.sourceAfterObservation("MOTION_ESTIMATE", "HARDWARE_COUNTER"))
        assertEquals("MIXED_CAPTURE", StepCapturePolicy.sourceAfterObservation("MIXED_CAPTURE", "HARDWARE_COUNTER"))
    }
}

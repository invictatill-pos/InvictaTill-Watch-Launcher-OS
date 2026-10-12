package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class OxygenCapturePolicyTest {
    @Test fun onlyExplicitVendorSaturationMetricsQualify() {
        assertTrue(OxygenCapturePolicy.isOxygenSensor(65_536, "SpO2", "vendor.spo2"))
        assertTrue(OxygenCapturePolicy.isOxygenSensor(65_536, "Blood oxygen saturation", ""))
        assertFalse(OxygenCapturePolicy.isOxygenSensor(21, "Blood oxygen", ""))
        assertFalse(OxygenCapturePolicy.isOxygenSensor(31, "SpO2", ""))
        assertFalse(OxygenCapturePolicy.isOxygenSensor(65_536, "Raw SpO2 PPG", ""))
        assertFalse(OxygenCapturePolicy.isOxygenSensor(65_536, "Infrared", ""))
        assertFalse(OxygenCapturePolicy.isOxygenSensor(65_536, "Pulse", "vendor.pulse"))
    }
    @Test fun uncapturedExpiredAndFutureReadingsStayEmpty() {
        assertEquals(98f, OxygenCapturePolicy.displayReading(98f, 1000L, 2000L, 120_000L))
        assertEquals(-1f, OxygenCapturePolicy.displayReading(98f, 0L, 2000L, 120_000L))
        assertEquals(-1f, OxygenCapturePolicy.displayReading(98f, 1000L, 999L, 120_000L))
        assertEquals(-1f, OxygenCapturePolicy.displayReading(98f, 1000L, 121_001L, 120_000L))
        assertEquals(-1f, OxygenCapturePolicy.displayReading(Float.NaN, 1000L, 2000L, 120_000L))
        assertEquals(-1f, OxygenCapturePolicy.displayReading(101f, 1000L, 2000L, 120_000L))
    }
}

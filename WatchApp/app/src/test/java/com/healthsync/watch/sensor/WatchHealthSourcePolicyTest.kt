package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class WatchHealthSourcePolicyTest {
    private fun reading(source: String = "WATCH_HEALTH_APP", pkg: String = "com.vendor.health",
                        label: String = "Health", accuracy: Int = 0, at: Long = 900_000L) =
        WatchHealthSourcePolicy.heartRate(75, accuracy, at, 1_000_000, 120_000, source, pkg, label)
    @Test fun exportedAppDataDoesNotInventSensorAccuracy() {
        assertEquals(75, reading())
        assertEquals(-1, reading(source = "WATCH_SENSOR"))
        assertEquals(75, reading(source = "WATCH_SENSOR", accuracy = 2))
    }
    @Test fun unknownSourceMissingIdentityAndStaleCaptureAreRejected() {
        assertEquals(-1, reading(source = "UNKNOWN"))
        assertEquals(-1, reading(pkg = ""))
        assertEquals(-1, reading(label = ""))
        assertEquals(-1, reading(at = 879_999))
        assertEquals(-1, reading(at = 1_000_001))
    }
}

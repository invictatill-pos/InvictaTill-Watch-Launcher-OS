package com.healthsync.watch.algorithm

import org.junit.Assert.*
import org.junit.Test

class FallDetectionTest {
    @Test fun impactAloneOrBriefWristMovementDoesNotTrigger() {
        val alerts = mutableListOf<String>()
        val detector = FallDetection { alerts.add(it) }
        detector.process(0f, 0f, 40f, 1000L)
        detector.process(0f, 0f, 1f, 2000L)
        detector.process(0f, 0f, 40f, 2020L)
        assertTrue(alerts.isEmpty())
    }

    @Test fun freeFallThenImpactTriggersOneAlertWithCooldown() {
        val alerts = mutableListOf<String>()
        val detector = FallDetection { alerts.add(it) }
        detector.process(0f, 0f, 1f, 1000L)
        detector.process(0f, 0f, 1f, 1100L)
        detector.process(0f, 0f, 9.81f, 1120L)
        detector.process(0f, 0f, 40f, 1200L)
        detector.process(0f, 0f, 40f, 1220L)
        detector.process(0f, 0f, 1f, 1300L)
        detector.process(0f, 0f, 1f, 1400L)
        detector.process(0f, 0f, 40f, 1450L)
        assertEquals(listOf("HIGH"), alerts)
    }

    @Test fun expiredFreeFallDoesNotTriggerAndInvalidSamplesAreIgnored() {
        val alerts = mutableListOf<String>()
        val detector = FallDetection { alerts.add(it) }
        detector.process(0f, 0f, 1f, 1000L)
        detector.process(0f, 0f, 1f, 1100L)
        detector.process(Float.NaN, 0f, 40f, 1200L)
        detector.process(0f, 0f, 40f, 2100L)
        assertTrue(alerts.isEmpty())
    }
}

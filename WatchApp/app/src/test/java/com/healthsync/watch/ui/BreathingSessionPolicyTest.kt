package com.healthsync.watch.ui

import org.junit.Assert.*
import org.junit.Test

class BreathingSessionPolicyTest {
    @Test fun holdsKeepTheVisualStillBetweenInhaleAndExhale() {
        assertEquals("Inhale", BreathingSessionPolicy.frame(0).label)
        assertEquals("Hold", BreathingSessionPolicy.frame(4_000).label)
        assertEquals(.85f, BreathingSessionPolicy.frame(5_000).radius, .001f)
        assertEquals(.85f, BreathingSessionPolicy.frame(7_999).radius, .001f)
        assertEquals("Exhale", BreathingSessionPolicy.frame(8_000).label)
        assertEquals(.60f, BreathingSessionPolicy.frame(10_000).radius, .001f)
        assertEquals(.35f, BreathingSessionPolicy.frame(13_000).radius, .001f)
        assertEquals("Inhale", BreathingSessionPolicy.frame(16_000).label)
    }

    @Test fun countdownClampsTimeAndDoesNotDisplayZeroBeforeCompletion() {
        assertEquals(60, BreathingSessionPolicy.frame(-100).sessionSeconds)
        assertEquals(4, BreathingSessionPolicy.frame(0).phaseSeconds)
        assertEquals(1, BreathingSessionPolicy.frame(3_999).phaseSeconds)
        assertEquals(1, BreathingSessionPolicy.frame(59_999).sessionSeconds)
        assertEquals(0, BreathingSessionPolicy.frame(70_000).sessionSeconds)
    }
}

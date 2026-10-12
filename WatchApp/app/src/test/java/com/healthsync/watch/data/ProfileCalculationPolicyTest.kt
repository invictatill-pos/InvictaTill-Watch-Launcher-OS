package com.healthsync.watch.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileCalculationPolicyTest {
    @Test fun legacyValuesDoNotSilentlyPersonalizeCalculationDefaults() {
        assertEquals(70.0, ProfileCalculationPolicy.weightKg(false, 92.5), 0.0)
        assertEquals(170, ProfileCalculationPolicy.heightCm(false, 193))
    }

    @Test fun explicitProfileEntriesPersonalizeEachInputIndependently() {
        assertEquals(92.5, ProfileCalculationPolicy.weightKg(true, 92.5), 0.0)
        assertEquals(170, ProfileCalculationPolicy.heightCm(false, 193))
        assertEquals(70.0, ProfileCalculationPolicy.weightKg(false, 92.5), 0.0)
        assertEquals(193, ProfileCalculationPolicy.heightCm(true, 193))
    }

    @Test fun corruptedConfiguredInputsDoNotPoisonHealthEstimates() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, 300.0).forEach {
            assertEquals(70.0, ProfileCalculationPolicy.weightKg(true, it), 0.0)
        }
        listOf(0, 99, 231, Int.MAX_VALUE).forEach {
            assertEquals(170, ProfileCalculationPolicy.heightCm(true, it))
        }
    }
}

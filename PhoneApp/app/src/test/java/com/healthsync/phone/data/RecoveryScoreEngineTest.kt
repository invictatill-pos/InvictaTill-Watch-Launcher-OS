package com.healthsync.phone.data

import com.healthsync.phone.data.model.SleepEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryScoreEngineTest {

    @Test
    fun computeSleepBreakdownCalculatesStagesAndEfficiency() {
        val sleep = SleepEntity(
            startTime = 0L,
            endTime = 8 * 3600 * 1000L, // 8 hours = 480 mins
            deepMinutes = 96,           // 20%
            lightMinutes = 240,         // 50%
            remMinutes = 120,           // 25%
            awakeMinutes = 24           // 5%
        )

        val breakdown = RecoveryScoreEngine.computeSleepBreakdown(sleep)
        assertEquals(480, breakdown.totalMinutes)
        assertEquals(96, breakdown.deepMinutes)
        assertEquals(240, breakdown.lightMinutes)
        assertEquals(120, breakdown.remMinutes)
        assertEquals(24, breakdown.awakeMinutes)

        // Efficiency = sleeping / total = (96+240+120)/480 = 456/480 = 95%
        assertEquals(95, breakdown.efficiencyPercent)
        assertTrue(breakdown.deepPercent in 0.19f..0.21f)
        assertTrue(breakdown.remPercent in 0.24f..0.26f)
        assertTrue(breakdown.sleepScore >= 85)
    }

    @Test
    fun optimalSleepAndRhrProducesHighRecoveryScore() {
        val sleep = SleepEntity(
            startTime = 0L,
            endTime = 8 * 3600 * 1000L,
            deepMinutes = 100,
            lightMinutes = 240,
            remMinutes = 110,
            awakeMinutes = 30
        )

        val result = RecoveryScoreEngine.calculate(
            sleep = sleep,
            restingHr = 56,
            yesterdaySteps = 9500,
            yesterdayCalories = 420.0
        )

        assertTrue("Expected score >= 85 for optimal sleep and RHR, got ${result.score}", result.score >= 85)
        assertEquals(RecoveryTier.OPTIMAL, result.tier)
        assertNotNull(result.sleepBreakdown)
        assertTrue(result.factors.isNotEmpty())
    }

    @Test
    fun severelyDeprivedSleepProducesRestRecommendation() {
        val poorSleep = SleepEntity(
            startTime = 0L,
            endTime = 3 * 3600 * 1000L, // 3 hours
            deepMinutes = 15,
            lightMinutes = 100,
            remMinutes = 20,
            awakeMinutes = 45
        )

        val result = RecoveryScoreEngine.calculate(
            sleep = poorSleep,
            restingHr = 82, // elevated RHR
            yesterdaySteps = 24000, // very high strain
            yesterdayCalories = 1200.0
        )

        assertTrue("Expected low score for severe fatigue, got ${result.score}", result.score < 60)
        assertTrue(result.tier == RecoveryTier.REST || result.tier == RecoveryTier.MODERATE)
    }

    @Test
    fun nullSleepProducesValidDaytimeEstimate() {
        val result = RecoveryScoreEngine.calculate(
            sleep = null,
            restingHr = 62,
            yesterdaySteps = 8000,
            yesterdayCalories = 350.0
        )

        assertTrue(result.score in 30..95)
        assertNotNull(result.headline)
        assertNotNull(result.recommendation)
    }
}

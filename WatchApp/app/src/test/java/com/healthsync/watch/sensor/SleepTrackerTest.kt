package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class SleepTrackerTest {

    @Test
    fun testOptimalSleepScoreCalculation() {
        // 7.5 hours = 450 mins (100m deep, 210m light, 105m rem, 35m awake)
        val score = SleepTracker.calculateSleepScore(
            deep = 100,
            light = 210,
            rem = 105,
            awake = 35,
            total = 450
        )
        assertTrue("Optimal sleep should score >= 80, got $score", score >= 80)
    }

    @Test
    fun testRestNeededSleepScoreCalculation() {
        // Fragmented short sleep: 2.5 hours = 150 mins, high awake time (50m awake, 10m deep, 80m light, 10m rem)
        val score = SleepTracker.calculateSleepScore(
            deep = 10,
            light = 80,
            rem = 10,
            awake = 50,
            total = 150
        )
        assertTrue("Rest-needed fragmented sleep should score < 65, got $score", score < 65)
    }

    @Test
    fun testZeroDurationReturnsZero() {
        val score = SleepTracker.calculateSleepScore(0, 0, 0, 0, 0)
        assertEquals(0, score)
    }

    @Test
    fun testStageClassification() {
        // High movement -> AWAKE
        assertEquals("AWAKE", SleepTracker.classifyEpochStage(0.55f, 72))

        // Near-zero movement + low resting heart rate -> DEEP
        assertEquals("DEEP", SleepTracker.classifyEpochStage(0.015f, 54))

        // Subtle twitching / low variance -> REM
        assertEquals("REM", SleepTracker.classifyEpochStage(0.05f, 68))

        // Moderate stillness -> LIGHT
        assertEquals("LIGHT", SleepTracker.classifyEpochStage(0.12f, 62))
    }
}

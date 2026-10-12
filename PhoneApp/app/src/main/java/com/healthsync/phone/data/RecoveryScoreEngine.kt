package com.healthsync.phone.data

import com.healthsync.phone.data.model.SleepEntity
import kotlin.math.roundToInt

enum class RecoveryTier(val label: String, val colorHex: Long) {
    OPTIMAL("Optimal Readiness", 0xFF00E676),
    GOOD("Good Recovery", 0xFF00B0FF),
    MODERATE("Moderate Recovery", 0xFFFFB300),
    REST("Rest Recommended", 0xFFFF5252)
}

data class RecoveryFactor(
    val title: String,
    val valueStr: String,
    val isPositive: Boolean
)

data class SleepBreakdown(
    val totalMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val deepPercent: Float,
    val lightPercent: Float,
    val remPercent: Float,
    val awakePercent: Float,
    val efficiencyPercent: Int,
    val sleepScore: Int
)

data class DailyRecoveryResult(
    val score: Int,
    val tier: RecoveryTier,
    val headline: String,
    val recommendation: String,
    val sleepBreakdown: SleepBreakdown?,
    val factors: List<RecoveryFactor>
)

object RecoveryScoreEngine {

    /**
     * Calculates the Daily Recovery & Readiness Score (0..100) combining
     * sleep stages, sleep efficiency, resting heart rate, and previous day activity strain.
     */
    fun calculate(
        sleep: SleepEntity?,
        restingHr: Int = 0,
        yesterdaySteps: Int = 0,
        yesterdayCalories: Double = 0.0
    ): DailyRecoveryResult {
        val breakdown = sleep?.let { computeSleepBreakdown(it) }

        var sleepWeight = 0.65
        var rhrWeight = 0.25
        var activityWeight = 0.10

        val sleepScore = breakdown?.sleepScore ?: 70 // default if no sleep record
        val rhrScore = computeRhrScore(restingHr)
        val activityScore = computeActivityBalanceScore(yesterdaySteps, yesterdayCalories)

        val compositeScore = if (breakdown != null) {
            (sleepScore * sleepWeight + rhrScore * rhrWeight + activityScore * activityWeight).roundToInt().coerceIn(0, 100)
        } else {
            // When sleep data is not available, lean more on resting HR and activity balance
            ((rhrScore * 0.70 + activityScore * 0.30)).roundToInt().coerceIn(30, 95)
        }

        val tier = when {
            compositeScore >= 85 -> RecoveryTier.OPTIMAL
            compositeScore >= 70 -> RecoveryTier.GOOD
            compositeScore >= 50 -> RecoveryTier.MODERATE
            else -> RecoveryTier.REST
        }

        val (headline, recommendation) = generateInsights(tier, breakdown, restingHr)

        val factors = mutableListOf<RecoveryFactor>()
        if (breakdown != null) {
            val totalHours = breakdown.totalMinutes / 60f
            factors.add(
                RecoveryFactor(
                    title = "Sleep Duration",
                    valueStr = String.format(java.util.Locale.US, "%.1fh", totalHours),
                    isPositive = totalHours >= 7.0f
                )
            )
            factors.add(
                RecoveryFactor(
                    title = "Deep Sleep",
                    valueStr = "${(breakdown.deepPercent * 100).roundToInt()}%",
                    isPositive = breakdown.deepPercent >= 0.15f
                )
            )
            factors.add(
                RecoveryFactor(
                    title = "Sleep Efficiency",
                    valueStr = "${breakdown.efficiencyPercent}%",
                    isPositive = breakdown.efficiencyPercent >= 85
                )
            )
        }

        if (restingHr in 40..120) {
            factors.add(
                RecoveryFactor(
                    title = "Resting HR",
                    valueStr = "$restingHr bpm",
                    isPositive = restingHr <= 70
                )
            )
        }

        return DailyRecoveryResult(
            score = compositeScore,
            tier = tier,
            headline = headline,
            recommendation = recommendation,
            sleepBreakdown = breakdown,
            factors = factors
        )
    }

    fun computeSleepBreakdown(sleep: SleepEntity): SleepBreakdown {
        val totalMins = ((sleep.endTime - sleep.startTime) / 60_000L).toInt().coerceAtLeast(1)
        val deep = sleep.deepMinutes.coerceAtLeast(0)
        val light = sleep.lightMinutes.coerceAtLeast(0)
        val rem = sleep.remMinutes.coerceAtLeast(0)
        val awake = sleep.awakeMinutes.coerceAtLeast(0)

        val stageSum = (deep + light + rem + awake).coerceAtLeast(1)
        val deepPct = deep.toFloat() / stageSum
        val lightPct = light.toFloat() / stageSum
        val remPct = rem.toFloat() / stageSum
        val awakePct = awake.toFloat() / stageSum

        val sleepingMins = (deep + light + rem)
        val efficiency = ((sleepingMins.toFloat() / stageSum) * 100).roundToInt().coerceIn(0, 100)

        // Duration score (optimal: 420 - 540 min, 7-9 hours)
        val durationScore = when {
            totalMins in 420..540 -> 100
            totalMins in 360..419 -> 82
            totalMins in 541..600 -> 90
            totalMins in 300..359 -> 65
            totalMins > 600 -> 75
            else -> 45
        }

        // Deep sleep score (optimal: 15% - 25%)
        val deepScore = when {
            deepPct >= 0.18f -> 100
            deepPct >= 0.14f -> 85
            deepPct >= 0.10f -> 68
            else -> 50
        }

        // REM score (optimal: 20% - 25%)
        val remScore = when {
            remPct >= 0.20f -> 100
            remPct >= 0.15f -> 85
            remPct >= 0.10f -> 70
            else -> 55
        }

        // Efficiency score
        val efficiencyScore = when {
            efficiency >= 90 -> 100
            efficiency >= 85 -> 88
            efficiency >= 78 -> 72
            else -> 50
        }

        val sleepScore = (durationScore * 0.35 + deepScore * 0.25 + remScore * 0.20 + efficiencyScore * 0.20).roundToInt().coerceIn(0, 100)

        return SleepBreakdown(
            totalMinutes = totalMins,
            deepMinutes = deep,
            lightMinutes = light,
            remMinutes = rem,
            awakeMinutes = awake,
            deepPercent = deepPct,
            lightPercent = lightPct,
            remPercent = remPct,
            awakePercent = awakePct,
            efficiencyPercent = efficiency,
            sleepScore = sleepScore
        )
    }

    private fun computeRhrScore(restingHr: Int): Int {
        if (restingHr <= 0) return 75 // Neutral default
        return when {
            restingHr in 48..58 -> 100
            restingHr in 59..65 -> 92
            restingHr in 66..72 -> 80
            restingHr in 73..78 -> 68
            restingHr in 79..85 -> 55
            else -> 40
        }
    }

    private fun computeActivityBalanceScore(steps: Int, calories: Double): Int {
        if (steps <= 0 && calories <= 0) return 80 // Neutral
        return when {
            steps in 7_000..13_000 -> 100
            steps in 4_000..6_999 -> 85
            steps in 13_001..20_000 -> 78
            steps > 20_000 -> 60 // Heavy strain, high recovery need
            else -> 75
        }
    }

    private fun generateInsights(
        tier: RecoveryTier,
        breakdown: SleepBreakdown?,
        restingHr: Int
    ): Pair<String, String> {
        return when (tier) {
            RecoveryTier.OPTIMAL -> Pair(
                "Peak Readiness",
                "Your central nervous system and muscles are fully recharged. Ideal condition for high-intensity workouts or personal bests."
            )
            RecoveryTier.GOOD -> Pair(
                "Well Rested",
                "Solid recovery across all sleep metrics. You are well prepared for steady-state cardio or moderate resistance training."
            )
            RecoveryTier.MODERATE -> Pair(
                "Moderate Capacity",
                if (breakdown != null && breakdown.deepPercent < 0.14f) {
                    "Deep sleep was lower than usual. Aim for an active recovery walk or moderate effort today."
                } else {
                    "Recovery is moderate. Listen to your body and keep training volume within manageable levels."
                }
            )
            RecoveryTier.REST -> Pair(
                "Recovery Needed",
                "High physiological fatigue detected. Prioritize adequate rest, hydration, and an early bedtime to facilitate rebuild."
            )
        }
    }
}

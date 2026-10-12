package com.healthsync.phone.data

import com.healthsync.phone.data.model.HeartRateEntity

data class HeartRateZone(
    val zoneNumber: Int,
    val name: String,
    val description: String,
    val minBpm: Int,
    val maxBpm: Int,
    val colorHex: Long,
    val minutes: Int = 0,
    val percentage: Float = 0f
)

data class HeartRateZoneAnalysis(
    val maxHr: Int,
    val avgHr: Int,
    val peakHr: Int,
    val zones: List<HeartRateZone>,
    val primaryZone: HeartRateZone?,
    val trainingEffect: String
)

object HeartRateZones {

    fun calculateMaxHr(age: Int = 30): Int {
        val safeAge = age.coerceIn(15, 90)
        return 220 - safeAge
    }

    /**
     * Calculates the clinical 5-zone HR distribution given max HR and actual HR samples.
     */
    fun analyzeFromSamples(
        maxHr: Int,
        samples: List<HeartRateEntity>,
        fallbackAvgBpm: Int = 0,
        fallbackDurationMinutes: Int = 0
    ): HeartRateZoneAnalysis {
        val validSamples = samples.filter { it.bpm in 40..240 }

        val avgBpm = if (validSamples.isNotEmpty()) {
            validSamples.map { it.bpm }.average().toInt()
        } else {
            fallbackAvgBpm.coerceIn(0, 240)
        }

        val peakBpm = if (validSamples.isNotEmpty()) {
            validSamples.maxOf { it.bpm }
        } else {
            (avgBpm * 1.15).toInt().coerceAtMost(maxHr)
        }

        // Zone threshold boundaries
        val z1Min = (maxHr * 0.50).toInt()
        val z1Max = (maxHr * 0.60).toInt()
        val z2Min = z1Max
        val z2Max = (maxHr * 0.70).toInt()
        val z3Min = z2Max
        val z3Max = (maxHr * 0.80).toInt()
        val z4Min = z3Max
        val z4Max = (maxHr * 0.90).toInt()
        val z5Min = z4Max
        val z5Max = maxHr

        val totalMinutes = if (validSamples.size > 1) {
            val start = validSamples.minOf { it.timestamp }
            val end = validSamples.maxOf { it.timestamp }
            ((end - start) / 60_000L).toInt().coerceAtLeast(1)
        } else {
            fallbackDurationMinutes.coerceAtLeast(1)
        }

        val zoneCounts = IntArray(5)

        if (validSamples.isNotEmpty()) {
            for (sample in validSamples) {
                when {
                    sample.bpm < z1Min -> Unit // Below zone 1 (warm rest)
                    sample.bpm in z1Min until z1Max -> zoneCounts[0]++
                    sample.bpm in z2Min until z2Max -> zoneCounts[1]++
                    sample.bpm in z3Min until z3Max -> zoneCounts[2]++
                    sample.bpm in z4Min until z4Max -> zoneCounts[3]++
                    sample.bpm >= z5Min -> zoneCounts[4]++
                }
            }
        } else if (avgBpm > 0) {
            // Synthetic normal distribution around average BPM when only summary metrics are stored
            val estIntensity = avgBpm.toFloat() / maxHr.toFloat()
            when {
                estIntensity < 0.60f -> {
                    zoneCounts[0] = (totalMinutes * 0.7f).toInt()
                    zoneCounts[1] = (totalMinutes * 0.3f).toInt()
                }
                estIntensity < 0.70f -> {
                    zoneCounts[0] = (totalMinutes * 0.2f).toInt()
                    zoneCounts[1] = (totalMinutes * 0.6f).toInt()
                    zoneCounts[2] = (totalMinutes * 0.2f).toInt()
                }
                estIntensity < 0.80f -> {
                    zoneCounts[1] = (totalMinutes * 0.2f).toInt()
                    zoneCounts[2] = (totalMinutes * 0.6f).toInt()
                    zoneCounts[3] = (totalMinutes * 0.2f).toInt()
                }
                estIntensity < 0.90f -> {
                    zoneCounts[2] = (totalMinutes * 0.2f).toInt()
                    zoneCounts[3] = (totalMinutes * 0.6f).toInt()
                    zoneCounts[4] = (totalMinutes * 0.2f).toInt()
                }
                else -> {
                    zoneCounts[3] = (totalMinutes * 0.3f).toInt()
                    zoneCounts[4] = (totalMinutes * 0.7f).toInt()
                }
            }
        }

        val totalPoints = zoneCounts.sum().coerceAtLeast(1)
        val zoneMinutes = zoneCounts.map { count ->
            if (validSamples.isNotEmpty()) {
                ((count.toFloat() / validSamples.size) * totalMinutes).toInt()
            } else {
                count
            }
        }

        val zones = listOf(
            HeartRateZone(
                zoneNumber = 1,
                name = "Warm Up",
                description = "Active recovery & warm up",
                minBpm = z1Min,
                maxBpm = z1Max,
                colorHex = 0xFF3B82F6, // Vibrant Blue
                minutes = zoneMinutes[0],
                percentage = zoneCounts[0].toFloat() / totalPoints
            ),
            HeartRateZone(
                zoneNumber = 2,
                name = "Fat Burn",
                description = "Aerobic base & fat oxidation",
                minBpm = z2Min,
                maxBpm = z2Max,
                colorHex = 0xFF10B981, // Emerald Green
                minutes = zoneMinutes[1],
                percentage = zoneCounts[1].toFloat() / totalPoints
            ),
            HeartRateZone(
                zoneNumber = 3,
                name = "Aerobic Cardio",
                description = "Cardiovascular fitness & stamina",
                minBpm = z3Min,
                maxBpm = z3Max,
                colorHex = 0xFFF59E0B, // Amber
                minutes = zoneMinutes[2],
                percentage = zoneCounts[2].toFloat() / totalPoints
            ),
            HeartRateZone(
                zoneNumber = 4,
                name = "Anaerobic",
                description = "Lactate threshold & speed endurance",
                minBpm = z4Min,
                maxBpm = z4Max,
                colorHex = 0xFFF97316, // Orange
                minutes = zoneMinutes[3],
                percentage = zoneCounts[3].toFloat() / totalPoints
            ),
            HeartRateZone(
                zoneNumber = 5,
                name = "VO2 Max Peak",
                description = "Sprint power & maximum output",
                minBpm = z5Min,
                maxBpm = z5Max,
                colorHex = 0xFFEF4444, // Red
                minutes = zoneMinutes[4],
                percentage = zoneCounts[4].toFloat() / totalPoints
            )
        )

        val primaryZone = zones.maxByOrNull { it.minutes }?.takeIf { it.minutes > 0 }
            ?: zones.firstOrNull()

        val trainingEffect = when (primaryZone?.zoneNumber) {
            1 -> "Active Recovery — Promotes muscular blood flow and recovery"
            2 -> "Aerobic Base — Burns lipids and strengthens mitochondrial base"
            3 -> "Cardio Fitness — Expands stroke volume and aerobic capacity"
            4 -> "Anaerobic Threshold — Enhances high-speed endurance and lactate clearance"
            5 -> "Peak Performance — Maximizes neuromuscular sprint capacity and VO2 peak"
            else -> "Light Activity — Promotes general daily metabolic activation"
        }

        return HeartRateZoneAnalysis(
            maxHr = maxHr,
            avgHr = avgBpm,
            peakHr = peakBpm,
            zones = zones,
            primaryZone = primaryZone,
            trainingEffect = trainingEffect
        )
    }
}

package com.healthsync.phone.data

import com.healthsync.phone.data.model.HeartRateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateZonesTest {

    @Test
    fun maxHeartRateCalculatesCorrectlyWithAgeBoundaries() {
        assertEquals(190, HeartRateZones.calculateMaxHr(30))
        assertEquals(200, HeartRateZones.calculateMaxHr(20))
        assertEquals(170, HeartRateZones.calculateMaxHr(50))
        // Clamped at 15 and 90
        assertEquals(205, HeartRateZones.calculateMaxHr(10))
        assertEquals(130, HeartRateZones.calculateMaxHr(95))
    }

    @Test
    fun analyzeFromSamplesDistributesInto5Zones() {
        val maxHr = 190 // 50%=95, 60%=114, 70%=133, 80%=152, 90%=171
        val samples = listOf(
            HeartRateEntity(bpm = 100, accuracy = 3, timestamp = 1000L), // Z1
            HeartRateEntity(bpm = 120, accuracy = 3, timestamp = 2000L), // Z2
            HeartRateEntity(bpm = 140, accuracy = 3, timestamp = 3000L), // Z3
            HeartRateEntity(bpm = 160, accuracy = 3, timestamp = 4000L), // Z4
            HeartRateEntity(bpm = 180, accuracy = 3, timestamp = 5000L)  // Z5
        )

        val result = HeartRateZones.analyzeFromSamples(maxHr, samples)
        assertEquals(190, result.maxHr)
        assertEquals(140, result.avgHr)
        assertEquals(180, result.peakHr)
        assertEquals(5, result.zones.size)

        // Each zone should have 1 sample -> 20%
        result.zones.forEach { zone ->
            assertEquals(0.20f, zone.percentage, 0.01f)
        }
        assertNotNull(result.primaryZone)
    }

    @Test
    fun emptySamplesUsesFallbackDurationAndAverages() {
        val maxHr = 190
        val result = HeartRateZones.analyzeFromSamples(
            maxHr = maxHr,
            samples = emptyList(),
            fallbackAvgBpm = 145,
            fallbackDurationMinutes = 45
        )

        assertEquals(145, result.avgHr)
        assertTrue(result.peakHr >= 145)
        assertEquals(5, result.zones.size)
        val totalMins = result.zones.sumOf { it.minutes }
        assertTrue("Total zone minutes should be around fallback duration", totalMins in 40..50)
    }
}

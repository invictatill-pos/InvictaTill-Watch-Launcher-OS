package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class HealthAppReadingPolicyTest {
    private val now = 1_797_000_000_000L

    private fun reading(value: String = "72", captured: String = (now - 60_000L).toString(),
                        metric: HealthAppMetric = HealthAppMetric.HEART_RATE,
                        unit: HealthAppTimestampUnit = HealthAppTimestampUnit.EPOCH_MILLIS,
                        age: Long = HealthAppReadingPolicy.MAX_AGE_MS) = HealthAppReadingPolicy.parse(
        metric, value, captured, unit, now, age, "com.watch.heartrate", "heartrate", "content://com.watch.health/heart_rate")

    @Test fun `saved app capture preserves original timestamp and app provenance`() {
        val result = reading()!!
        assertEquals(72f, result.value, 0f)
        assertEquals(now - 60_000L, result.capturedAt)
        assertEquals("com.watch.heartrate", result.sourcePackage)
        assertEquals("heartrate", result.sourceLabel)
    }

    @Test fun `sensor quality is not guessed from app records`() {
        assertNotNull(reading("240"))
        assertNull(reading("241"))
        assertNull(reading("24"))
        assertNull(reading("NaN"))
        assertNull(reading("Infinity"))
        assertNull(reading("0"))
    }

    @Test fun `oxygen must be percent saturation and never raw optical value or fraction`() {
        assertNotNull(reading("98.5", metric = HealthAppMetric.OXYGEN))
        assertNull(reading("0.98", metric = HealthAppMetric.OXYGEN))
        assertNull(reading("101", metric = HealthAppMetric.OXYGEN))
        assertNull(reading("49", metric = HealthAppMetric.OXYGEN))
        assertNull(reading("1800", metric = HealthAppMetric.OXYGEN))
    }

    @Test fun `missing stale and future capture times never become current time`() {
        assertNull(reading(captured = ""))
        assertNull(reading(captured = "0"))
        assertNull(reading(captured = (now + 1).toString()))
        assertNull(reading(captured = (now - HealthAppReadingPolicy.MAX_AGE_MS - 1).toString()))
        assertNull(reading(captured = (now - 120_001).toString(), age = 120_000))
        assertNotNull(reading(captured = (now - 120_000).toString(), age = 120_000))
        assertNull(reading(captured = (now - 2 * HealthAppReadingPolicy.MAX_AGE_MS).toString(), age = Long.MAX_VALUE))
    }

    @Test fun `epoch seconds convert exactly while generic uptime and row IDs fail`() {
        assertEquals(now - 60_000L, reading(captured = ((now - 60_000L) / 1000).toString(),
            unit = HealthAppTimestampUnit.EPOCH_SECONDS)!!.capturedAt)
        assertEquals(now - 60_000L, reading(captured = ((now - 60_000L) / 1000).toString(),
            unit = HealthAppTimestampUnit.EPOCH_AUTO)!!.capturedAt)
        assertEquals(now - 60_000L, reading(unit = HealthAppTimestampUnit.EPOCH_AUTO)!!.capturedAt)
        assertNull(reading(captured = "3500", unit = HealthAppTimestampUnit.EPOCH_AUTO))
        assertNull(HealthAppReadingPolicy.capturedAt(Long.MAX_VALUE.toString(), HealthAppTimestampUnit.EPOCH_SECONDS))
    }

    @Test fun `schema requires an explicit metric column and capture timestamp`() {
        val heart = HealthAppReadingPolicy.schemaFromColumns(HealthAppMetric.HEART_RATE,
            listOf("_id", "heart_rate", "captured_at_ms"))!!
        assertEquals("heart_rate", heart.valueColumn)
        assertEquals(HealthAppTimestampUnit.EPOCH_MILLIS, heart.timestampUnit)
        assertNull(HealthAppReadingPolicy.schemaFromColumns(HealthAppMetric.HEART_RATE, listOf("value", "timestamp")))
        assertNull(HealthAppReadingPolicy.schemaFromColumns(HealthAppMetric.HEART_RATE, listOf("spo2", "timestamp")))
        assertNull(HealthAppReadingPolicy.schemaFromColumns(HealthAppMetric.OXYGEN, listOf("bpm", "timestamp")))
        assertNull(HealthAppReadingPolicy.schemaFromColumns(HealthAppMetric.OXYGEN, listOf("spo2", "time", "date", "_id")))
        assertNull(HealthAppReadingPolicy.schemaFromColumns(HealthAppMetric.OXYGEN, listOf("raw_ppg", "timestamp")))
    }

    @Test fun `opt in metadata must explicitly declare units and safe column identifiers`() {
        assertNotNull(HealthAppReadingPolicy.declaredSchema("value", "capture", "bpm", "epoch_ms", HealthAppMetric.HEART_RATE))
        assertNotNull(HealthAppReadingPolicy.declaredSchema("value", "capture", "percent", "epoch_seconds", HealthAppMetric.OXYGEN))
        assertNull(HealthAppReadingPolicy.declaredSchema("value", "capture", "percent", "epoch_ms", HealthAppMetric.HEART_RATE))
        assertNull(HealthAppReadingPolicy.declaredSchema("value", "capture", "bpm", "uptime_ms", HealthAppMetric.HEART_RATE))
        assertNull(HealthAppReadingPolicy.declaredSchema("value", "capture DESC; anything", "bpm", "epoch_ms", HealthAppMetric.HEART_RATE))
    }

    @Test fun `latest app reading uses capture time and deduplicates aliases`() {
        val first = reading()!!
        val second = reading("81", (now - 20_000L).toString())!!
        val alias = second.copy(sourceUri = "content://com.watch.health/heartrate")
        assertEquals(second.captureKey, alias.captureKey)
        assertEquals(second, HealthAppReadingPolicy.latest(listOf(first, second, alias)))
    }

    @Test fun `both named stock watch apps are suggested without matching unrelated packages`() {
        assertTrue(HealthAppReadingPolicy.isSuggestedHealthApp("com.vendor.hr", "heartrate"))
        assertTrue(HealthAppReadingPolicy.isSuggestedHealthApp("com.vendor.ox", "blood oxygen"))
        assertFalse(HealthAppReadingPolicy.isSuggestedHealthApp("com.vendor.filemanager", "Files"))
        assertFalse(HealthAppReadingPolicy.isSuggestedHealthApp("com.vendor.sportsbook", "Sportsbook"))
    }
}

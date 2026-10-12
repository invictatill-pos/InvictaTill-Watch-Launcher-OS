package com.healthsync.watch.sensor

import java.util.Locale

enum class HealthAppMetric { HEART_RATE, OXYGEN }

data class HealthAppReading(
    val metric: HealthAppMetric,
    val value: Float,
    val capturedAt: Long,
    val sourcePackage: String,
    val sourceLabel: String,
    val sourceUri: String
) {
    /** The same capture exposed through two provider paths is still one reading. */
    val captureKey: String get() = "$metric:$sourcePackage:$capturedAt:$value"
}

enum class HealthAppTimestampUnit { EPOCH_MILLIS, EPOCH_SECONDS, EPOCH_AUTO }

data class HealthAppColumnSchema(
    val valueColumn: String,
    val timestampColumn: String,
    val timestampUnit: HealthAppTimestampUnit
)

/** Parse only explicitly named health metrics with an original epoch capture time. */
object HealthAppReadingPolicy {
    const val MAX_AGE_MS = 86_400_000L

    private val heartColumns = listOf("bpm", "heart_rate_bpm", "heart_rate", "heartrate")
    private val oxygenColumns = listOf("spo2_percent", "oxygen_saturation_percent", "spo2", "oxygen_saturation")
    private val timeColumns = listOf(
        "captured_at_ms" to HealthAppTimestampUnit.EPOCH_MILLIS,
        "measured_at_ms" to HealthAppTimestampUnit.EPOCH_MILLIS,
        "timestamp_ms" to HealthAppTimestampUnit.EPOCH_MILLIS,
        "captured_at_seconds" to HealthAppTimestampUnit.EPOCH_SECONDS,
        "measured_at_seconds" to HealthAppTimestampUnit.EPOCH_SECONDS,
        "timestamp_seconds" to HealthAppTimestampUnit.EPOCH_SECONDS,
        "captured_at" to HealthAppTimestampUnit.EPOCH_AUTO,
        "measured_at" to HealthAppTimestampUnit.EPOCH_AUTO,
        "timestamp" to HealthAppTimestampUnit.EPOCH_AUTO
    )

    fun schemaFromColumns(metric: HealthAppMetric, columns: Collection<String>): HealthAppColumnSchema? {
        val originals = columns.associateBy { it.lowercase(Locale.ROOT) }
        val metricColumn = (if (metric == HealthAppMetric.HEART_RATE) heartColumns else oxygenColumns)
            .firstNotNullOfOrNull { originals[it] } ?: return null
        val time = timeColumns.firstOrNull { originals.containsKey(it.first) } ?: return null
        return HealthAppColumnSchema(metricColumn, originals.getValue(time.first), time.second)
    }

    /** An untyped "value" column is accepted only with an explicit provider opt-in contract. */
    fun declaredSchema(valueColumn: String?, timestampColumn: String?, unit: String?, timestampUnit: String?,
                       metric: HealthAppMetric): HealthAppColumnSchema? {
        val expectedUnit = if (metric == HealthAppMetric.HEART_RATE) "bpm" else "percent"
        if (unit != expectedUnit || valueColumn.isNullOrBlank() || timestampColumn.isNullOrBlank()) return null
        val identifier = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
        if (!identifier.matches(valueColumn) || !identifier.matches(timestampColumn)) return null
        val timeUnit = when (timestampUnit) {
            "epoch_ms" -> HealthAppTimestampUnit.EPOCH_MILLIS
            "epoch_seconds" -> HealthAppTimestampUnit.EPOCH_SECONDS
            else -> return null
        }
        return HealthAppColumnSchema(valueColumn, timestampColumn, timeUnit)
    }

    fun parse(metric: HealthAppMetric, valueText: String?, timestampText: String?,
              timestampUnit: HealthAppTimestampUnit, now: Long, maximumAgeMs: Long,
              sourcePackage: String, sourceLabel: String, sourceUri: String): HealthAppReading? {
        val value = valueText?.toFloatOrNull() ?: return null
        if (!value.isFinite()) return null
        val allowed = if (metric == HealthAppMetric.HEART_RATE) 25f..240f else 50f..100f
        if (value !in allowed || sourcePackage.isBlank() || sourceLabel.isBlank() || sourceUri.isBlank()) return null
        val capturedAt = capturedAt(timestampText, timestampUnit) ?: return null
        if (now <= 0L || capturedAt <= 0L || capturedAt > now || maximumAgeMs < 0L) return null
        if (now - capturedAt > maximumAgeMs.coerceAtMost(MAX_AGE_MS)) return null
        return HealthAppReading(metric, value, capturedAt, sourcePackage, sourceLabel, sourceUri)
    }

    fun capturedAt(raw: String?, unit: HealthAppTimestampUnit): Long? {
        val time = raw?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val seconds = unit == HealthAppTimestampUnit.EPOCH_SECONDS ||
            (unit == HealthAppTimestampUnit.EPOCH_AUTO && time in 946_684_800L..4_102_444_800L)
        if (seconds) return if (time <= Long.MAX_VALUE / 1000L) time * 1000L else null
        // Generic timestamps must look like a wall-clock epoch, never elapsed uptime or row IDs.
        if (unit == HealthAppTimestampUnit.EPOCH_AUTO && time !in 946_684_800_000L..4_102_444_800_000L) return null
        return time
    }

    fun latest(readings: Collection<HealthAppReading>): HealthAppReading? =
        readings.distinctBy { it.captureKey }.maxByOrNull { it.capturedAt }

    fun isSuggestedHealthApp(packageName: String, label: String): Boolean {
        val text = "$packageName $label".lowercase(Locale.ROOT)
        val words = text.split(Regex("[^a-z0-9]+"))
        return words.any { it in setOf("health", "fitness", "sport", "sports", "heartrate", "heart", "spo2", "oxygen", "pulse") } ||
            listOf("heart rate", "blood oxygen", "健康", "心率", "血氧").any { text.contains(it) }
    }
}

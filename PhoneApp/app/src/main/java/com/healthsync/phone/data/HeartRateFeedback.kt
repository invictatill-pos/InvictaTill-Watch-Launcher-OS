package com.healthsync.phone.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.healthsync.phone.data.model.HeartRateStatusPayload
import java.util.Locale

/** Device status is feedback, never a replacement for an actual recorded sample. */
data class HeartRateFeedback(val status: String, val updatedAt: Long, val diagnosticReport: String? = null,
                             val sensorPreview: UnverifiedHeartRatePreview? = null)

/** Kept in memory only. It must never be converted to a HeartRateEntity or workout sample. */
data class UnverifiedHeartRatePreview(val bpm: Int, val capturedAt: Long)

private const val SENSOR_PREVIEW_MAX_AGE_MS = 86_400_000L
private const val SENSOR_PREVIEW_FUTURE_TOLERANCE_MS = 5_000L
private val SENSOR_PREVIEW_STATUSES = setOf("unreliable_accuracy", "measuring", "requesting", "idle", "disabled",
    "no_contact", "no_events", "invalid_samples", "sensor_error", "service_unavailable", "permission_required", "missing_sensor")

private fun validSensorPreviewTime(timestamp: Long, now: Long): Boolean =
    timestamp > 0L && now > 0L &&
        (timestamp <= now || timestamp - now <= SENSOR_PREVIEW_FUTURE_TOLERANCE_MS) &&
        (timestamp >= now || now - timestamp <= SENSOR_PREVIEW_MAX_AGE_MS)

internal fun validatedHeartRateSensorPreview(payload: HeartRateStatusPayload?, now: Long): UnverifiedHeartRatePreview? {
    if (payload == null || payload.status.trim().lowercase(Locale.ROOT) !in SENSOR_PREVIEW_STATUSES ||
        payload.sensorBpm !in 25..240 || !validSensorPreviewTime(payload.sensorReadingTime, now)) return null
    return UnverifiedHeartRatePreview(payload.sensorBpm, payload.sensorReadingTime)
}

fun activeHeartRateSensorPreview(feedback: HeartRateFeedback?, connected: Boolean,
                               now: Long): UnverifiedHeartRatePreview? = feedback?.sensorPreview?.takeIf {
    connected && feedback.status in SENSOR_PREVIEW_STATUSES && it.bpm in 25..240 && validSensorPreviewTime(it.capturedAt, now)
}

/** Disconnect discards feedback; reconnect cannot restore a previous session's preview. */
internal fun currentHeartRateFeedback(feedback: HeartRateFeedback?, connected: Boolean, now: Long): HeartRateFeedback? =
    if (!connected) null else feedback?.let {
        val active = activeHeartRateSensorPreview(it, true, now)
        if (active == it.sensorPreview) it else it.copy(sensorPreview = active)
    }

internal fun validatedHeartRateStatus(payload: HeartRateStatusPayload?, now: Long): String? {
    val status = payload?.status?.trim()?.lowercase(Locale.ROOT) ?: return null
    if (status !in setOf("missing_sensor", "permission_required", "measuring", "reading", "no_contact",
            "sensor_error", "service_unavailable", "disabled", "idle", "no_events", "invalid_samples",
            "unreliable_accuracy")) return null
    if (payload.bpm != -1 && payload.bpm !in 25..240) return null
    if (payload.readingTime != 0L && !SyncValidation.validTimestamp(payload.readingTime, now)) return null
    if (payload.bpm in 25..240 && payload.readingTime <= 0L) return null
    if (status == "reading" && (payload.bpm !in 25..240 || payload.readingTime <= 0L)) return null
    return status
}

/** Read raw fields so strings, fractions, overflow and null cannot become a plausible preview through Gson coercion. */
internal fun heartRateFeedbackFromJson(json: String, now: Long): HeartRateFeedback {
    return runCatching {
        val obj = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
            ?: return HeartRateFeedback("invalid_reading", now)
        val status = obj.text("status") ?: return HeartRateFeedback("invalid_reading", now)
        val bpm = if (obj.has("bpm")) obj.integer("bpm")?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
            ?: return HeartRateFeedback("invalid_reading", now) else -1
        val readingTime = if (obj.has("readingTime")) obj.integer("readingTime")
            ?: return HeartRateFeedback("invalid_reading", now) else 0L
        val payload = HeartRateStatusPayload(status, bpm, readingTime, obj.text("diagnosticReport"),
            obj.integer("sensorBpm")?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt() ?: -1,
            obj.integer("sensorReadingTime") ?: 0L)
        val validatedStatus = validatedHeartRateStatus(payload, now) ?: return HeartRateFeedback("invalid_reading", now)
        HeartRateFeedback(validatedStatus, now, validatedHeartRateDiagnosticReport(payload.diagnosticReport),
            validatedHeartRateSensorPreview(payload, now))
    }.getOrElse { HeartRateFeedback("invalid_reading", now) }
}

private fun JsonObject.text(key: String): String? = get(key)?.takeIf {
    it.isJsonPrimitive && it.asJsonPrimitive.isString
}?.asString

private fun JsonObject.integer(key: String): Long? = get(key)?.takeIf {
    it.isJsonPrimitive && it.asJsonPrimitive.isNumber
}?.let { runCatching { it.asBigDecimal.longValueExact() }.getOrNull() }

/** Diagnostics explain acquisition failures; they never become a health record. */
internal fun validatedHeartRateDiagnosticReport(report: String?): String? = report?.trim()?.takeIf {
    it.isNotEmpty() && it.length <= 8_192 && it.none { character ->
        character.code < 32 && character !in "\n\r\t" || character.code == 127
    }
}

fun heartRateFeedbackText(feedback: HeartRateFeedback?, connected: Boolean,
                         readingTime: Long?, now: Long): String {
    if (!connected) return "Connect watch to measure"
    activeHeartRateSensorPreview(feedback, connected, now)?.let {
        val prefix = when (feedback?.status) {
            "requesting" -> "Request sent"
            "measuring" -> "Measuring"
            "disabled" -> "Automatic off"
            "permission_required" -> "Allow watch Sensors"
            "missing_sensor" -> "Sensor unavailable"
            "service_unavailable" -> "Open Health on watch"
            "sensor_error" -> "Refresh could not start"
            "no_contact" -> "Check wrist contact"
            "no_events", "invalid_samples" -> "No new reading"
            else -> "Sensor reading"
        }
        return "$prefix · ${sensorPreviewAge(it, now)}"
    }
    val status = feedback?.status
    if (status in setOf("requesting", "measuring") && feedback != null &&
        now - feedback.updatedAt > 90_000L) return "No response · check Health on watch"
    return when (status) {
        "requesting" -> "Request sent · waiting for watch"
        "measuring" -> "Measuring · keep wrist still"
        "missing_sensor" -> "Heart-rate sensor unavailable on watch"
        "permission_required" -> "Allow Sensors in watch settings"
        "no_contact" -> "No reading · adjust watch and retry"
        "no_events" -> "Sensor sent no data · open Health on watch"
        "invalid_samples" -> "Sensor data rejected · check measurement details"
        "unreliable_accuracy" -> "Tap to refresh the watch sensor reading"
        "sensor_error" -> "Watch sensor could not start · retry"
        "service_unavailable" -> "Open Health on watch to start sensors"
        "invalid_reading" -> "Reading rejected · check watch time and Sensors"
        "disabled" -> "Automatic checks off · tap to measure"
        else -> {
            if (readingTime == null || readingTime <= 0L) "Tap to measure on your watch"
            else {
                val minutes = ((now - readingTime) / 60_000L).coerceAtLeast(0L)
                when {
                    minutes < 1L -> "Last reading · just received"
                    minutes < 60L -> "Last reading · $minutes min ago"
                    minutes < 1440L -> "Last reading · ${minutes / 60L}h ${minutes % 60L}m ago"
                    else -> "Last reading · ${minutes / 1440L} days ago"
                }
            }
        }
    }
}

private fun sensorPreviewAge(preview: UnverifiedHeartRatePreview, now: Long): String {
    val seconds = ((now - preview.capturedAt) / 1_000L).coerceAtLeast(0L)
    return when {
        seconds == 0L -> "just received"
        seconds < 60L -> "$seconds sec ago"
        seconds < 3600L -> "${seconds / 60L} min ago"
        else -> "${seconds / 3600L} h ago"
    }
}

fun heartRateMeasurementDetailsText(feedback: HeartRateFeedback?, connected: Boolean, now: Long): String? {
    val preview = activeHeartRateSensorPreview(feedback, connected, now)
    val explanation = if (preview != null || feedback?.status == "unreliable_accuracy") {
        val value = preview?.let { "${it.bpm} bpm · unverified · ${sensorPreviewAge(it, now)}" }
            ?: "No current unverified sensor value"
        "$value\nThe watch marks this signal unreliable (accuracy 0). Unverified values are not saved to history or used in workouts."
    } else null
    return listOfNotNull(explanation, feedback?.diagnosticReport).takeIf { it.isNotEmpty() }?.joinToString("\n\n")
}

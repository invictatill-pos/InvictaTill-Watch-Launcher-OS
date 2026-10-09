package com.healthsync.phone.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.healthsync.phone.data.model.LatLngPoint
import com.healthsync.phone.data.model.MessageType

/** Reject corrupt records before they enter health history or receive a sync acknowledgement. */
object SyncValidation {
    private const val FUTURE_TOLERANCE_MS = 5 * 60_000L
    fun validTimestamp(timestamp: Long, now: Long = System.currentTimeMillis()) =
        timestamp > 0 && now > 0 && (timestamp <= now || timestamp - now <= FUTURE_TOLERANCE_MS)

    fun validHeartRate(bpm: Int, accuracy: Int, timestamp: Long, now: Long = System.currentTimeMillis()) =
        bpm in 25..240 && accuracy in 1..3 && validTimestamp(timestamp, now)

    fun validSpO2(percentage: Float, timestamp: Long, now: Long = System.currentTimeMillis()) =
        percentage.isFinite() && percentage in 50f..100f && validTimestamp(timestamp, now)

    fun validSteps(steps: Int, calories: Double) =
        steps in 0..1_000_000 && calories.isFinite() && calories in 0.0..100_000.0

    fun validWorkout(start: Long, end: Long, distance: Float, calories: Double,
                     steps: Int, heartRate: Int, swings: Int, now: Long = System.currentTimeMillis()) =
        validTimestamp(start, now) && validTimestamp(end, now) && end > start &&
            distance.isFinite() && distance in 0f..1_000_000f && validSteps(steps, calories) &&
            (heartRate == 0 || heartRate in 25..240) && swings in 0..1_000_000

    fun validWorkoutRecord(activity: String?, start: Long, end: Long, durationSeconds: Int,
                           distance: Float, calories: Double, steps: Int, heartRate: Int,
                           swings: Int, now: Long = System.currentTimeMillis()) =
        !activity.isNullOrBlank() && activity.length <= 64 &&
            validWorkout(start, end, distance, calories, steps, heartRate, swings, now) &&
            durationSeconds >= 0 && durationSeconds.toLong() <= (end - start) / 1000L

    fun validSleep(start: Long, end: Long, deep: Int, light: Int, rem: Int, awake: Int,
                   now: Long = System.currentTimeMillis()): Boolean {
        if (!validTimestamp(start, now) || !validTimestamp(end, now) || end <= start) return false
        val stages = listOf(deep, light, rem, awake)
        val total = stages.sumOf { it.toLong() }
        return stages.all { it >= 0 } && total > 0 && total <= (end - start) / 60_000L + 5
    }

    /** A rejected fix must break the next line, including when it carried a segment marker. */
    fun sanitizeRoute(points: List<LatLngPoint?>?, start: Long, end: Long): List<LatLngPoint> {
        val result = ArrayList<LatLngPoint>()
        var gap = true
        var previousTime: Long? = null
        for (point in points.orEmpty()) {
            val valid = point != null && point.lat.isFinite() && point.lng.isFinite() &&
                point.lat in -90.0..90.0 && point.lng in -180.0..180.0 &&
                point.timestamp > 0 && point.timestamp >= (start - 30_000L).coerceAtLeast(1L) &&
                point.timestamp <= end && (previousTime?.let { point.timestamp > it } != false)
            if (!valid) { gap = true; continue }
            point!!
            val resumed = previousTime?.let { point.timestamp - it > 30_000L } ?: true
            result.add(point.copy(segmentStart = gap || resumed || point.segmentStart))
            previousTime = point.timestamp
            gap = false
            if (result.size == 20_000) break
        }
        return result
    }

    /** Null means malformed outer JSON; malformed individual fixes create a gap. */
    fun sanitizeRouteJson(json: String?, start: Long, end: Long): String? = runCatching {
        val array = JsonParser.parseString(json ?: return null).takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val points = array.map { element ->
            val point = element.takeIf { it.isJsonObject }?.asJsonObject
            val lat = point?.number("lat")
            val lng = point?.number("lng")
            val time = point?.integer("timestamp")
            val segment = point?.get("segmentStart")
            if (lat == null || lng == null || time == null ||
                (segment != null && (!segment.isJsonPrimitive || !segment.asJsonPrimitive.isBoolean))) null
            else LatLngPoint(lat, lng, time, segment?.asBoolean ?: false)
        }
        Gson().toJson(sanitizeRoute(points, start, end))
    }.getOrNull()

    /** Check raw fields before Gson can replace missing/null primitives with zero. */
    fun validIncomingHealthPayload(type: MessageType, payloadJson: String, timestamp: Long,
                                   now: Long = System.currentTimeMillis()): Boolean = runCatching {
        val obj = jsonObject(payloadJson) ?: return false
        when (type) {
            MessageType.HEART_RATE -> validHeartRate(obj.int("bpm") ?: return false,
                obj.int("accuracy") ?: return false, timestamp, now)
            MessageType.SPO2 -> {
                val percentage = obj.number("percentage") ?: return false
                percentage in 50.0..100.0 && validSpO2(percentage.toFloat(), timestamp, now)
            }
            MessageType.STEPS -> validTimestamp(timestamp, now) && validSteps(
                obj.int("steps") ?: return false, obj.number("calories") ?: return false)
            MessageType.SLEEP -> validTimestamp(timestamp, now) && validSleep(
                obj.integer("startTime") ?: return false, obj.integer("endTime") ?: return false,
                obj.int("deepMinutes") ?: return false, obj.int("lightMinutes") ?: return false,
                obj.int("remMinutes") ?: return false, obj.int("awakeMinutes") ?: return false, now)
            MessageType.FALL_DETECTED -> validTimestamp(timestamp, now) && obj.text("severity") in setOf("LOW", "HIGH")
            MessageType.WORKOUT_SESSION -> validTimestamp(timestamp, now) && validIncomingWorkout(obj, false, now)
            // Record checks happen independently so a corrupt row does not block valid rows or get an ACK.
            MessageType.BATCHED_SYNC -> optionalArray(obj, "workouts") && optionalArray(obj, "daily_steps")
            else -> true
        }
    }.getOrDefault(false)

    fun validIncomingWorkoutSession(json: String, now: Long = System.currentTimeMillis()): Boolean = runCatching {
        validIncomingWorkout(jsonObject(json) ?: return false, true, now)
    }.getOrDefault(false)

    fun validIncomingStepBucket(json: String, now: Long = System.currentTimeMillis()): Boolean = runCatching {
        val obj = jsonObject(json) ?: return false
        validTimestamp(obj.integer("timestamp") ?: return false, now) &&
            validSteps(obj.int("steps") ?: return false, obj.number("calories") ?: return false)
    }.getOrDefault(false)

    private fun validIncomingWorkout(obj: JsonObject, batch: Boolean, now: Long): Boolean {
        val start = obj.integer(if (batch) "start_time" else "startTime") ?: return false
        val end = obj.integer(if (batch) "end_time" else "endTime") ?: return false
        if (batch && obj.text("session_id").let { it.isNullOrBlank() || it.length > 200 }) return false
        val durationKey = if (batch) "duration_sec" else "durationSeconds"
        val duration = if (!batch && !obj.has(durationKey)) 0 else obj.int(durationKey) ?: return false
        val swings = if (!obj.has("swings")) 0 else obj.int("swings") ?: return false
        val distance = obj.number(if (batch) "distance_m" else "distanceMeters") ?: return false
        if (distance !in 0.0..1_000_000.0) return false
        if (!validWorkoutRecord(obj.text(if (batch) "activity_type" else "activityType"), start, end, duration,
                distance.toFloat(),
                obj.number("calories") ?: return false, obj.int(if (batch) "step_count" else "steps") ?: return false,
                obj.int(if (batch) "avg_hr" else "avgHeartRate") ?: return false, swings, now)) return false
        return if (batch) !obj.has("route_json") || sanitizeRouteJson(obj.text("route_json"), start, end) != null
        else !obj.has("route") || (obj.get("route").isJsonArray && obj.getAsJsonArray("route").all { element ->
            // Invalid numeric fixes can be dropped later; absent coordinates cannot be read as (0, 0).
            element.isJsonNull || element.isJsonObject && element.asJsonObject.let { point ->
                point.number("lat") != null && point.number("lng") != null && point.integer("timestamp") != null &&
                    (!point.has("segmentStart") || point.get("segmentStart").let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean })
            }
        })
    }

    private fun jsonObject(json: String): JsonObject? = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
    private fun optionalArray(obj: JsonObject, key: String) = !obj.has(key) || obj.get(key).isJsonArray
    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun JsonObject.number(key: String): Double? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asDouble?.takeIf { it.isFinite() }
    private fun JsonObject.integer(key: String): Long? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.let { runCatching { it.asBigDecimal.longValueExact() }.getOrNull() }
    private fun JsonObject.int(key: String): Int? = integer(key)?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
}

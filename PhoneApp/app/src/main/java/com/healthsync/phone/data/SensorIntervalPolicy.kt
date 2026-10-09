package com.healthsync.phone.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.healthsync.phone.data.model.SensorIntervalsPayload

/** Last-edit revisions use wall time, but remain monotonic if the local clock moves backward. */
object SensorIntervalPolicy {
    fun validInterval(value: Long) = value == -1L || value in 30_000L..3_600_000L
    // Incoming epoch revisions reserve arithmetic headroom for subsequent local edits.
    fun valid(snapshot: SensorIntervalsPayload) = validInterval(snapshot.hrIntervalMs) &&
        validInterval(snapshot.spo2IntervalMs) && snapshot.revision in 0L..Long.MAX_VALUE / 2L

    fun nextRevision(previous: Long, now: Long): Long {
        require(previous >= 0L && previous < Long.MAX_VALUE)
        return maxOf(previous + 1L, now)
    }

    fun localEdit(current: SensorIntervalsPayload, hr: Long, oxygen: Long, now: Long,
                  forceRevision: Boolean = false): SensorIntervalsPayload {
        require(validInterval(current.hrIntervalMs) && validInterval(current.spo2IntervalMs) &&
            current.revision >= 0L && current.revision < Long.MAX_VALUE && validInterval(hr) && validInterval(oxygen))
        if (!forceRevision && current.hrIntervalMs == hr && current.spo2IntervalMs == oxygen) return current
        return SensorIntervalsPayload(hr, oxygen, nextRevision(current.revision, now))
    }

    fun shouldApply(local: SensorIntervalsPayload, incoming: SensorIntervalsPayload,
                    acceptLegacyWatchBaseline: Boolean = false): Boolean = valid(incoming) &&
        (incoming.revision > local.revision || incoming == local ||
            (acceptLegacyWatchBaseline && local.revision == 0L && incoming.revision == 0L))

    /** Required fields are checked before Gson can manufacture missing primitive values. */
    fun parse(json: String): SensorIntervalsPayload? = parseFields(json, "revision", false)
    fun parseSettings(json: String): SensorIntervalsPayload? = parseFields(json, "sensorSettingsRevision", true)

    private fun parseFields(json: String, revisionField: String, legacyRevision: Boolean): SensorIntervalsPayload? = runCatching {
        val fields = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val hr = fields.integer("hrIntervalMs") ?: return null
        val oxygen = fields.integer("spo2IntervalMs") ?: return null
        val revision = if (legacyRevision && !fields.has(revisionField)) 0L else fields.integer(revisionField) ?: return null
        SensorIntervalsPayload(hr, oxygen, revision).takeIf(::valid)
    }.getOrNull()

    private fun JsonObject.integer(key: String): Long? {
        val primitive = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        if (!primitive.isNumber) return null
        return runCatching { primitive.asBigDecimal.longValueExact() }.getOrNull()
    }
}

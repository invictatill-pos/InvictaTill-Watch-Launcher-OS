package com.healthsync.watch.ui

import kotlin.math.roundToInt

internal object CompassDisplayPolicy {
    fun smooth(previous: Float?, measured: Float): Float? {
        if (!measured.isFinite()) return null
        val heading = ((measured % 360f) + 360f) % 360f
        if (previous == null || !previous.isFinite()) return heading
        val difference = (heading - previous + 540f) % 360f - 180f
        return (previous + difference * .25f + 360f) % 360f
    }
    fun cardinal(heading: Float): String {
        val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val degrees = ((heading % 360f) + 360f) % 360f
        return directions[(degrees / 45f).roundToInt() % directions.size]
    }
    fun locationIsRecent(capturedAt: Long, nowMs: Long, mock: Boolean): Boolean =
        !mock && capturedAt > 0 && nowMs >= capturedAt && nowMs - capturedAt <= 120_000L
}

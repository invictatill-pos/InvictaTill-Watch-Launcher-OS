package com.healthsync.watch.algorithm

import com.healthsync.watch.data.LatLngPoint
import java.util.Locale

fun workoutUsesLocation(type: String): Boolean = type.lowercase(Locale.ROOT).trim() in setOf("walk", "run", "running", "cycling", "basketball", "cricket")
fun workoutUsesStepDistance(type: String): Boolean = type.lowercase(Locale.ROOT).trim() in setOf("walk", "run", "running")

enum class WorkoutLocationState { NOT_USED, PAUSED, PERMISSION_REQUIRED, PRECISE_REQUIRED, NO_PROVIDER, DISABLED, GPS_DISABLED, UNAVAILABLE, SEARCHING, STALE, GPS_FIX, LOCATION_FIX }

internal fun workoutLocationState(
    usesLocation: Boolean, paused: Boolean, fine: Boolean, coarse: Boolean,
    availableProviders: Set<String>, enabledProviders: Set<String>, registeredProviders: Set<String>,
    lastFixElapsed: Long, nowElapsed: Long, lastProvider: String
): WorkoutLocationState = when {
    !usesLocation -> WorkoutLocationState.NOT_USED
    paused -> WorkoutLocationState.PAUSED
    !fine && !coarse -> WorkoutLocationState.PERMISSION_REQUIRED
    !fine -> WorkoutLocationState.PRECISE_REQUIRED
    availableProviders.isEmpty() -> WorkoutLocationState.NO_PROVIDER
    enabledProviders.isEmpty() -> WorkoutLocationState.DISABLED
    registeredProviders.intersect(enabledProviders).isEmpty() -> WorkoutLocationState.UNAVAILABLE
    "gps" in availableProviders && "gps" !in enabledProviders &&
        (lastFixElapsed < 0 || nowElapsed - lastFixElapsed !in 0L..30_000L) -> WorkoutLocationState.GPS_DISABLED
    lastFixElapsed < 0 -> WorkoutLocationState.SEARCHING
    nowElapsed - lastFixElapsed !in 0L..30_000L -> WorkoutLocationState.STALE
    lastProvider == "gps" -> WorkoutLocationState.GPS_FIX
    else -> WorkoutLocationState.LOCATION_FIX
}

internal fun validWorkoutLocation(latitude: Double, longitude: Double, accuracy: Float?, fixElapsed: Long, nowElapsed: Long): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        accuracy != null && accuracy.isFinite() && accuracy in 0f..50f && fixElapsed > 0 && nowElapsed - fixElapsed in 0L..30_000L

internal fun workoutFixElapsedTime(elapsedNanos: Long, wallTime: Long, nowElapsed: Long, nowWall: Long): Long? {
    if (elapsedNanos < 0) return null
    if (elapsedNanos > 0) return elapsedNanos / 1_000_000L
    if (wallTime <= 0 || wallTime > nowWall) return null
    return nowElapsed - (nowWall - wallTime)
}

internal fun validWorkoutFixOrder(fixElapsed: Long, registeredAt: Long, previousAccepted: Long): Boolean =
    fixElapsed >= registeredAt && (previousAccepted < 0 || fixElapsed > previousAccepted)

internal fun workoutRouteWallTime(fixElapsed: Long, nowElapsed: Long, nowWall: Long): Long? {
    val age = nowElapsed - fixElapsed
    if (fixElapsed <= 0 || age !in 0L..30_000L) return null
    return (nowWall - age).takeIf { it > 0 }
}

internal fun shouldBreakDiscardedWorkoutRoute(provider: String?, routeProvider: String?, fixElapsed: Long?, lastAccepted: Long): Boolean =
    routeProvider != null && provider == routeProvider && (fixElapsed == null || fixElapsed > lastAccepted)

internal fun validWorkoutMotionSample(values: FloatArray, maximumRange: Float, timestampMs: Long, nowElapsed: Long, previousTimestamp: Long, registeredAt: Long): Boolean {
    if (values.size < 3 || !values.take(3).all { value -> value.isFinite() &&
        (!maximumRange.isFinite() || maximumRange <= 0 || kotlin.math.abs(value) <= maximumRange) }) return false
    // Protect consumers that square Float components before converting the magnitude to Double.
    // This arithmetic guard still applies when a vendor omits valid maximum-range metadata.
    val squared = values[0].toDouble() * values[0] + values[1].toDouble() * values[1] + values[2].toDouble() * values[2]
    if (squared > Float.MAX_VALUE || !(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).isFinite()) return false
    return timestampMs >= registeredAt && timestampMs > previousTimestamp && nowElapsed - timestampMs in 0L..5_000L
}

internal enum class RouteSegmentDecision { REJECT, ORIGIN, STATIONARY, MOVE }

internal fun workoutRouteSegmentDecision(deltaSeconds: Double, distanceMeters: Float, previousAccuracy: Float, accuracy: Float, cycling: Boolean): RouteSegmentDecision {
    if (!deltaSeconds.isFinite() || deltaSeconds <= 0 || !distanceMeters.isFinite() || distanceMeters < 0f ||
        !previousAccuracy.isFinite() || previousAccuracy !in 0f..50f || !accuracy.isFinite() || accuracy !in 0f..50f) return RouteSegmentDecision.REJECT
    if (deltaSeconds > 30.0) return RouteSegmentDecision.ORIGIN
    val maximumSpeed = if (cycling) 45.0 else 25.0
    if (distanceMeters / deltaSeconds > maximumSpeed) return RouteSegmentDecision.REJECT
    val threshold = maxOf(2f, minOf(previousAccuracy, accuracy) * .25f)
    return if (distanceMeters >= threshold) RouteSegmentDecision.MOVE else RouteSegmentDecision.STATIONARY
}

/** Keep segment boundaries and both endpoints when bounding long sessions. */
internal fun thinWorkoutRoute(points: List<LatLngPoint>): List<LatLngPoint> = points.filterIndexed { index, point ->
    index == 0 || index == points.lastIndex || index % 2 == 0 || point.segmentStart || points.getOrNull(index + 1)?.segmentStart == true
}

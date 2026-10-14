package com.healthsync.watch.algorithm

import com.healthsync.watch.data.LatLngPoint
import java.util.Locale

fun workoutUsesLocation(type: String): Boolean = type.lowercase(Locale.ROOT).trim() in setOf("walk", "run", "running", "cycling", "basketball", "cricket")
fun workoutUsesStepDistance(type: String): Boolean = type.lowercase(Locale.ROOT).trim() in setOf("walk", "run", "running")

fun workoutDistanceSource(usesLocation: Boolean, observedRoute: Boolean, stepEstimate: Boolean): String = when {
    !usesLocation -> "NOT_APPLICABLE"
    observedRoute && stepEstimate -> "LOCATION_AND_STEP_ESTIMATE"
    stepEstimate -> "STEP_ESTIMATE"
    observedRoute -> "LOCATION"
    else -> "UNAVAILABLE"
}

enum class WorkoutLocationState { NOT_USED, PAUSED, PERMISSION_REQUIRED, PRECISE_REQUIRED, NO_PROVIDER, DISABLED, GPS_DISABLED, UNAVAILABLE, SEARCHING, WEAK_FIX, STALE, GPS_FIX, LOCATION_FIX }

/** Passive locations originate in another provider; keep that provider as their real source. */
internal fun workoutLocationProviders(available: Collection<String>, fine: Boolean): Set<String> =
    available.filter { it.isNotBlank() && (fine || it == "network") }.toSet()

internal fun workoutLocationSourceAllowed(provider: String?, registered: Set<String>, enabled: Boolean = true): Boolean =
    enabled && !provider.isNullOrBlank() && (provider in registered || "passive" in registered)

/** Give a cold GNSS receiver time to acquire satellites; do not disrupt ongoing tracking. */
internal fun shouldRecoverWorkoutLocation(registeredAt: Long, lastFixElapsed: Long, lastRecoveryElapsed: Long, nowElapsed: Long): Boolean {
    val latestProgress = maxOf(registeredAt, lastFixElapsed, lastRecoveryElapsed)
    return registeredAt >= 0 && nowElapsed - latestProgress >= 900_000L
}

const val MAX_WORKOUT_ACCURACY_METERS = 120f

internal fun workoutLocationState(
    usesLocation: Boolean, paused: Boolean, fine: Boolean, coarse: Boolean,
    availableProviders: Set<String>, enabledProviders: Set<String>, registeredProviders: Set<String>,
    lastFixElapsed: Long, nowElapsed: Long, lastProvider: String,
    lastSignalElapsed: Long = -1, reportedAccuracy: Float = -1f
): WorkoutLocationState = when {
    !usesLocation -> WorkoutLocationState.NOT_USED
    paused -> WorkoutLocationState.PAUSED
    !fine && !coarse -> WorkoutLocationState.PERMISSION_REQUIRED
    !fine -> WorkoutLocationState.PRECISE_REQUIRED
    lastFixElapsed > 0 && nowElapsed - lastFixElapsed in 0L..30_000L &&
        (lastProvider !in availableProviders || lastProvider in enabledProviders) ->
        if (lastProvider == "gps") WorkoutLocationState.GPS_FIX else WorkoutLocationState.LOCATION_FIX
    availableProviders.isEmpty() -> WorkoutLocationState.NO_PROVIDER
    enabledProviders.isEmpty() -> WorkoutLocationState.DISABLED
    registeredProviders.intersect(enabledProviders).isEmpty() -> WorkoutLocationState.UNAVAILABLE
    "gps" in availableProviders && "gps" !in enabledProviders -> WorkoutLocationState.GPS_DISABLED
    lastSignalElapsed > 0 && nowElapsed - lastSignalElapsed in 0L..30_000L && reportedAccuracy.isFinite() && reportedAccuracy > MAX_WORKOUT_ACCURACY_METERS -> WorkoutLocationState.WEAK_FIX
    lastFixElapsed < 0 -> WorkoutLocationState.SEARCHING
    else -> WorkoutLocationState.STALE
}

internal fun validWorkoutLocation(
    latitude: Double, longitude: Double, accuracy: Float?, fixElapsed: Long, nowElapsed: Long,
    mock: Boolean = false, maxAccuracy: Float = MAX_WORKOUT_ACCURACY_METERS,
    maxAgeMs: Long = 30_000L
): Boolean =
    !mock &&
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        accuracy != null && accuracy.isFinite() && accuracy in 0f..maxAccuracy &&
        fixElapsed > 0 && (nowElapsed - fixElapsed) in 0L..maxAgeMs

internal fun workoutFixElapsedTime(
    elapsedNanos: Long, wallTime: Long, nowElapsed: Long, nowWall: Long,
    maxForwardSkewMs: Long = 15_000L
): Long? {
    if (elapsedNanos < 0) return null
    if (elapsedNanos > 0) {
        val elapsedMs = elapsedNanos / 1_000_000L
        if (elapsedMs > nowElapsed + maxForwardSkewMs) return null
        return elapsedMs.coerceAtMost(nowElapsed)
    }
    if (wallTime <= 0 || wallTime > nowWall + maxForwardSkewMs) return null
    if (wallTime > nowWall) return nowElapsed
    return nowElapsed - (nowWall - wallTime)
}

internal fun validWorkoutFixOrder(
    fixElapsed: Long, registeredAt: Long, previousAccepted: Long,
    allowRecentOrigin: Boolean = false
): Boolean =
    (fixElapsed >= registeredAt || (allowRecentOrigin && fixElapsed >= registeredAt - 120_000L)) &&
    (previousAccepted < 0 || fixElapsed > previousAccepted)

internal fun workoutRouteWallTime(fixElapsed: Long, nowElapsed: Long, nowWall: Long, maxAgeMs: Long = 30_000L): Long? {
    val age = nowElapsed - fixElapsed
    if (fixElapsed <= 0 || age !in 0L..maxAgeMs) return null
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

internal fun workoutRouteSegmentDecision(
    deltaSeconds: Double, distanceMeters: Float, previousAccuracy: Float, accuracy: Float,
    cycling: Boolean, maxAccuracy: Float = MAX_WORKOUT_ACCURACY_METERS
): RouteSegmentDecision {
    if (!deltaSeconds.isFinite() || deltaSeconds <= 0 || !distanceMeters.isFinite() || distanceMeters < 0f ||
        !previousAccuracy.isFinite() || previousAccuracy !in 0f..maxAccuracy ||
        !accuracy.isFinite() || accuracy !in 0f..maxAccuracy) return RouteSegmentDecision.REJECT
    if (deltaSeconds > 30.0) return RouteSegmentDecision.ORIGIN
    val maximumSpeed = if (cycling) 45.0 else 25.0
    if (distanceMeters / deltaSeconds > maximumSpeed) return RouteSegmentDecision.REJECT
    val threshold = minOf(12f, maxOf(2f, minOf(previousAccuracy, accuracy) * .25f))
    return if (distanceMeters >= threshold) RouteSegmentDecision.MOVE else RouteSegmentDecision.STATIONARY
}

/** Keep segment boundaries and both endpoints when bounding long sessions. */
internal fun thinWorkoutRoute(points: List<LatLngPoint>): List<LatLngPoint> = points.filterIndexed { index, point ->
    index == 0 || index == points.lastIndex || index % 2 == 0 || point.segmentStart || points.getOrNull(index + 1)?.segmentStart == true
}

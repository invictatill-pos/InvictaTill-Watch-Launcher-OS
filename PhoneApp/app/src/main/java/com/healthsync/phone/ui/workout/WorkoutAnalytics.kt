package com.healthsync.phone.ui.workout

import com.healthsync.phone.data.model.LatLngPoint
import kotlin.math.*

data class CapturedKmSplit(val kmNumber: Int, val paceSecondsPerKm: Long)

/** Split time uses recorded consecutive fixes only. A pause or missing fix starts a new segment. */
fun calculateCapturedSplits(route: List<LatLngPoint>): List<CapturedKmSplit> {
    val result = mutableListOf<CapturedKmSplit>()
    var segmentDistance = 0.0
    var segmentSeconds = 0.0
    var splitNumber = 1
    for ((previous, current) in route.zipWithNext()) {
        val elapsed = (current.timestamp - previous.timestamp) / 1000.0
        if (current.segmentStart || elapsed <= 0 || elapsed > 30) {
            segmentDistance = 0.0
            segmentSeconds = 0.0
            continue
        }
        val distance = capturedDistanceMeters(previous, current)
        if (!distance.isFinite() || distance <= 0) continue
        // Reject impossible location jumps; never turn a gap into a fast kilometre.
        if (distance / elapsed > 30.0) {
            segmentDistance = 0.0
            segmentSeconds = 0.0
            continue
        }
        var remainingDistance = distance
        var remainingSeconds = elapsed
        while (segmentDistance + remainingDistance >= 1000.0) {
            val toBoundary = 1000.0 - segmentDistance
            val boundarySeconds = remainingSeconds * (toBoundary / remainingDistance)
            result += CapturedKmSplit(splitNumber++, (segmentSeconds + boundarySeconds).roundToLong().coerceAtLeast(1))
            remainingDistance -= toBoundary
            remainingSeconds -= boundarySeconds
            segmentDistance = 0.0
            segmentSeconds = 0.0
        }
        segmentDistance += remainingDistance
        segmentSeconds += remainingSeconds
    }
    return result
}

private fun capturedDistanceMeters(a: LatLngPoint, b: LatLngPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 6_371_000.0 * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1 - h).coerceIn(0.0, 1.0)))
}

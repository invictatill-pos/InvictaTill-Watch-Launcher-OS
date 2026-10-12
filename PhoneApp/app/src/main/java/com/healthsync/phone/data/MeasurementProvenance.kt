package com.healthsync.phone.data

/** Older records remain visibly unspecified instead of being described as measured GPS. */
fun validatedDistanceSource(source: String?): String = source?.takeIf {
    it in setOf("LOCATION", "STEP_ESTIMATE", "LOCATION_AND_STEP_ESTIMATE", "UNAVAILABLE", "NOT_APPLICABLE", "UNKNOWN")
} ?: "UNKNOWN"

fun distanceSourceLabel(source: String?): String = when (validatedDistanceSource(source)) {
    "LOCATION" -> "From captured location fixes"
    "STEP_ESTIMATE" -> "Estimated from captured steps and profile"
    "LOCATION_AND_STEP_ESTIMATE" -> "Location fixes with step estimates for gaps"
    "UNAVAILABLE" -> "Distance was unavailable"
    "NOT_APPLICABLE" -> "Distance does not apply to this activity"
    else -> "Distance source unavailable for this older session"
}

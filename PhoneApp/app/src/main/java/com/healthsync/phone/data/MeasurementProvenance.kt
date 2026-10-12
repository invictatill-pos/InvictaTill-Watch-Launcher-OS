package com.healthsync.phone.data

/** Imported records must identify the app that actually exposed the captured sample. */
fun validHealthAppIdentity(packageName: String?, label: String?): Boolean =
    packageName != null && packageName.length in 3..255 &&
        packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) &&
        label != null && label.isNotBlank() && label.length <= 160 &&
        label.none { it.code < 32 || it.code == 127 }

fun healthReadingSourceLabel(source: String?, sourcePackage: String?, sourceLabel: String?): String = when {
    source == "WATCH_SENSOR" -> "Watch sensor"
    source == "WATCH_HEALTH_APP" && validHealthAppIdentity(sourcePackage, sourceLabel) -> "Watch app: ${sourceLabel!!.trim()}"
    else -> "Source unavailable"
}

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

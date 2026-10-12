package com.healthsync.watch.ui

import java.util.Locale

internal object WorkoutMeasurementLabels {
    fun distanceSource(value: String?): String = when (value) {
        "LOCATION" -> "Location distance"
        "STEP_ESTIMATE" -> "Step-based estimate"
        "LOCATION_AND_STEP_ESTIMATE" -> "Location + step estimate"
        "UNAVAILABLE" -> "Distance unavailable"
        "NOT_APPLICABLE" -> "Distance not tracked"
        else -> "Distance source unknown"
    }
    fun distanceValue(meters: Float, source: String?): String {
        if (!meters.isFinite() || meters < 0f || source == "UNAVAILABLE" || source == "NOT_APPLICABLE") return "— km"
        val suffix = if (source == "STEP_ESTIMATE" || source == "LOCATION_AND_STEP_ESTIMATE") " est." else ""
        return String.format(Locale.getDefault(), "%.2f km%s", meters / 1000f, suffix)
    }
}

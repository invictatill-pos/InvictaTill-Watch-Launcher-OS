package com.healthsync.phone.ui.workout

import com.healthsync.phone.data.model.LatLngPoint
import com.healthsync.phone.data.model.WorkoutEntity
import java.time.Instant
import java.util.Locale

/** New sessions report active seconds; older stored sessions use elapsed time. */
fun workoutDurationSeconds(workout: WorkoutEntity): Long =
    workout.durationSeconds.takeIf { it > 0 }?.toLong()
        ?: ((workout.endTime - workout.startTime) / 1000).coerceAtLeast(0)

fun workoutSummary(workout: WorkoutEntity): String {
    val seconds = workoutDurationSeconds(workout)
    val activity = workout.activityType.replace('_', ' ').lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return buildString {
        appendLine("$activity · HealthSync")
        appendLine("Duration: ${seconds / 60}m ${seconds % 60}s")
        if (workout.distanceMeters > 0) appendLine("Distance: ${String.format(Locale.getDefault(), "%.2f", workout.distanceMeters / 1000f)} km")
        appendLine("Calories: ${workout.calories.toInt()} kcal")
        if (workout.steps > 0) appendLine("Steps: ${workout.steps}")
        if (workout.avgHeartRate > 0) appendLine("Average heart rate: ${workout.avgHeartRate} bpm")
        if (workout.swingsCount > 0) appendLine("Swings / repetitions: ${workout.swingsCount}")
    }.trim()
}

fun workoutsCsv(workouts: List<WorkoutEntity>): String = buildString {
    appendLine("activity,start_utc,end_utc,duration_seconds,distance_metres,calories,average_heart_rate,steps,swings")
    workouts.forEach { workout ->
        val activity = "\"${workout.activityType.replace("\"", "\"\"")}\""
        appendLine(listOf(activity, Instant.ofEpochMilli(workout.startTime), Instant.ofEpochMilli(workout.endTime),
            workoutDurationSeconds(workout), workout.distanceMeters,
            workout.calories, workout.avgHeartRate, workout.steps, workout.swingsCount).joinToString(","))
    }
}

fun workoutGpx(workout: WorkoutEntity, route: List<LatLngPoint>): String = buildString {
    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    appendLine("<gpx version=\"1.1\" creator=\"HealthSync\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>HealthSync workout</name><trkseg>")
    route.forEachIndexed { index, point ->
        if (index > 0 && point.segmentStart) appendLine("</trkseg><trkseg>")
        appendLine("<trkpt lat=\"${point.lat}\" lon=\"${point.lng}\"><time>${Instant.ofEpochMilli(point.timestamp.takeIf { it > 0 } ?: workout.startTime)}</time></trkpt>")
    }
    appendLine("</trkseg></trk></gpx>")
}

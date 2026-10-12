package com.healthsync.watch.data

/** Reject corrupt measurements before storing or syncing them as a captured workout. */
internal fun validCapturedWorkout(session: WorkoutSessionPayload): Boolean =
    session.session_id.isNotBlank() && session.session_id.length <= 128 && session.activity_type.isNotBlank() && session.activity_type.length <= 64 &&
        session.start_time > 0L && session.end_time > session.start_time && session.duration_sec in 0..172_800 &&
        session.duration_sec.toLong() * 1000L <= session.end_time - session.start_time &&
        session.distance_m.isFinite() && session.distance_m in 0f..1_000_000f && session.calories.isFinite() && session.calories in 0.0..100_000.0 &&
        session.step_count in 0..1_000_000 && session.swings in 0..1_000_000 && (session.avg_hr == 0 || session.avg_hr in 25..240) &&
        session.route_json.length <= 1_000_000 && session.distance_source in setOf("LOCATION", "STEP_ESTIMATE",
            "LOCATION_AND_STEP_ESTIMATE", "UNAVAILABLE", "NOT_APPLICABLE", "UNKNOWN")

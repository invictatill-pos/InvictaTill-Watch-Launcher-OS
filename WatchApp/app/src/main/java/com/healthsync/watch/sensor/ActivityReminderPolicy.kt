package com.healthsync.watch.sensor

/** Missing monitoring time is unknown activity, not evidence of inactivity. */
internal fun shouldSendActivityReminder(hour: Int, minute: Int, steps: Int, monitoredMs: Long,
                                       sensorAvailable: Boolean, lastReminderHour: Int): Boolean =
    sensorAvailable && hour in 9..20 && minute >= 50 && steps < 100 &&
        monitoredMs in 45 * 60_000L..3_600_000L && lastReminderHour != hour

internal fun observedActivityMonitoringDelta(previousSample: Long, sample: Long): Long =
    if (previousSample >= 0L && sample - previousSample in 1L..5_000L) sample - previousSample else 0L

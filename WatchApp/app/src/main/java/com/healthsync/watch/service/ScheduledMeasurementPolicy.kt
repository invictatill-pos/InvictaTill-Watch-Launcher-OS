package com.healthsync.watch.service

/** Alarm callbacks identify one current due slot; old callbacks cannot restart the optical sensor. */
internal object ScheduledMeasurementPolicy {
    fun matches(intervalInAlarm: Long, revisionInAlarm: Long, dueInAlarm: Long,
                currentInterval: Long, currentRevision: Long, currentDue: Long, now: Long): Boolean =
        currentInterval in MeasurementSchedule.MIN_INTERVAL_MS..MeasurementSchedule.MAX_INTERVAL_MS &&
            intervalInAlarm == currentInterval && currentRevision >= 0L &&
            revisionInAlarm == currentRevision && dueInAlarm > 0L &&
            dueInAlarm == currentDue && now >= dueInAlarm
}

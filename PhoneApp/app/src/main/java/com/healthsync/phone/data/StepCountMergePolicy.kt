package com.healthsync.phone.data

/** A recorded count and the time at which that count was actually observed. */
internal data class StepCountObservation(val steps: Int, val timestamp: Long)

internal object StepCountMergePolicy {
    /**
     * Keep historical maxima across watch resets without making the retained count look
     * newly captured. A delayed higher count keeps its own capture time; an equal count
     * can refresh observation time only when the equal observation is newer.
     */
    fun merge(previous: StepCountObservation?, incoming: StepCountObservation): StepCountObservation =
        when {
            previous == null -> incoming
            incoming.steps > previous.steps -> incoming
            incoming.steps < previous.steps -> previous
            incoming.timestamp > previous.timestamp -> incoming
            else -> previous
        }
}

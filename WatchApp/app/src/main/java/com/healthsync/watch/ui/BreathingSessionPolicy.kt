package com.healthsync.watch.ui

/** Timing guidance only: a breathing exercise never creates a health measurement. */
internal object BreathingSessionPolicy {
    const val DURATION_MS = 60_000L
    private const val PHASE_MS = 4_000L
    private val phases = listOf("Inhale", "Hold", "Exhale", "Hold")
    data class Frame(val phase: Int, val label: String, val phaseSeconds: Int, val sessionSeconds: Int, val radius: Float)

    fun frame(elapsedMs: Long): Frame {
        val elapsed = elapsedMs.coerceIn(0, DURATION_MS)
        val cycle = elapsed % (PHASE_MS * 4)
        val phase = (cycle / PHASE_MS).toInt()
        val fraction = (cycle % PHASE_MS).toFloat() / PHASE_MS
        val radius = when (phase) {
            0 -> .35f + .5f * fraction
            1 -> .85f
            2 -> .85f - .5f * fraction
            else -> .35f
        }
        return Frame(phase, phases[phase], ((PHASE_MS - cycle % PHASE_MS + 999L) / 1000L).toInt(),
            ((DURATION_MS - elapsed + 999L) / 1000L).toInt(), radius)
    }
}

package com.healthsync.watch.ui

/** Scheduling rules shared by the interactive face and the foreground dim clock. */
internal object WatchDisplayPolicy {
    const val IDLE_DELAY_MS = 15_000L
    const val AMBIENT_BRIGHTNESS = 0.03f

    fun shouldDim(enabled: Boolean, resumed: Boolean, focused: Boolean): Boolean =
        enabled && resumed && focused

    /** Align updates with wall-clock boundaries so the minute never stays stale for a full minute. */
    fun nextTickDelay(nowMillis: Long, ambient: Boolean): Long {
        val interval = if (ambient) 60_000L else 1_000L
        val elapsed = ((nowMillis % interval) + interval) % interval
        return interval - elapsed
    }

    /** A bounded nine-position path moves the lit pixels once per minute. Values are in dp. */
    fun burnInOffset(nowMillis: Long): Pair<Int, Int> {
        val minute = nowMillis / 60_000L
        val slot = (((minute % 9L) + 9L) % 9L).toInt()
        return ((slot % 3) - 1) * 3 to ((slot / 3) - 1) * 3
    }
}

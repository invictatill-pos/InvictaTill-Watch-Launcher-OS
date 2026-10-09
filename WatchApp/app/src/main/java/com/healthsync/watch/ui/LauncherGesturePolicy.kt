package com.healthsync.watch.ui

import kotlin.math.abs

/** Decide once per gesture; diagonal drags, long holds and short taps do not navigate. */
internal object LauncherGesturePolicy {
    enum class Direction { UP, DOWN, LEFT, RIGHT }

    fun direction(dx: Float, dy: Float, threshold: Float, elapsedMs: Long): Direction? {
        if (!dx.isFinite() || !dy.isFinite() || !threshold.isFinite() || threshold <= 0f ||
            elapsedMs !in 0L..1_200L) return null
        val x = abs(dx)
        val y = abs(dy)
        return when {
            x >= threshold && x >= y * 1.4f -> if (dx > 0) Direction.RIGHT else Direction.LEFT
            y >= threshold && y >= x * 1.4f -> if (dy > 0) Direction.DOWN else Direction.UP
            else -> null
        }
    }
}

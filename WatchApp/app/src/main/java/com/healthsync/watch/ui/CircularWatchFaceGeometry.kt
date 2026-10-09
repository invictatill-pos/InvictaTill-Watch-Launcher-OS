package com.healthsync.watch.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Circle-safe dimensions shared by the face's text and complication rows. */
internal object CircularWatchFaceGeometry {
    fun radius(width: Float, height: Float): Float =
        min(width.coerceAtLeast(0f), height.coerceAtLeast(0f)) / 2f

    /** The narrowest horizontal chord across a vertical band, allowing an inset at the rim. */
    fun halfWidthForBand(radius: Float, topFromCenter: Float, bottomFromCenter: Float,
                         inset: Float = 0f): Float {
        val safeRadius = max(0f, radius - inset.coerceAtLeast(0f))
        val furthestY = max(abs(topFromCenter), abs(bottomFromCenter))
        return sqrt(max(0f, safeRadius * safeRadius - furthestY * furthestY))
    }
}

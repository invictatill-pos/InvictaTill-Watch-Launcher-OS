package com.healthsync.watch.ui.navigation

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Pixel geometry shared by the actual layout and circle-boundary checks. */
internal data class MapOverlayBounds(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

internal data class PhoneMapLayout(
    val toolbar: MapOverlayBounds,
    val actions: MapOverlayBounds,
    val status: MapOverlayBounds,
    val quietStatus: MapOverlayBounds,
    val message: MapOverlayBounds,
    val help: MapOverlayBounds
)

internal fun phoneMapLayout(width: Int, height: Int, desiredTouchHeight: Int): PhoneMapLayout {
    val side = min(width, height).coerceAtLeast(1)
    val touch = desiredTouchHeight.coerceAtLeast(1).coerceAtMost((side * .18f).roundToInt().coerceAtLeast(1))
    fun centered(w: Float, h: Int, top: Int) = MapOverlayBounds(((width - side * w) / 2).roundToInt(),
        top, (side * w).roundToInt(), h)
    val circleTop = (height - side) / 2
    val toolbar = centered(.60f, touch, circleTop + (side * .12f).roundToInt())
    val actions = centered(.64f, touch, circleTop + (side * .84f).roundToInt() - touch)
    val statusHeight = (side * .08f).roundToInt().coerceAtLeast(1)
    return PhoneMapLayout(toolbar, actions,
        centered(.52f, statusHeight, toolbar.bottom + (side * .015f).roundToInt()),
        centered(.42f, statusHeight, circleTop + (side * .85f).roundToInt() - statusHeight),
        centered(.72f, (side * .48f).roundToInt(), circleTop + (side * .26f).roundToInt()),
        centered(.72f, (side * .64f).roundToInt(), circleTop + (side * .18f).roundToInt()))
}

/** Full-frame Fit keeps all four phone corners inside a round display, including the turn banner. */
internal fun circlePhoneFrameScale(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): Float {
    if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return 0f
    return (min(viewWidth, viewHeight) / hypot(imageWidth.toDouble(), imageHeight.toDouble())).toFloat()
}

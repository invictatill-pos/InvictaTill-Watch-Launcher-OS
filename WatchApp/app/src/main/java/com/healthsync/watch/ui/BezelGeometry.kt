package com.healthsync.watch.ui

/** Geometry shared by the overlay and tests; the aperture always stays inside the display. */
internal object BezelGeometry {
    fun radius(width: Int, height: Int, diameterPercent: Int): Float =
        minOf(width.coerceAtLeast(0), height.coerceAtLeast(0)) * diameterPercent.coerceIn(85, 100) / 200f

    // Android 12+ blocks touches through opaque untrusted overlay windows, even in a
    // transparent cutout. Cap the WINDOW alpha, rather than just the painted pixels.
    fun windowAlpha(api: Int, maximumObscuringOpacity: Float): Float =
        if (api < 31) 1f else if (maximumObscuringOpacity.isFinite())
            maximumObscuringOpacity.coerceIn(0f, 0.8f) else 0.8f
}

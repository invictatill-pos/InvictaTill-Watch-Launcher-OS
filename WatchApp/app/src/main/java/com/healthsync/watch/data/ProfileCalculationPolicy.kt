package com.healthsync.watch.data

/** Legacy profile keys can contain phone defaults, so only explicit entries personalize estimates. */
internal object ProfileCalculationPolicy {
    const val DEFAULT_WEIGHT_KG = 70.0
    const val DEFAULT_HEIGHT_CM = 170

    fun weightKg(configured: Boolean, saved: Double): Double =
        if (configured && saved.isFinite() && saved in 30.0..250.0) saved else DEFAULT_WEIGHT_KG

    fun heightCm(configured: Boolean, saved: Int): Int =
        if (configured && saved in 100..230) saved else DEFAULT_HEIGHT_CM
}

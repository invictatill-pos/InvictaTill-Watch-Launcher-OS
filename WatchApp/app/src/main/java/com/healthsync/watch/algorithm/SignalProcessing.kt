package com.healthsync.watch.algorithm

import kotlin.math.sqrt

/**
 * Signal Processing algorithms for smartwatch sensor filtering and step detection.
 */
object SignalProcessing {

    /**
     * 2nd-Order Low-Pass Butterworth Filter (Cutoff ~2.5 Hz at 50 Hz sample rate)
     * Transfer function coefficients calculated for cutoff = 2.5Hz, Fs = 50Hz.
     */
    class ButterworthLowPass2_5Hz {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        // Coefficients for 2.5Hz @ 50Hz sampling
        private val b0 = 0.02008337
        private val b1 = 0.04016674
        private val b2 = 0.02008337
        private val a1 = -1.56101808
        private val a2 = 0.64135157

        fun filter(input: Double): Double {
            if (!input.isFinite()) return y1
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
        }
    }

    /**
     * 1Hz - 3Hz Bandpass Filter for human movement frequency step detection
     */
    class MovementBandpassFilter {
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        private val b0 = 0.117350
        private val b1 = 0.000000
        private val b2 = -0.117350
        private val a1 = -1.705298
        private val a2 = 0.765299

        fun filter(input: Double): Double {
            if (!input.isFinite()) return y1
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = input
            y2 = y1; y1 = output
            return output
        }

        fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }
    }

    /**
     * Continuous 24/7 Step Tracker with Anti-False Positive Buffer + adaptive threshold.
     * Rule: Must detect at least 6 consecutive step peaks within valid interval (220ms <= dt <= 1400ms)
     * before committing steps to database to filter out isolated wrist flicks.
     * The threshold adapts to the user's current activity level for higher accuracy.
     */
    class AntiFalsePositiveStepTracker(private val onStepsCommitted: (Int) -> Unit) {
        private val bandpass = MovementBandpassFilter()
        private var lastPeakTime = 0L
        private var consecutiveBufferCount = 0
        private var stepThreshold = 1.6 // adaptive threshold start
        private var magSum = 0.0
        private var magCount = 0
        private var lastPeakMag = 0.0
        private var lastSampleMs = -1L

        fun reset() {
            bandpass.reset()
            lastPeakTime = 0L
            consecutiveBufferCount = 0
            stepThreshold = 1.6
            magSum = 0.0
            magCount = 0
            lastPeakMag = 0.0
            lastSampleMs = -1L
        }

        fun processAccel(ax: Float, ay: Float, az: Float, nowMs: Long) {
            if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return
            if (lastSampleMs >= 0 && nowMs <= lastSampleMs) return
            // Filters are tuned for 50Hz. Decimate faster streams and reset after gaps.
            if (lastSampleMs >= 0 && nowMs - lastSampleMs < 16) return
            if (lastSampleMs >= 0 && nowMs - lastSampleMs > 1500) reset()
            lastSampleMs = nowMs
            val mag = sqrt((ax * ax + ay * ay + az * az).toDouble())
            val filtered = bandpass.filter(mag)

            // Adaptive threshold: 50% of the rolling peak magnitude, clamped
            magSum += kotlin.math.abs(filtered)
            magCount++
            if (magCount >= 100) {
                val avg = magSum / magCount
                stepThreshold = (avg * 1.2 + 0.6).coerceIn(1.2, 3.0)
                magSum = 0.0
                magCount = 0
            }

            // Peak detection with hysteresis: rising edge above threshold
            if (filtered > stepThreshold && lastPeakMag <= stepThreshold) {
                val dt = nowMs - lastPeakTime
                when {
                    lastPeakTime == 0L -> {
                        consecutiveBufferCount = 1
                        lastPeakTime = nowMs
                    }
                    dt in 220..1400 -> {
                        consecutiveBufferCount++
                        lastPeakTime = nowMs
                        if (consecutiveBufferCount == 6) {
                            onStepsCommitted(6)
                        } else if (consecutiveBufferCount > 6) {
                            onStepsCommitted(1)
                        }
                    }
                    dt > 1400 -> {
                        consecutiveBufferCount = 1
                        lastPeakTime = nowMs
                    }
                }
            }
            lastPeakMag = filtered
        }
    }
}

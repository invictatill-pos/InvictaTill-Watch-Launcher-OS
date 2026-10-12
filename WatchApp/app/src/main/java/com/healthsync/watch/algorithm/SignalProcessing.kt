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
     * Continuous 24/7 Step Tracker with Anti-False Positive Buffer + dynamic gravity compensation.
     * Rule: Must detect at least 4 consecutive step peaks within valid interval (220ms <= dt <= 1800ms)
     * before committing steps to database to filter out isolated wrist flicks.
     * The threshold adapts to the user's current activity level for higher accuracy.
     */
    class AntiFalsePositiveStepTracker(private val onStepsCommitted: (Int) -> Unit) {
        private val bandpass = MovementBandpassFilter()
        private var lastPeakTime = 0L
        private var consecutiveBufferCount = 0
        private var stepThreshold = 1.4
        private var magSum = 0.0
        private var magCount = 0
        private var lastPeakMag = 0.0
        private var lastSampleMs = -1L

        private var gravity = 9.81
        private var smoothLinearAcc = 0.0

        fun reset() {
            bandpass.reset()
            lastPeakTime = 0L
            consecutiveBufferCount = 0
            stepThreshold = 1.4
            magSum = 0.0
            magCount = 0
            lastPeakMag = 0.0
            lastSampleMs = -1L
            gravity = 9.81
            smoothLinearAcc = 0.0
        }

        fun processAccel(ax: Float, ay: Float, az: Float, nowMs: Long) {
            if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return
            if (lastSampleMs >= 0 && nowMs <= lastSampleMs) return
            // Decimate faster streams (dt < 10ms)
            if (lastSampleMs >= 0 && nowMs - lastSampleMs < 10) return
            // If user paused for > 2500ms, reset the debounce cadence buffer but preserve committed steps
            if (lastSampleMs >= 0 && nowMs - lastSampleMs > 2500) {
                consecutiveBufferCount = 0
                lastPeakTime = 0L
            }
            lastSampleMs = nowMs

            val mag = sqrt((ax.toDouble() * ax + ay.toDouble() * ay + az.toDouble() * az))
            if (!mag.isFinite()) return

            // Dynamic gravity isolation (sample-rate independent signed linear acceleration)
            gravity = 0.88 * gravity + 0.12 * mag
            val linearAcc = mag - gravity
            smoothLinearAcc = 0.65 * smoothLinearAcc + 0.35 * linearAcc

            // Discrete bandpass filter
            val bp = bandpass.filter(mag)

            // Dynamic fused signal: maximum of smoothed linear acceleration and bandpass output
            val signal = maxOf(smoothLinearAcc, bp)

            // Adaptive threshold
            magSum += kotlin.math.abs(signal)
            magCount++
            if (magCount >= 60) {
                val avg = magSum / magCount
                stepThreshold = (avg * 1.1 + 0.6).coerceIn(1.2, 3.2)
                magSum = 0.0
                magCount = 0
            }

            // Peak detection with hysteresis: rising edge above threshold
            if (signal > stepThreshold && lastPeakMag <= stepThreshold) {
                val dt = nowMs - lastPeakTime
                when {
                    lastPeakTime == 0L -> {
                        consecutiveBufferCount = 1
                        lastPeakTime = nowMs
                    }
                    dt in 220..1800 -> {
                        consecutiveBufferCount++
                        lastPeakTime = nowMs
                        if (consecutiveBufferCount == 4) {
                            onStepsCommitted(4)
                        } else if (consecutiveBufferCount > 4) {
                            onStepsCommitted(1)
                        }
                    }
                    dt > 1800 -> {
                        consecutiveBufferCount = 1
                        lastPeakTime = nowMs
                    }
                }
            }
            lastPeakMag = signal
        }
    }
}

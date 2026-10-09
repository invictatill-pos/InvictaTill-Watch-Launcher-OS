package com.healthsync.watch.algorithm

import kotlin.math.*

object SportAlgorithms {

    /** Unit-range checks happen against Sensor.maximumRange in the service; guard filter arithmetic here too. */
    private fun validMotionVector(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float): Boolean {
        if (!listOf(ax, ay, az, gx, gy, gz).all { it.isFinite() }) return false
        val accelSquared = ax.toDouble() * ax + ay.toDouble() * ay + az.toDouble() * az
        val gyroSquared = gx.toDouble() * gx + gy.toDouble() * gy + gz.toDouble() * gz
        return accelSquared <= Float.MAX_VALUE && gyroSquared <= Float.MAX_VALUE
    }

    // Default user physiological benchmarks (configurable via settings)
    var userHeightMeters: Double = 1.75
    var userWeightKg: Double = 70.0
    var userRestingHr: Int = 70

    // ── 1. Walk Calculations ──────────────────────────────────────────────────

    fun calculateWalkStrideLength(heightM: Double = userHeightMeters): Double {
        return validHeight(heightM) * 0.415
    }

    fun calculateWalkDistanceMeters(steps: Int, heightM: Double = userHeightMeters): Double {
        return steps.coerceAtLeast(0) * calculateWalkStrideLength(heightM)
    }

    /**
     * Walk Calorie Model (3.5 METs)
     * Calories / Min = (3.5 * 3.5 * weight / 200) * (currentHR / restingHR)
     */
    fun calculateWalkCaloriesPerMin(currentHr: Int, weightKg: Double = userWeightKg, restingHr: Int = userRestingHr): Double {
        val hrRatio = if (restingHr > 0 && currentHr > 0) (currentHr.toDouble() / restingHr.toDouble()) else 1.0
        val basePerMin = (3.5 * 3.5 * validWeight(weightKg)) / 200.0
        return basePerMin * hrRatio.coerceIn(0.5, 2.5)
    }

    // ── 2. Run Calculations ───────────────────────────────────────────────────

    /**
     * Haversine formula for exact distance between two GPS coordinates
     */
    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        if (!listOf(lat1, lon1, lat2, lon2).all { it.isFinite() } ||
            lat1 !in -90.0..90.0 || lat2 !in -90.0..90.0 || lon1 !in -180.0..180.0 || lon2 !in -180.0..180.0) return 0.0
        val a = (sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)).coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun calculateRunFallbackStrideLength(heightM: Double = userHeightMeters): Double {
        return validHeight(heightM) * 0.53
    }

    fun calculateRunFallbackDistance(steps: Int, heightM: Double = userHeightMeters): Double {
        return steps.coerceAtLeast(0) * calculateRunFallbackStrideLength(heightM)
    }

    /**
     * Current Pace in min/km
     */
    fun calculatePaceMinPerKm(distanceMeters: Double, durationSeconds: Long): Double {
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0 || durationSeconds <= 0) return 0.0
        val km = distanceMeters / 1000.0
        val minutes = durationSeconds / 60.0
        return minutes / km
    }

    /**
     * Current Speed in km/h (for Cycling etc.)
     */
    fun calculateSpeedKmh(distanceMeters: Double, durationSeconds: Long): Double {
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0 || durationSeconds <= 0) return 0.0
        val km = distanceMeters / 1000.0
        val hours = durationSeconds / 3600.0
        return km / hours
    }

    /**
     * Cadence Tracker (Steps Per Minute over a rolling 30s window)
     */
    class CadenceTracker {
        private val samples = java.util.ArrayDeque<Pair<Long, Int>>()
        private var firstSampleMs = -1L

        fun recordStep(nowMs: Long): Int {
            return recordSteps(nowMs, 1)
        }

        fun recordSteps(nowMs: Long, count: Int): Int {
            if (firstSampleMs < 0) firstSampleMs = nowMs
            if (count > 0) samples.addLast(nowMs to count.coerceAtMost(100))
            return currentCadence(nowMs)
        }

        fun currentCadence(nowMs: Long): Int {
            while ((samples.peekFirst()?.first ?: Long.MAX_VALUE) < nowMs - 30_000L) samples.removeFirst()
            if (samples.isEmpty() || firstSampleMs < 0) return 0
            val windowMs = (nowMs - firstSampleMs + 1000L).coerceIn(1000L, 30_000L)
            return (samples.sumOf { it.second } * 60_000L / windowMs).toInt().coerceIn(0, 300)
        }

        fun reset() { samples.clear(); firstSampleMs = -1L }
    }

    // ── 3. Home Workout Repetition Engine ─────────────────────────────────────

    class HomeWorkoutDetector(
        private val onRepDetected: (Int) -> Unit
    ) {
        private val butterworth = SignalProcessing.ButterworthLowPass2_5Hz()
        private var lastPeakTime = 0L
        private var lastGyroMag = 0.0
        private var totalReps = 0
        private var peakArmed = false
        private var lastSampleTime = -1L
        private val ACCEL_PEAK_THRESHOLD = 11.5 // m/s^2

        fun processSensor(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, nowMs: Long) {
            if (nowMs < 0 || nowMs <= lastSampleTime) return
            if (!validMotionVector(ax, ay, az, gx, gy, gz)) { butterworth.reset(); peakArmed = false; return }
            lastSampleTime = nowMs
            val rawMag = sqrt(ax.toDouble() * ax + ay.toDouble() * ay + az.toDouble() * az)
            val filteredAccel = butterworth.filter(rawMag)
            val gyroMag = sqrt(gx.toDouble() * gx + gy.toDouble() * gy + gz.toDouble() * gz)

            // Count a full rise-and-return cycle with angular movement confirmation.
            lastGyroMag = gyroMag
            if (filteredAccel > ACCEL_PEAK_THRESHOLD && gyroMag > 0.25) peakArmed = true
            if (peakArmed && filteredAccel < 10.2 && nowMs - lastPeakTime >= 800) {
                peakArmed = false
                lastPeakTime = nowMs
                totalReps++
                onRepDetected(totalReps)
            }
        }

        fun reset(count: Int = 0) {
            butterworth.reset(); lastPeakTime = 0L; lastGyroMag = 0.0
            totalReps = count.coerceAtLeast(0); peakArmed = false
            lastSampleTime = -1L
        }
    }

    /**
     * Home Workout Calorie Model (5.0 METs)
     */
    fun calculateHomeWorkoutCaloriesPerMin(currentHr: Int, weightKg: Double = userWeightKg, restingHr: Int = userRestingHr): Double {
        val hrRatio = if (restingHr > 0 && currentHr > 0) (currentHr.toDouble() / restingHr.toDouble()) else 1.0
        val basePerMin = (5.0 * 3.5 * validWeight(weightKg)) / 200.0
        return basePerMin * hrRatio.coerceIn(0.5, 2.5)
    }

    // ── 4. Badminton Swing Engine ──────────────────────────────────────────────

    class BadmintonDetector(
        private val onSwingDetected: (Int) -> Unit
    ) {
        private var totalSwings = 0
        private var lastSwingTime = 0L
        private var maxOmegaWindow = 0.0
        private var maxAccelWindow = 0.0
        private var windowStartMs = 0L
        private var lastSampleTime = -1L

        fun processSensor(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, nowMs: Long) {
            if (nowMs < 0 || nowMs <= lastSampleTime) return
            if (!validMotionVector(ax, ay, az, gx, gy, gz)) {
                maxOmegaWindow = 0.0; maxAccelWindow = 0.0; windowStartMs = nowMs
                return
            }
            lastSampleTime = nowMs
            val accelG = sqrt(ax.toDouble() * ax + ay.toDouble() * ay + az.toDouble() * az) / 9.81
            val omegaDegSec = Math.toDegrees(sqrt(gx.toDouble() * gx + gy.toDouble() * gy + gz.toDouble() * gz))

            if (nowMs - windowStartMs > 150) {
                // Evaluate 150ms window
                if (maxOmegaWindow > 350.0 && maxAccelWindow > 2.5 && (nowMs - lastSwingTime >= 400)) {
                    lastSwingTime = nowMs
                    totalSwings++
                    onSwingDetected(totalSwings)
                }
                // Reset window
                windowStartMs = nowMs
                maxOmegaWindow = 0.0
                maxAccelWindow = 0.0
            }

            maxOmegaWindow = max(maxOmegaWindow, omegaDegSec)
            maxAccelWindow = max(maxAccelWindow, accelG)
        }

        fun reset(count: Int = 0) {
            totalSwings = count.coerceAtLeast(0); lastSwingTime = 0L
            maxOmegaWindow = 0.0; maxAccelWindow = 0.0; windowStartMs = 0L
            lastSampleTime = -1L
        }
    }

    /**
     * Badminton Calorie Model (7.0 METs)
     */
    fun calculateBadmintonCaloriesPerMin(currentHr: Int, weightKg: Double = userWeightKg, restingHr: Int = userRestingHr): Double {
        val hrRatio = if (restingHr > 0 && currentHr > 0) (currentHr.toDouble() / restingHr.toDouble()) else 1.0
        val basePerMin = (7.0 * 3.5 * validWeight(weightKg)) / 200.0
        return basePerMin * hrRatio.coerceIn(0.5, 2.5)
    }

    /** MET estimates remain available when a real HR sample is unavailable. */
    fun estimateCalories(activity: String, durationMs: Long, weightKg: Double = userWeightKg): Double {
        val met = when (activity) {
            "Walk" -> 3.5; "Run" -> 8.0; "Cycling" -> 6.8; "Basketball" -> 8.0
            "Cricket" -> 5.0; "Yoga" -> 2.5; "Home Workout" -> 5.0; "Badminton" -> 7.0
            else -> 3.5
        }
        return durationMs.coerceAtLeast(0) / 60_000.0 * met * 3.5 * validWeight(weightKg) / 200.0
    }

    private fun validHeight(value: Double): Double = if (value.isFinite() && value in 0.5..2.8) value else 1.75
    private fun validWeight(value: Double): Double = if (value.isFinite() && value in 15.0..400.0) value else 70.0
}

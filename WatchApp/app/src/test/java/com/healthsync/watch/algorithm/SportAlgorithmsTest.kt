package com.healthsync.watch.algorithm

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class SportAlgorithmsTest {
    @Test fun distanceAndPaceRejectInvalidInputs() {
        assertEquals(0.0, SportAlgorithms.calculateWalkDistanceMeters(-5), 0.0)
        assertEquals(0.0, SportAlgorithms.calculatePaceMinPerKm(Double.NaN, 60), 0.0)
        assertEquals(0.0, SportAlgorithms.calculateSpeedKmh(1000.0, 0), 0.0)
        assertEquals(5.0, SportAlgorithms.calculatePaceMinPerKm(1000.0, 300), 0.0001)
        assertEquals(12.0, SportAlgorithms.calculateSpeedKmh(1000.0, 300), 0.0001)
    }

    @Test fun haversineIsFiniteAtAntipodesAndRejectsBadCoordinates() {
        assertEquals(0.0, SportAlgorithms.haversineDistanceMeters(10.0, 20.0, 10.0, 20.0), 0.001)
        val antipodes = SportAlgorithms.haversineDistanceMeters(0.0, 0.0, 0.0, 180.0)
        assertTrue(antipodes.isFinite() && antipodes in 20_000_000.0..20_020_000.0)
        assertEquals(0.0, SportAlgorithms.haversineDistanceMeters(91.0, 0.0, 0.0, 0.0), 0.0)
    }

    @Test fun cadenceUsesActualStepDeltasAndExpiresWhenStationary() {
        val tracker = SportAlgorithms.CadenceTracker()
        for (second in 0..29) tracker.recordSteps(second * 1000L, 3)
        assertEquals(180, tracker.currentCadence(29_000L))
        assertEquals(0, tracker.currentCadence(61_000L))
        tracker.reset()
        assertEquals(0, tracker.currentCadence(62_000L))
    }

    @Test fun estimatesUseActiveDurationAndStayFiniteWithoutHeartRate() {
        assertEquals(0.0, SportAlgorithms.estimateCalories("Run", 0), 0.0)
        val oneMinute = SportAlgorithms.estimateCalories("Run", 60_000L)
        assertEquals(oneMinute * 2, SportAlgorithms.estimateCalories("Run", 120_000L), 0.00001)
        assertTrue(SportAlgorithms.estimateCalories("Walk", 60_000L, Double.NaN).isFinite())
        assertTrue(SportAlgorithms.calculateWalkCaloriesPerMin(0, 70.0, 0).isFinite())
    }

    @Test fun stationaryAccelerationAndIsolatedMovementDoNotCreateSteps() {
        var steps = 0
        val tracker = SignalProcessing.AntiFalsePositiveStepTracker { steps += it }
        for (i in 0..249) tracker.processAccel(0f, 0f, 9.81f, 1000L + i * 20L)
        for (i in 0..24) {
            val z = (9.81 + 6 * sin(i * 0.02 * 2 * Math.PI * 1.8)).toFloat()
            tracker.processAccel(0f, 0f, z, 6000L + i * 20L)
        }
        for (i in 0..249) tracker.processAccel(0f, 0f, 9.81f, 6500L + i * 20L)
        assertEquals(0, steps)
    }

    @Test fun sustainedWalkingCommitsBufferedStepsAndNanDoesNotPoisonFilter() {
        var steps = 0
        val tracker = SignalProcessing.AntiFalsePositiveStepTracker { steps += it }
        for (i in 0..999) {
            if (i == 300) tracker.processAccel(Float.NaN, 0f, 0f, 1000L + i * 20L)
            val z = (9.81 + 6 * sin(i * 0.02 * 2 * Math.PI * 1.8)).toFloat()
            tracker.processAccel(0f, 0f, z, 1000L + i * 20L)
        }
        assertTrue("Sustained gait should produce steps", steps >= 20)
        assertTrue("50Hz sensor samples must not each count as a step", steps <= 40)
    }

    @Test fun swingCountersResetForANewWorkout() {
        val counts = mutableListOf<Int>()
        val detector = SportAlgorithms.BadmintonDetector { counts.add(it) }
        for (i in 0..10) detector.processSensor(0f, 0f, 30f, 8f, 0f, 0f, 1000L + i * 20L)
        assertEquals(listOf(1), counts)
        detector.reset()
        for (i in 0..10) detector.processSensor(0f, 0f, 30f, 8f, 0f, 0f, 2000L + i * 20L)
        assertEquals(listOf(1, 1), counts)
    }

    @Test fun overflowingFiniteVectorsDoNotCountSwingsOrPoisonRepetitionRecovery() {
        val swings = mutableListOf<Int>()
        val swing = SportAlgorithms.BadmintonDetector { swings.add(it) }
        val reps = mutableListOf<Int>()
        val rep = SportAlgorithms.HomeWorkoutDetector { reps.add(it) }
        for (i in 0..50) {
            swing.processSensor(Float.MAX_VALUE, 0f, 0f, Float.MAX_VALUE, 0f, 0f, 1_000 + i * 20L)
            rep.processSensor(Float.MAX_VALUE, 0f, 0f, Float.MAX_VALUE, 0f, 0f, 1_000 + i * 20L)
        }
        assertTrue(swings.isEmpty())
        assertTrue(reps.isEmpty())
        for (i in 0..10) swing.processSensor(0f, 0f, 30f, 8f, 0f, 0f, 3_000 + i * 20L)
        assertEquals(listOf(1), swings)
        for (i in 0..599) {
            val z = (9.81 + 7 * sin(i * .02 * 2 * Math.PI)).toFloat()
            rep.processSensor(0f, 0f, z, 1f, 0f, 0f, 3_000 + i * 20L)
        }
        assertTrue("Valid rise/return cycles must recover after invalid input", reps.isNotEmpty())
        assertTrue(reps.last() in 1..15)
    }

    @Test fun reorderedMotionSamplesCannotFillTheSwingWindow() {
        val counts = mutableListOf<Int>()
        val detector = SportAlgorithms.BadmintonDetector { counts.add(it) }
        detector.processSensor(0f, 0f, 9.81f, 0f, 0f, 0f, 2_000)
        detector.processSensor(0f, 0f, 30f, 8f, 0f, 0f, 1_990)
        detector.processSensor(0f, 0f, 30f, 8f, 0f, 0f, 2_000)
        detector.processSensor(0f, 0f, 9.81f, 0f, 0f, 0f, 2_200)
        assertTrue(counts.isEmpty())
    }
}

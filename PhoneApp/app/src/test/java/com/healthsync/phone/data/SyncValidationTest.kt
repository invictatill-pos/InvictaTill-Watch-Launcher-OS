package com.healthsync.phone.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.healthsync.phone.data.model.LatLngPoint
import com.healthsync.phone.data.model.MessageType
import com.healthsync.phone.data.model.WorkoutSessionPayload
import org.junit.Assert.*
import org.junit.Test

class SyncValidationTest {
    @Test fun rejectsCorruptAndFutureTimestamps() {
        assertFalse(SyncValidation.validTimestamp(0, 1_000_000))
        assertFalse(SyncValidation.validTimestamp(1_300_001, 1_000_000))
        assertTrue(SyncValidation.validTimestamp(900_000, 1_000_000))
    }
    @Test fun rejectsNonFiniteAndNegativeHealthMetrics() {
        assertFalse(SyncValidation.validSteps(100, Double.NaN))
        assertFalse(SyncValidation.validSteps(-1, 0.0))
        assertFalse(SyncValidation.validSteps(100, Double.POSITIVE_INFINITY))
        assertTrue(SyncValidation.validSteps(1234, 48.5))
    }
    @Test fun acceptsUnavailableHeartRateButRejectsReversedWorkout() {
        assertTrue(SyncValidation.validWorkout(100, 200, 0f, 0.0, 0, 0, 0, 300))
        assertTrue(SyncValidation.validWorkout(100, 200, 0f, 0.0, 0, 25, 0, 300))
        assertFalse(SyncValidation.validWorkout(200, 100, 0f, 0.0, 0, 70, 0, 300))
        assertFalse(SyncValidation.validWorkout(100, 200, Float.NaN, 0.0, 0, 70, 0, 300))
        assertFalse(SyncValidation.validWorkout(100, 200, 0f, 0.0, 0, 999, 0, 300))
    }

    @Test fun rejectsUnreliableAndNoContactHeartRateBeforeReplayDedup() {
        for (accuracy in listOf(-1, 0, 4)) assertFalse(SyncValidation.validHeartRate(72, accuracy, 900_000, 1_000_000))
        for (accuracy in 1..3) assertTrue(SyncValidation.validHeartRate(72, accuracy, 900_000, 1_000_000))
        assertFalse(SyncValidation.validHeartRate(0, 3, 900_000, 1_000_000))
        assertFalse(SyncValidation.validHeartRate(241, 3, 900_000, 1_000_000))
        assertFalse(SyncValidation.validHeartRate(72, 3, 1_300_001, 1_000_000))
    }

    @Test fun rejectsInvalidOxygenWithoutInventingAReading() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, 49.9f, 100.1f)) {
            assertFalse(SyncValidation.validSpO2(value, 900_000, 1_000_000))
        }
        assertTrue(SyncValidation.validSpO2(98f, 900_000, 1_000_000))
        assertFalse(SyncValidation.validSpO2(98f, 0, 1_000_000))
    }

    @Test fun rejectsNegativeOrImpossibleActiveDurationInsteadOfClampingIt() {
        fun valid(activity: String?, seconds: Int) = SyncValidation.validWorkoutRecord(
            activity, 600_000, 900_000, seconds, 0f, 0.0, 0, 0, 0, 1_000_000)
        assertFalse(valid("WALK", -1))
        assertFalse(valid("WALK", 301))
        assertFalse(valid(null, 120))
        assertFalse(valid(" ", 120))
        assertTrue(valid("WALK", 120)) // Pauses can make active time shorter than elapsed time.
        assertFalse(SyncValidation.validWorkout(600_000, 600_000, 0f, 0.0, 0, 0, 0, 1_000_000))
    }

    @Test fun rejectsSleepWithNoStagesNegativeStagesOrMoreStagesThanElapsed() {
        assertFalse(SyncValidation.validSleep(100_000, 700_000, 0, 0, 0, 0, 1_000_000))
        assertFalse(SyncValidation.validSleep(100_000, 700_000, 5, -1, 3, 0, 1_000_000))
        assertFalse(SyncValidation.validSleep(100_000, 700_000, 20, 0, 0, 0, 1_000_000))
        assertTrue(SyncValidation.validSleep(100_000, 700_000, 5, 3, 2, 0, 1_000_000))
    }

    @Test fun rawHeartRateRejectsMissingNullStringAndFractionalRequiredValues() {
        val invalid = listOf("{}", "null", "[]", """{"bpm":72}""",
            """{"bpm":72,"accuracy":null}""", """{"bpm":"72","accuracy":3}""",
            """{"bpm":72.5,"accuracy":3}""", """{"bpm":72,"accuracy":0}""")
        for (json in invalid) assertFalse(json, SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE, json, 900_000, 1_000_000))
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
            """{"bpm":72,"accuracy":3}""", 900_000, 1_000_000))
    }

    @Test fun rawMetricsCannotUseGsonDefaultZerosForMissingFields() {
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.STEPS, """{"steps":20}""", 900_000, 1_000_000))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.SPO2, """{"percentage":null}""", 900_000, 1_000_000))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.SLEEP,
            """{"startTime":100000,"endTime":700000,"deepMinutes":10}""", 900_000, 1_000_000))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.FALL_DETECTED, "{}", 900_000, 1_000_000))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.FALL_DETECTED, """{"severity":"unknown"}""", 900_000, 1_000_000))
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.FALL_DETECTED, """{"severity":"HIGH"}""", 900_000, 1_000_000))
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.STEPS, """{"steps":0,"calories":0}""", 900_000, 1_000_000))
    }

    private fun sessionJson() = JsonParser.parseString(Gson().toJson(WorkoutSessionPayload(
        "session-A", "WALK", 600_000, 900_000, 120, 200f, 15.0, 250, 0, 72, "[]"))).asJsonObject

    @Test fun rawWorkoutSessionRequiresActualMetricsAndActiveDurationForAcknowledgement() {
        val valid = sessionJson()
        assertTrue(SyncValidation.validIncomingWorkoutSession(valid.toString(), 1_000_000))
        for (key in listOf("distance_m", "step_count", "avg_hr", "calories", "duration_sec", "activity_type", "session_id")) {
            val missing = valid.deepCopy().apply { remove(key) }
            assertFalse(key, SyncValidation.validIncomingWorkoutSession(missing.toString(), 1_000_000))
        }
        assertFalse(SyncValidation.validIncomingWorkoutSession(valid.deepCopy().apply { addProperty("duration_sec", -1) }.toString(), 1_000_000))
        assertFalse(SyncValidation.validIncomingWorkoutSession(valid.deepCopy().apply { addProperty("route_json", "not-json") }.toString(), 1_000_000))
    }

    @Test fun batchStructureKeepsValidRowsIndependentAndRejectsNullLists() {
        val good = sessionJson().toString()
        val mixed = """{"workouts":[$good,null,{"session_id":"bad"}],"daily_steps":[]}"""
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.BATCHED_SYNC, mixed, 900_000, 1_000_000))
        assertTrue(SyncValidation.validIncomingWorkoutSession(good, 1_000_000))
        assertFalse(SyncValidation.validIncomingWorkoutSession("null", 1_000_000))
        assertFalse(SyncValidation.validIncomingWorkoutSession("""{"session_id":"bad"}""", 1_000_000))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.BATCHED_SYNC,
            """{"workouts":null,"daily_steps":[]}""", 900_000, 1_000_000))
    }

    @Test fun stepBucketRequiresAnIntegralIdentityAndEveryMetric() {
        assertTrue(SyncValidation.validIncomingStepBucket("""{"timestamp":900000,"steps":20,"calories":0.8}""", 1_000_000))
        assertFalse(SyncValidation.validIncomingStepBucket("""{"timestamp":900000,"steps":20}""", 1_000_000))
        assertFalse(SyncValidation.validIncomingStepBucket("""{"timestamp":900000.5,"steps":20,"calories":0.8}""", 1_000_000))
        assertFalse(SyncValidation.validIncomingStepBucket("""{"timestamp":900000,"steps":-20,"calories":0.8}""", 1_000_000))
    }

    @Test fun droppedInvalidFixCarriesItsSegmentBoundaryToTheNextValidPoint() {
        val points = listOf(LatLngPoint(22.0, 88.0, 600_000), LatLngPoint(22.001, 88.001, 605_000),
            LatLngPoint(91.0, 88.002, 610_000, true), LatLngPoint(22.003, 88.003, 615_000))
        val route = SyncValidation.sanitizeRoute(points, 600_000, 900_000)
        assertEquals(3, route.size)
        assertFalse(route[1].segmentStart)
        assertTrue(route[2].segmentStart)
    }

    @Test fun nullReplayedAndOutOfSessionFixesCannotJoinRouteSegments() {
        val points = listOf(LatLngPoint(22.0, 88.0, 600_000), null,
            LatLngPoint(22.001, 88.001, 599_000), LatLngPoint(22.002, 88.002, 605_000),
            LatLngPoint(22.003, 88.003, 950_000), LatLngPoint(22.004, 88.004, 650_000))
        val route = SyncValidation.sanitizeRoute(points, 600_000, 900_000)
        assertEquals(listOf(600_000L, 605_000L, 650_000L), route.map { it.timestamp })
        assertTrue(route.all { it.segmentStart })
    }

    @Test fun rawRouteNeverInventsMissingCoordinatesAndRetainsFollowingGap() {
        val json = """[{"lat":22,"lng":88,"timestamp":600000},{"lng":88.1,"timestamp":605000},{"lat":22.2,"lng":88.2,"timestamp":610000}]"""
        val route = JsonParser.parseString(SyncValidation.sanitizeRouteJson(json, 600_000, 900_000)).asJsonArray
        assertEquals(2, route.size())
        assertEquals(22.2, route[1].asJsonObject.get("lat").asDouble, 0.0)
        assertTrue(route[1].asJsonObject.get("segmentStart").asBoolean)
        assertNull(SyncValidation.sanitizeRouteJson("null", 600_000, 900_000))
        assertNull(SyncValidation.sanitizeRouteJson("{broken", 600_000, 900_000))
    }
}

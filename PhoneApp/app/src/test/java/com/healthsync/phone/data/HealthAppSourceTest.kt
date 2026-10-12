package com.healthsync.phone.data

import com.healthsync.phone.data.model.HeartRateStatusPayload
import com.healthsync.phone.data.model.MessageType
import org.junit.Assert.*
import org.junit.Test

class HealthAppSourceTest {
    private val capturedAt = 900_000L
    private val now = 1_000_000L
    private val appIdentity = """"source":"WATCH_HEALTH_APP","sourcePackage":"vendor.health","sourceLabel":"Watch Health""""

    @Test fun appHeartRateRetainsUnknownAccuracyWhileUnreliableSensorIsStillRejected() {
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
            """{"bpm":72,"accuracy":0,$appIdentity}""", capturedAt, now))
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
            """{"bpm":72,"accuracy":3,$appIdentity}""", capturedAt, now))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
            """{"bpm":72,"accuracy":0,"source":"WATCH_SENSOR"}""", capturedAt, now))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
            """{"bpm":72,$appIdentity}""", capturedAt, now))
        for (accuracy in listOf(-1, 4)) assertFalse(SyncValidation.validCapturedHeartRate(
            72, accuracy, capturedAt, "WATCH_HEALTH_APP", "vendor.health", "Watch Health", now))
    }

    @Test fun appOxygenRequiresCapturedNumericValueAndNamedProvidingApp() {
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.SPO2,
            """{"percentage":98.5,$appIdentity}""", capturedAt, now))
        for (value in listOf("null", "\"98\"", "49.9", "100.1")) {
            assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.SPO2,
                """{"percentage":$value,$appIdentity}""", capturedAt, now))
        }
        for (identity in listOf(
            """"source":"WATCH_HEALTH_APP"""",
            appIdentity.replace("vendor.health", "vendor health"),
            appIdentity.replace("Watch Health", " "),
            appIdentity.replace("Watch Health", "x".repeat(161)),
            appIdentity.replace("\"vendor.health\"", "null"),
            appIdentity.replace("\"Watch Health\"", "98"),
            appIdentity.replace("Watch Health", "Watch\\u0000Health"),
            appIdentity.replace("WATCH_HEALTH_APP", "MANUAL"))) {
            assertFalse(identity, SyncValidation.validIncomingHealthPayload(MessageType.SPO2,
                """{"percentage":98,$identity}""", capturedAt, now))
            assertFalse(identity, SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
                """{"bpm":72,"accuracy":0,$identity}""", capturedAt, now))
        }
    }

    @Test fun appReadingCannotUseReceiveTimeToMakeInvalidCaptureTimeValid() {
        for (timestamp in listOf(0L, -1L, now + 300_001L)) {
            assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
                """{"bpm":72,"accuracy":0,$appIdentity}""", timestamp, now))
            assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.SPO2,
                """{"percentage":98,$appIdentity}""", timestamp, now))
        }
    }

    @Test fun legacyHeartRateStillWorksButExplicitMalformedSourceCannotBeReinterpreted() {
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
            """{"bpm":72,"accuracy":3}""", capturedAt, now))
        for (source in listOf("null", "98", "\"UNKNOWN\"")) assertFalse(
            SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
                """{"bpm":72,"accuracy":3,"source":$source}""", capturedAt, now))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.HEART_RATE,
            """{"bpm":72,"accuracy":3,"source":"WATCH_SENSOR","sourcePackage":"vendor.health"}""", capturedAt, now))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.SPO2,
            """{"percentage":98,"source":"WATCH_SENSOR","sourceLabel":null}""", capturedAt, now))
    }

    @Test fun sourceLabelsIdentifyTheProvidingAppAndDoNotUpgradeLegacyRecords() {
        assertEquals("Watch sensor", healthReadingSourceLabel("WATCH_SENSOR", "", ""))
        assertEquals("Watch app: Watch Health", healthReadingSourceLabel("WATCH_HEALTH_APP", "vendor.health", "Watch Health"))
        assertEquals("Source unavailable", healthReadingSourceLabel("LEGACY_UNVERIFIED", "", ""))
        assertEquals("Source unavailable", healthReadingSourceLabel("WATCH_HEALTH_APP", "", "Watch Health"))
    }

    @Test fun fallbackStatusExplainsTheSearchAndCannotManufactureAHistorySample() {
        val search = heartRateFeedbackFromJson("""{"status":"checking_health_apps"}""", now)
        assertEquals("checking_health_apps", search.status)
        assertEquals("Checking shared readings from watch apps", heartRateFeedbackText(search, true, null, now))
        assertNull(search.sensorPreview)
        assertNull(validatedHeartRateStatus(HeartRateStatusPayload("app_reading"), now))
        assertEquals("app_reading", validatedHeartRateStatus(HeartRateStatusPayload("app_reading", 72, capturedAt), now))
        val found = heartRateFeedbackFromJson("""{"status":"app_reading","bpm":72,"readingTime":900000}""", now)
        assertEquals("Last reading · 1 min ago", heartRateFeedbackText(found, true, capturedAt, now))
        assertNull(found.sensorPreview)
    }
}

package com.healthsync.phone.data

import com.healthsync.phone.data.model.MessageType
import org.junit.Assert.*
import org.junit.Test

class CapturedSleepTest {
    private val recorded = """{"startTime":100000,"endTime":700000,"deepMinutes":0,"lightMinutes":0,"remMinutes":0,"awakeMinutes":0,"source":"WATCH_ACTIGRAPHY","estimatedMinutes":8,"stagesAvailable":false}"""

    @Test fun acceptsOnlyObservedMotionDurationWithoutStaging() {
        assertTrue(SyncValidation.validIncomingHealthPayload(MessageType.SLEEP, recorded, 900_000, 1_000_000))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.SLEEP, recorded.replace("\"estimatedMinutes\":8", "\"estimatedMinutes\":11"), 900_000, 1_000_000))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.SLEEP, recorded.replace("\"deepMinutes\":0", "\"deepMinutes\":2"), 900_000, 1_000_000))
        assertFalse(SyncValidation.validIncomingHealthPayload(MessageType.SLEEP, recorded.replace("\"stagesAvailable\":false", "\"stagesAvailable\":true"), 900_000, 1_000_000))
    }

    @Test fun missingProvenanceAndGsonDefaultsCannotReviveLegacyGeneratedSleep() {
        for (invalid in listOf(recorded.replace(",\"source\":\"WATCH_ACTIGRAPHY\"", ""),
            recorded.replace("\"WATCH_ACTIGRAPHY\"", "null"),
            recorded.replace("\"estimatedMinutes\":8", "\"estimatedMinutes\":0"),
            recorded.replace(",\"stagesAvailable\":false", ""))) {
            assertFalse(invalid, SyncValidation.validIncomingHealthPayload(MessageType.SLEEP, invalid, 900_000, 1_000_000))
        }
    }
}

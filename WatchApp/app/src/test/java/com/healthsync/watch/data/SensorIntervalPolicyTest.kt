package com.healthsync.watch.data

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SensorIntervalPolicyTest {
    private val baseline = SensorIntervalsPayload(60_000L, 300_000L, 0L)

    @Test fun intervalBoundsAndOffAreAcceptedWithoutClampingCorruptValues() {
        listOf(-1L, 30_000L, 60_000L, 300_000L, 3_600_000L).forEach { assertTrue(SensorIntervalPolicy.validInterval(it)) }
        listOf(Long.MIN_VALUE, -2L, 0L, 29_999L, 3_600_001L, Long.MAX_VALUE).forEach { assertFalse(SensorIntervalPolicy.validInterval(it)) }
    }

    @Test fun newerEditsWinAndReconnectReplayDoesNotUndoThem() {
        val watch = baseline.copy(hrIntervalMs = 600_000L, revision = 200L)
        assertFalse(SensorIntervalPolicy.shouldApply(watch, baseline.copy(revision = 100L)))
        assertTrue(SensorIntervalPolicy.shouldApply(watch, baseline.copy(revision = 201L)))
        assertTrue(SensorIntervalPolicy.shouldApply(watch, watch))
        assertFalse(SensorIntervalPolicy.shouldApply(watch, watch.copy(hrIntervalMs = -1L)))
    }

    @Test fun legacyWatchChoiceCanEstablishPhoneBaselineButCannotOverwriteAnEdit() {
        val watch = baseline.copy(hrIntervalMs = -1L)
        assertFalse(SensorIntervalPolicy.shouldApply(baseline, watch))
        assertTrue(SensorIntervalPolicy.shouldApply(baseline, watch, acceptLegacyWatchBaseline = true))
        assertFalse(SensorIntervalPolicy.shouldApply(baseline.copy(revision = 1L), watch, acceptLegacyWatchBaseline = true))
    }

    @Test fun localChangesStayMonotonicAcrossSameMillisecondAndClockRollback() {
        val first = SensorIntervalPolicy.localEdit(baseline, -1L, 300_000L, 1_000L)
        val second = SensorIntervalPolicy.localEdit(first, 600_000L, 300_000L, 1_000L)
        val third = SensorIntervalPolicy.localEdit(second, 600_000L, -1L, 500L)
        assertEquals(1_000L, first.revision)
        assertEquals(1_001L, second.revision)
        assertEquals(1_002L, third.revision)
    }

    @Test fun automaticReplayKeepsRevisionButExplicitApplyMintsEvenForSameChoices() {
        val saved = baseline.copy(revision = 1_000L)
        assertEquals(saved, SensorIntervalPolicy.localEdit(saved, saved.hrIntervalMs, saved.spo2IntervalMs, 2_000L))
        assertEquals(2_000L, SensorIntervalPolicy.localEdit(saved, saved.hrIntervalMs, saved.spo2IntervalMs, 2_000L, forceRevision = true).revision)
    }

    @Test fun intervalSnapshotRoundTripsAndRequiresEveryNumericField() {
        val snapshot = baseline.copy(hrIntervalMs = -1L, spo2IntervalMs = 3_600_000L, revision = 123L)
        assertEquals(snapshot, SensorIntervalPolicy.parse(Gson().toJson(snapshot)))
        listOf("{}", "[]", "null", """{"hrIntervalMs":60000,"spo2IntervalMs":300000}""",
            """{"hrIntervalMs":60000,"revision":1}""", """{"spo2IntervalMs":300000,"revision":1}""",
            """{"hrIntervalMs":"60000","spo2IntervalMs":300000,"revision":1}""",
            """{"hrIntervalMs":60000.5,"spo2IntervalMs":300000,"revision":1}""",
            """{"hrIntervalMs":60000,"spo2IntervalMs":null,"revision":1}""",
            """{"hrIntervalMs":60000,"spo2IntervalMs":300000,"revision":-1}""",
            """{"hrIntervalMs":60000,"spo2IntervalMs":300000,"revision":true}""",
            """{"hrIntervalMs":60000,"spo2IntervalMs":300000,"revision":9223372036854775807}""",
            """{"hrIntervalMs":60000,"spo2IntervalMs":300000,"revision":9223372036854775806}""",
            """{"hrIntervalMs":60000,"spo2IntervalMs":300000,"revision":9223372036854775808}""")
            .forEach { assertNull(it, SensorIntervalPolicy.parse(it)) }
    }

    @Test fun settingsAllowMissingLegacyRevisionButRejectMalformedRevision() {
        assertEquals(baseline, SensorIntervalPolicy.parseSettings("""{"hrIntervalMs":60000,"spo2IntervalMs":300000}"""))
        assertNull(SensorIntervalPolicy.parseSettings("""{"hrIntervalMs":60000,"spo2IntervalMs":300000,"sensorSettingsRevision":null}"""))
        assertNull(SensorIntervalPolicy.parseSettings("""{"hrIntervalMs":60000,"spo2IntervalMs":300000,"sensorSettingsRevision":1.1}"""))
        assertNull(SensorIntervalPolicy.parseSettings("""{"hrIntervalMs":0,"spo2IntervalMs":300000,"sensorSettingsRevision":1}"""))
        assertEquals(17L, SensorIntervalPolicy.parseSettings("""{"hrIntervalMs":60000,"spo2IntervalMs":300000,"sensorSettingsRevision":17}""")?.revision)
    }
}

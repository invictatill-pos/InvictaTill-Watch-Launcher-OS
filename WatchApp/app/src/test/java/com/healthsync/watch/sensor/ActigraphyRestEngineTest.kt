package com.healthsync.watch.sensor

import org.junit.Assert.*
import org.junit.Test

class ActigraphyRestEngineTest {
    private val wallOrigin = 1_000_000L
    private fun feed(engine: ActigraphyRestEngine, from: Int, through: Int,
                     movementAfterSecond: Int = Int.MAX_VALUE, night: Boolean = true,
                     intervalSeconds: Int = 1, wallOffset: Long = 0L): List<ObservedRest> {
        val records = mutableListOf<ObservedRest>()
        for (second in from..through step intervalSeconds) {
            val z = if (second >= movementAfterSecond && second % 2 == 0) 13f else 9.81f
            engine.sample(0f, 0f, z, second * 1000L, wallOrigin + second * 1000L + wallOffset, night)?.let { records += it }
        }
        return records
    }

    @Test fun emptyEngineAndShortRestNeverInventARecord() {
        val engine = ActigraphyRestEngine()
        assertFalse(engine.isResting)
        assertTrue(feed(engine, 0, 42 * 60, movementAfterSecond = 19 * 60).isEmpty())
        assertFalse(engine.isResting)
    }

    @Test fun observedNightRestProducesOnlyCapturedMinutesAndActualMovementBoundary() {
        val records = feed(ActigraphyRestEngine(), 0, 42 * 60, movementAfterSecond = 30 * 60)
        assertEquals(1, records.size)
        assertEquals(ObservedRest(wallOrigin, wallOrigin + 30 * 60_000L, 30), records.single())
    }

    @Test fun missingSamplesDoNotBecomeHoursOfRest() {
        val engine = ActigraphyRestEngine()
        feed(engine, 0, 25 * 60)
        assertTrue(engine.isResting)
        // Service downtime breaks continuity rather than being counted as rest.
        assertTrue(feed(engine, 60 * 60, 72 * 60, movementAfterSecond = 60 * 60).isEmpty())
        assertFalse(engine.isResting)
    }

    @Test fun sparseFramesAndDaytimeStillnessCannotStartAnEstimate() {
        val sparse = ActigraphyRestEngine()
        assertTrue(feed(sparse, 0, 60 * 60, intervalSeconds = 5).isEmpty())
        assertFalse(sparse.isResting)
        val day = ActigraphyRestEngine()
        assertTrue(feed(day, 0, 42 * 60, movementAfterSecond = 30 * 60, night = false).isEmpty())
        assertFalse(day.isResting)
    }

    @Test fun wallClockChangesAndOutOfOrderFramesCannotExtendAnEstimate() {
        val engine = ActigraphyRestEngine()
        feed(engine, 0, 25 * 60)
        assertNull(engine.sample(0f, 0f, 9.81f, 1000L, wallOrigin + 1000L, true))
        assertTrue(engine.isResting)
        assertTrue(feed(engine, 25 * 60 + 1, 40 * 60, movementAfterSecond = 25 * 60,
            wallOffset = 3_600_000L).isEmpty())
        assertFalse(engine.isResting)
    }
}

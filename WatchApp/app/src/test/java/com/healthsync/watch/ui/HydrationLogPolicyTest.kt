package com.healthsync.watch.ui

import org.junit.Assert.*
import org.junit.Test

class HydrationLogPolicyTest {
    @Test fun midnightResetsTheTotalBeforeRecordingOrUndoing() {
        val old = HydrationLogPolicy.State("yesterday", 2_000, 500, 123)
        assertEquals(HydrationLogPolicy.State("today"), HydrationLogPolicy.forDate(old, "today"))
        assertEquals(250, HydrationLogPolicy.record(old, "today", 250, 200).amountMl)
        assertEquals(0, HydrationLogPolicy.undo(old, "today").amountMl)
    }

    @Test fun undoRemovesOnlyTheLatestExplicitEntryAndCannotBeRepeated() {
        val first = HydrationLogPolicy.record(HydrationLogPolicy.State("today"), "today", 250, 123)
        val second = HydrationLogPolicy.record(first, "today", 500, 124)
        assertEquals(750, second.amountMl)
        val undo = HydrationLogPolicy.undo(second, "today")
        assertEquals(250, undo.amountMl)
        assertEquals(undo, HydrationLogPolicy.undo(undo, "today"))
    }

    @Test fun invalidEntriesAndGoalsCannotCreateAnAutomaticAmountOrDivideByZero() {
        val empty = HydrationLogPolicy.State("today")
        assertEquals(empty, HydrationLogPolicy.record(empty, "today", 0, 1))
        assertEquals(empty, HydrationLogPolicy.record(empty, "today", -250, 1))
        assertEquals(empty, HydrationLogPolicy.record(empty, "today", 250, 0))
        assertEquals(250, HydrationLogPolicy.goal(0))
        assertEquals(5_000, HydrationLogPolicy.goal(Int.MAX_VALUE))
    }
}

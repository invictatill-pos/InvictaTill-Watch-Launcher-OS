package com.healthsync.watch.ui

/** A daily manual log. Midnight must never carry yesterday's intake into today's total. */
internal object HydrationLogPolicy {
    data class State(val date: String, val amountMl: Int = 0, val undoAmountMl: Int = 0, val capturedAt: Long = 0)
    fun forDate(state: State, today: String): State =
        if (state.date != today) State(today) else state.copy(amountMl = state.amountMl.coerceAtLeast(0),
            undoAmountMl = state.undoAmountMl.coerceIn(0, state.amountMl.coerceAtLeast(0)))

    fun record(state: State, today: String, amountMl: Int, nowMs: Long): State {
        val current = forDate(state, today)
        if (amountMl <= 0 || nowMs <= 0) return current
        val total = (current.amountMl.toLong() + amountMl).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return State(today, total, total - current.amountMl, nowMs)
    }

    fun undo(state: State, today: String): State {
        val current = forDate(state, today)
        return current.copy(amountMl = current.amountMl - current.undoAmountMl, undoAmountMl = 0, capturedAt = 0)
    }

    fun goal(value: Int) = value.coerceIn(250, 5_000)
}

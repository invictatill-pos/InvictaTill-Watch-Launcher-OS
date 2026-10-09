package com.healthsync.watch.timer

import android.content.Context
import android.os.Build
import android.provider.Settings

object WatchTimerStore {
    private const val NAME = "watch_shell_timer"
    private val lock = Any()

    fun bootCount(context: Context): Int = if (Build.VERSION.SDK_INT >= 24) runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1) else -1

    fun read(context: Context): TimerCheckpoint = synchronized(lock) {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        TimerCheckpoint(
            runCatching { TimerPhase.valueOf(prefs.getString("phase", TimerPhase.IDLE.name).orEmpty()) }.getOrDefault(TimerPhase.IDLE),
            prefs.getLong("duration", 0L).coerceIn(0L, 86_400_000L),
            prefs.getLong("remaining", 0L).coerceIn(0L, 86_400_000L),
            prefs.getLong("elapsed_anchor", 0L), prefs.getLong("wall_anchor", 0L), prefs.getInt("boot", -1)
        )
    }

    fun write(context: Context, state: TimerCheckpoint) = synchronized(lock) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("phase", state.phase.name).putLong("duration", state.durationMs)
            .putLong("remaining", state.remainingAtAnchorMs).putLong("elapsed_anchor", state.anchorElapsedMs)
            .putLong("wall_anchor", state.anchorWallMs).putInt("boot", state.bootCount).commit()
        Unit
    }

    fun exact(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("exact", false)
    fun setExact(context: Context, exact: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean("exact", exact).apply()
    }

    /** Receiver and service race to expire the same timer; exactly one owns the transition. */
    fun finishIfDue(context: Context, elapsedMs: Long, wallMs: Long): Boolean = synchronized(lock) {
        val state = read(context)
        if (state.phase != TimerPhase.RUNNING || state.remaining(elapsedMs, wallMs, bootCount(context)) > 0) return@synchronized false
        write(context, state.copy(phase = TimerPhase.FINISHED, remainingAtAnchorMs = 0L))
        true
    }
}

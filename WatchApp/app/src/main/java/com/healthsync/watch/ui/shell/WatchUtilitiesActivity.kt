package com.healthsync.watch.ui.shell

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.healthsync.watch.timer.StopwatchCheckpoint
import com.healthsync.watch.timer.TimerPhase
import com.healthsync.watch.timer.WatchTimerScheduler
import com.healthsync.watch.timer.WatchTimerStore
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Watch-sized timer and stopwatch with persisted monotonic checkpoints. */
class WatchUtilitiesActivity : LauncherPanelActivity() {
    companion object { const val EXTRA_TOOL = "watch_tool"; private const val NOTIFICATION_REQUEST = 610 }
    private var tool = "tools"
    private var selectedDurationMs = 300_000L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private var dial: TimeDial? = null
    private var status: TextView? = null
    private var timerActions: LinearLayout? = null
    private var lapRows: LinearLayout? = null
    private var currentPhase: TimerPhase? = null
    private var resumed = false
    private var pendingTimerStart = false
    private var pendingTimerResume = false
    private var stopwatch = StopwatchCheckpoint()
    private val stopwatchPrefs by lazy { getSharedPreferences("watch_shell_stopwatch", Context.MODE_PRIVATE) }
    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            refreshClock()
            handler.postDelayed(this, if (tool == "stopwatch" && stopwatch.running) 100L else 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tool = savedInstanceState?.getString("tool") ?: intent.getStringExtra(EXTRA_TOOL) ?: "tools"
        selectedDurationMs = (savedInstanceState?.getLong("duration")
            ?: getSharedPreferences("watch_shell_tools", Context.MODE_PRIVATE).getLong("selected_duration", 300_000L)).coerceIn(15_000L, 86_400_000L)
        readStopwatch()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tool = intent.getStringExtra(EXTRA_TOOL) ?: tool
        render()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        readStopwatch()
        // Permission may have changed in Android's exact-alarm settings.
        if (WatchTimerStore.read(this).phase == TimerPhase.RUNNING) WatchTimerScheduler.schedule(this)
        render()
        handler.removeCallbacks(tick); handler.post(tick)
    }

    override fun onPause() {
        resumed = false; handler.removeCallbacks(tick)
        saveStopwatch()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tool", tool); outState.putLong("duration", selectedDurationMs)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (tool == "tools") finish() else { tool = "tools"; render() }
    }

    private fun render() {
        content = roundContent()
        content.setPadding((resources.displayMetrics.widthPixels * .14f).roundToInt(),
            (resources.displayMetrics.heightPixels * .14f).roundToInt(),
            (resources.displayMetrics.widthPixels * .14f).roundToInt(),
            (resources.displayMetrics.heightPixels * .18f).roundToInt())
        dial = null; status = null; timerActions = null; lapRows = null; currentPhase = null
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("‹", accent) {
            if (tool == "tools") finish() else { tool = "tools"; render() }
        }.apply { textSize = 25f }, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(labelView(when (tool) { "timer" -> "Timer"; "stopwatch" -> "Stopwatch"; else -> "Tools" }, 21f, Color.WHITE).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        },
            LinearLayout.LayoutParams(0, dp(44), 1f))
        content.addView(header, LinearLayout.LayoutParams(-1, -2))
        when (tool) {
            "timer" -> renderTimer()
            "stopwatch" -> renderStopwatch()
            else -> renderTools()
        }
        refreshClock()
    }

    private fun renderTools() {
        val timer = WatchTimerStore.read(this)
        toolButton(android.R.drawable.ic_lock_idle_alarm, "Timer", when (timer.phase) {
            TimerPhase.RUNNING -> "${timerText(timer.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(this)))} left"
            TimerPhase.PAUSED -> "Paused"
            TimerPhase.FINISHED -> "Finished · stop alert"
            else -> "Countdown with alert"
        }) { tool = "timer"; render() }
        toolButton(android.R.drawable.ic_menu_recent_history, "Stopwatch", if (stopwatch.running) "Running · ${stopwatchText(stopwatch.elapsed(SystemClock.elapsedRealtime(), WatchTimerStore.bootCount(this)))}" else "Time and record laps") {
            tool = "stopwatch"; render()
        }
        toolButton(android.R.drawable.ic_lock_idle_alarm, "Alarms", "Open watch alarm app") {
            launchFirst(listOf(Intent(AlarmClock.ACTION_SHOW_ALARMS), Intent(AlarmClock.ACTION_SET_ALARM)),
                "Watch alarms could not open. Use Timer for a countdown alert.")
        }
    }

    private fun renderTimer() {
        WatchTimerStore.read(this).durationMs.takeIf { it > 0 }?.let { selectedDurationMs = it }
        dial = TimeDial(this).also { content.addView(it, LinearLayout.LayoutParams(-1, dp(142))) }
        status = labelView("", 11f, muted).also { content.addView(it) }
        timerActions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(timerActions, LinearLayout.LayoutParams(-1, -2))
        updateTimerControls(WatchTimerStore.read(this).phase)
        if (Build.VERSION.SDK_INT >= 31 && !WatchTimerScheduler.canScheduleExact(this)) {
            content.addView(button("Enable precise timer", accent) {
                launchFirst(listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))),
                    "Exact alarm access is unavailable. Timer alerts may be delayed.")
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
            content.addView(labelView("Approximate alerts may be delayed while the watch sleeps.", 10f, muted), LinearLayout.LayoutParams(-1, -2))
        }
        if (!notificationsEnabled()) {
            content.addView(button("Enable timer alerts", accent) { requestTimerAlerts(startAfter = false) },
                LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        }
    }

    private fun updateTimerControls(phase: TimerPhase) {
        val actions = timerActions ?: return
        actions.removeAllViews(); currentPhase = phase
        when (phase) {
            TimerPhase.IDLE -> {
                val presets = LinearLayout(this)
                for (minutes in listOf(1, 3, 5)) presets.addView(button("${minutes}m", if (selectedDurationMs == minutes * 60_000L) accent else Color.WHITE) {
                    selectedDurationMs = minutes * 60_000L; saveDuration(); updateTimerControls(TimerPhase.IDLE); refreshClock()
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), dp(7)) })
                actions.addView(presets)
                val adjust = LinearLayout(this)
                adjust.addView(button("− 15s") { adjustDuration(-15_000L) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
                adjust.addView(button("+ 1m") { adjustDuration(60_000L) }, LinearLayout.LayoutParams(0, dp(44), 1f))
                actions.addView(adjust)
                actions.addView(button("Start", accent) {
                    startTimerSafely()
                }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(7) })
            }
            TimerPhase.RUNNING -> addPair(actions, "Pause", "Cancel", {
                WatchTimerScheduler.pause(this); refreshClock()
            }, { WatchTimerScheduler.stop(this); refreshClock() })
            TimerPhase.PAUSED -> addPair(actions, "Resume", "Cancel", {
                if (!notificationsEnabled()) requestTimerAlerts(startAfter = false, resumeAfter = true) else resumeTimer()
            }, { WatchTimerScheduler.stop(this); refreshClock() })
            TimerPhase.FINISHED -> {
                actions.addView(button("Stop alert", 0xFFFFA593.toInt()) { WatchTimerScheduler.stop(this); refreshClock() }, LinearLayout.LayoutParams(-1, dp(50)))
                actions.addView(button("Start again", accent) { startTimerSafely() }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })
            }
        }
    }

    private fun adjustDuration(changeMs: Long) {
        selectedDurationMs = (selectedDurationMs + changeMs).coerceIn(15_000L, 86_400_000L)
        saveDuration()
        updateTimerControls(TimerPhase.IDLE); refreshClock()
    }

    private fun saveDuration() {
        getSharedPreferences("watch_shell_tools", Context.MODE_PRIVATE).edit().putLong("selected_duration", selectedDurationMs).apply()
    }

    private fun startTimer() {
        val old = WatchTimerStore.read(this)
        if (old.phase == TimerPhase.FINISHED && old.durationMs > 0) selectedDurationMs = old.durationMs
        saveDuration()
        if (!WatchTimerScheduler.start(this, selectedDurationMs)) message("Timer could not start. Check watch alarm settings and try again.")
        refreshClock()
    }

    private fun startTimerSafely() {
        if (!notificationsEnabled()) requestTimerAlerts(startAfter = true) else startTimer()
    }

    private fun resumeTimer() {
        if (!WatchTimerScheduler.resume(this)) message("Timer could not resume. Try starting a new timer.")
        refreshClock()
    }

    private fun requestTimerAlerts(startAfter: Boolean, resumeAfter: Boolean = false) {
        pendingTimerStart = startAfter
        pendingTimerResume = resumeAfter
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        } else {
            launchFirst(listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))), "Notification settings is unavailable.")
            pendingTimerStart = false
            pendingTimerResume = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_REQUEST) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            if (granted && pendingTimerResume) resumeTimer()
            else if (granted && pendingTimerStart) startTimer()
            else if (!granted) message("Timer alerts need notification access. Enable alerts to hear and dismiss finished timers.")
            pendingTimerStart = false
            pendingTimerResume = false
            render()
        }
    }

    private fun notificationsEnabled(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager?.getNotificationChannel(WatchTimerScheduler.ALERT_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private fun renderStopwatch() {
        dial = TimeDial(this).apply { track = false }.also { content.addView(it, LinearLayout.LayoutParams(-1, dp(142))) }
        status = labelView(if (stopwatch.running) "Running" else "Ready", 11f, muted).also { content.addView(it) }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(actions)
        addPair(actions, if (stopwatch.running) "Pause" else if (stopwatch.accumulatedMs > 0) "Resume" else "Start", if (stopwatch.running) "Lap" else "Reset", {
            val now = SystemClock.elapsedRealtime(); val boot = WatchTimerStore.bootCount(this)
            stopwatch = if (stopwatch.running) stopwatch.pause(now, boot) else stopwatch.resume(now, boot)
            saveStopwatch(); render()
        }, {
            if (stopwatch.running && stopwatch.laps.size >= 100) {
                message("100 laps recorded. Pause and reset to start a new session.")
            } else {
                stopwatch = if (stopwatch.running) stopwatch.lap(SystemClock.elapsedRealtime(), WatchTimerStore.bootCount(this)) else StopwatchCheckpoint()
                saveStopwatch(); render()
            }
        })
        lapRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(lapRows, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val rows = lapRows ?: return
        stopwatch.laps.withIndex().reversed().forEach { (index, elapsed) ->
            val previous = stopwatch.laps.getOrNull(index - 1) ?: 0L
            rows.addView(labelView("Lap ${index + 1}     ${stopwatchText((elapsed - previous).coerceAtLeast(0))}", 13f, Color.WHITE))
        }
    }

    private fun refreshClock() {
        val now = SystemClock.elapsedRealtime(); val wall = System.currentTimeMillis(); val boot = WatchTimerStore.bootCount(this)
        when (tool) {
            "timer" -> {
                var state = WatchTimerStore.read(this)
                if (state.phase == TimerPhase.RUNNING && state.remaining(now, wall, boot) == 0L) {
                    WatchTimerScheduler.expire(this); state = WatchTimerStore.read(this)
                }
                val remaining = if (state.phase == TimerPhase.IDLE) selectedDurationMs else state.remaining(now, wall, boot)
                dial?.time = timerText(remaining)
                dial?.fraction = if (state.durationMs > 0) remaining.toFloat() / state.durationMs else 1f
                dial?.caption = when (state.phase) { TimerPhase.RUNNING -> "REMAINING"; TimerPhase.PAUSED -> "PAUSED"; TimerPhase.FINISHED -> "FINISHED"; else -> "DURATION" }
                dial?.invalidate()
                status?.text = when {
                    state.phase == TimerPhase.FINISHED -> "Timer finished"
                    !notificationsEnabled() -> "Alerts disabled"
                    state.phase == TimerPhase.RUNNING && !WatchTimerStore.exact(this) -> "Approximate alert · may be delayed"
                    Build.VERSION.SDK_INT >= 31 && !WatchTimerScheduler.canScheduleExact(this) -> "Approximate alert"
                    else -> "Timer alert enabled"
                }
                if (currentPhase != state.phase) updateTimerControls(state.phase)
            }
            "stopwatch" -> {
                dial?.time = stopwatchText(stopwatch.elapsed(now, boot)); dial?.caption = "ELAPSED"; dial?.invalidate()
                status?.text = if (stopwatch.running) "Running" else if (stopwatch.accumulatedMs > 0) "Paused" else "Ready"
            }
        }
    }

    private fun readStopwatch() {
        stopwatch = StopwatchCheckpoint(stopwatchPrefs.getLong("elapsed", 0L), stopwatchPrefs.getLong("anchor", 0L),
            stopwatchPrefs.getBoolean("running", false), stopwatchPrefs.getInt("boot", -1),
            stopwatchPrefs.getString("laps", "").orEmpty().split(',').mapNotNull { it.toLongOrNull()?.takeIf { value -> value >= 0 } }.take(100))
            .checkpoint(SystemClock.elapsedRealtime(), WatchTimerStore.bootCount(this))
    }

    private fun saveStopwatch() {
        stopwatch = stopwatch.checkpoint(SystemClock.elapsedRealtime(), WatchTimerStore.bootCount(this))
        stopwatchPrefs.edit().putLong("elapsed", stopwatch.accumulatedMs).putLong("anchor", stopwatch.anchorElapsedMs)
            .putBoolean("running", stopwatch.running).putInt("boot", stopwatch.bootCount)
            .putString("laps", stopwatch.laps.joinToString(",")).apply()
    }

    private fun timerText(ms: Long): String {
        val seconds = ceil(ms.coerceAtLeast(0) / 1000.0).toLong()
        return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    }
    private fun stopwatchText(ms: Long): String {
        val tenths = ms.coerceAtLeast(0) / 100
        return if (tenths >= 36000) String.format(Locale.getDefault(), "%d:%02d:%02d.%d", tenths / 36000, tenths / 600 % 60, tenths / 10 % 60, tenths % 10)
        else String.format(Locale.getDefault(), "%02d:%02d.%d", tenths / 600, tenths / 10 % 60, tenths % 10)
    }

    private fun toolButton(icon: Int, title: String, detail: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = shape(panelColor)
            setPadding(dp(12), dp(10), dp(12), dp(10)); minimumHeight = dp(74)
            isFocusable = true
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); action() }
        }
        row.addView(ImageView(this).apply {
            setImageResource(icon); setColorFilter(accent); scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), 0, 0, 0) }
        words.addView(labelView(title, 17f, Color.WHITE).apply { gravity = Gravity.START })
        words.addView(labelView(detail, 10f, muted).apply { gravity = Gravity.START })
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
    }

    private fun labelView(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        includeFontPadding = false; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(2), dp(5), dp(2), dp(5))
    }
    private fun button(value: String, color: Int = Color.WHITE, action: () -> Unit) = labelView(value, 14f, color).apply {
        background = buttonBackground(); minimumHeight = dp(44); isFocusable = true
        contentDescription = value
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); action() }
    }
    private fun addPair(parent: LinearLayout, first: String, second: String, firstAction: () -> Unit, secondAction: () -> Unit) {
        val row = LinearLayout(this)
        row.addView(button(first, accent, firstAction), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        row.addView(button(second, Color.WHITE, secondAction), LinearLayout.LayoutParams(0, dp(48), 1f))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
    }

    private class TimeDial(context: Context) : View(context) {
        var time = "00:00"; var caption = ""; var fraction = 1f; var track = true
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arc = RectF()
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val diameter = minOf(width, height).toFloat(); val cx = width / 2f; val cy = height / 2f
            if (track) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = diameter * .035f; paint.strokeCap = Paint.Cap.ROUND
                val radius = diameter * .45f
                arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
                paint.color = 0xFF1A2A23.toInt(); canvas.drawOval(arc, paint)
                paint.color = 0xFF8CE7C8.toInt(); canvas.drawArc(arc, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
            }
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); paint.textSize = diameter * .24f
            val measured = paint.measureText(time)
            if (measured > width * .82f) paint.textSize *= width * .82f / measured
            canvas.drawText(time, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
            paint.color = 0xFFA8B9B1.toInt(); paint.textSize = diameter * .063f
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            canvas.drawText(caption, cx, cy + diameter * .23f, paint)
            contentDescription = "$caption, $time"
        }
    }
}

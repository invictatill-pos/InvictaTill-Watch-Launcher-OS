package com.healthsync.watch.ui.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.healthsync.watch.data.WatchPreferences
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.service.WorkoutTrackingService
import com.healthsync.watch.ui.ActiveWorkoutActivity
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.StrengthWorkoutActivity
import com.healthsync.watch.ui.WatchHistoryActivity
import com.healthsync.watch.ui.WatchOptionsActivity
import com.healthsync.watch.ui.WorkoutHistoryStore
import com.healthsync.watch.ui.WorkoutSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** Fitness tile in Home. Every number comes from a watch reading or a saved session. */
class FitnessPanel(
    private val activity: AppCompatActivity,
    private val onClose: () -> Unit,
    private val onOpenSettings: () -> Unit
) {
    private val accent = 0xFF8CE7C8.toInt()
    private val muted = 0xFF9CAFA7.toInt()
    private val preferences = WatchPreferences(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var historyJob: Job? = null
    private var resumed = false
    private var registered = false
    private var savedMinutes = 0L
    private var savedDistance = 0.0
    private var savedCalories = 0.0
    private var savedSessions = 0
    private var liveDuration = 0L
    private var liveDistance = 0f
    private var lastHistoryDate = ""
    private var lastHistoryLoad = 0L
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val diameter = minOf(activity.resources.displayMetrics.widthPixels, activity.resources.displayMetrics.heightPixels)
        val side = (diameter * .14f).roundToInt()
        val end = (diameter * .17f).roundToInt().coerceAtLeast(dp(40))
        setPadding(side, end, side, end)
    }
    val view: View = RoundScrollView(activity).apply {
        setBackgroundColor(Color.BLACK)
        isFillViewport = true
        addView(content, ViewGroup.LayoutParams(-1, -2))
    }
    private val battery: TextView
    private val ring = StepGoalRing(activity)
    private val minutes: TextView
    private val distance: TextView
    private val calories: TextView
    private val heartRate: TextView
    private val heartStatus: TextView
    private val sessionInfo: TextView
    private val liveCard: LinearLayout
    private val liveTitle: TextView
    private val liveInfo: TextView
    private val workoutAction: TextView
    private val updates = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WorkoutTrackingService.ACTION_UPDATE) {
                liveDuration = intent.getLongExtra(WorkoutTrackingService.EXTRA_DURATION, 0L).coerceAtLeast(0L)
                liveDistance = intent.getFloatExtra(WorkoutTrackingService.EXTRA_DISTANCE, 0f).takeIf { it.isFinite() && it >= 0 } ?: 0f
            }
            if (intent?.action == "com.healthsync.watch.WORKOUT_SAVED") loadHistory()
            refresh()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            refresh()
            if (System.currentTimeMillis() - lastHistoryLoad > 60_000L || lastHistoryDate != dateKey()) loadHistory()
            handler.postDelayed(this, 1_000L)
        }
    }

    init {
        text(content, "Activity", 25f, Color.WHITE, true).gravity = Gravity.CENTER
        battery = text(content, "Today", 11f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(7), 0, dp(8)) }
        content.addView(ring, LinearLayout.LayoutParams(-1, dp(184)))
        val stats = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        minutes = metric(stats, "Exercise", 0xFFFFBC6A.toInt())
        distance = metric(stats, "Workout distance", 0xFF9FC3FF.toInt())
        content.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
        val health = panel()
        text(health, "HEART RATE", 10f, 0xFFFF8999.toInt(), true)
        heartRate = text(health, "— bpm", 27f, Color.WHITE, true).apply { setPadding(0, dp(7), 0, dp(6)) }
        heartStatus = text(health, "Tap to measure", 11f, muted)
        health.isClickable = true; health.isFocusable = true
        health.setOnClickListener { openHealth() }
        content.addView(health, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val totals = panel()
        calories = text(totals, "0 kcal estimated", 16f, 0xFFFFBC6A.toInt(), true)
        sessionInfo = text(totals, "Loading saved workouts…", 11f, muted).apply { setPadding(0, dp(6), 0, 0) }
        totals.setOnClickListener { launch(WatchHistoryActivity::class.java) }
        totals.isFocusable = true
        content.addView(totals, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        liveCard = panel().apply { background = shape(0xFF20382B.toInt()) }
        liveTitle = text(liveCard, "Workout in progress", 16f, accent, true)
        liveInfo = text(liveCard, "", 12f, Color.WHITE).apply { setPadding(0, dp(7), 0, 0) }
        liveCard.isFocusable = true
        liveCard.setOnClickListener { openCurrentWorkout() }
        content.addView(liveCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        workoutAction = action("Start a workout", accent) {
            if (WorkoutTrackingService.isActive) openCurrentWorkout() else launch(WorkoutSelectionActivity::class.java)
        }
        action("Workout history", 0xFF9FC3FF.toInt()) { launch(WatchHistoryActivity::class.java) }
        action("Health measurements", 0xFFFF8999.toInt()) { openHealth() }
        action("Activity settings", muted, onOpenSettings)
        action("Back to clock", muted, onClose).background = shape(Color.TRANSPARENT)
        refresh()
    }

    fun onResume() {
        if (resumed) return
        resumed = true
        runCatching {
            ContextCompat.registerReceiver(activity, updates, IntentFilter().apply {
                addAction(WorkoutTrackingService.ACTION_UPDATE)
                addAction("com.healthsync.watch.WORKOUT_SAVED")
                addAction("com.healthsync.watch.STEP_UPDATE")
                addAction("com.healthsync.watch.HR_UPDATE")
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        if (WorkoutTrackingService.isActive) runCatching {
            ContextCompat.startForegroundService(activity, Intent(activity, WorkoutTrackingService::class.java)
                .setAction(WorkoutTrackingService.ACTION_REQUEST_UPDATE))
        }
        loadHistory()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        historyJob?.cancel()
        if (registered) { runCatching { activity.unregisterReceiver(updates) }; registered = false }
    }

    fun destroy() { onPause(); scope.cancel() }

    private fun loadHistory() {
        historyJob?.cancel()
        lastHistoryLoad = System.currentTimeMillis()
        historyJob = scope.launch {
            val midnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sessions = withContext(Dispatchers.IO) {
                WorkoutHistoryStore.read(activity.applicationContext).filter { it.start_time >= midnight && it.start_time <= System.currentTimeMillis() }
            }
            savedMinutes = sessions.sumOf { it.duration_sec.coerceAtLeast(0).toLong() } / 60L
            savedDistance = sessions.sumOf { it.distance_m.takeIf { value -> value.isFinite() && value >= 0 }?.toDouble() ?: 0.0 }
            savedCalories = sessions.sumOf { it.calories.takeIf { value -> value.isFinite() && value >= 0 } ?: 0.0 }
            savedSessions = sessions.size
            lastHistoryDate = dateKey()
            refresh()
        }
    }

    private fun refresh() {
        val level = (activity.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        battery.text = "Today${level?.let { " · Watch $it%" } ?: ""}"
        ring.update(SensorCollectorService.latestSteps.coerceAtLeast(0), preferences.stepGoal)
        minutes.text = "$savedMinutes min"
        distance.text = String.format(Locale.getDefault(), "%.2f km", savedDistance / 1000.0)
        calories.text = "${savedCalories.toLong()} kcal estimated"
        sessionInfo.text = "$savedSessions saved ${if (savedSessions == 1) "workout" else "workouts"} today"
        heartRate.text = SensorCollectorService.heartRateValueText()
        heartStatus.text = SensorCollectorService.heartRateStatusText(activity)
        liveCard.visibility = if (WorkoutTrackingService.isActive) View.VISIBLE else View.GONE
        workoutAction.text = if (WorkoutTrackingService.isActive) "Open current workout" else "Start a workout"
        if (WorkoutTrackingService.isActive) {
            liveTitle.text = "${WorkoutTrackingService.activeActivityType} · ${if (WorkoutTrackingService.currentlyPaused) "Paused" else "Active"}"
            val seconds = liveDuration / 1000L
            liveInfo.text = String.format(Locale.getDefault(), "%d:%02d · %.2f km", seconds / 60L, seconds % 60L, liveDistance / 1000f)
            liveCard.contentDescription = "${liveTitle.text}, ${liveInfo.text}. Tap to open current workout"
        }
    }

    private fun openCurrentWorkout() {
        if (!WorkoutTrackingService.isActive) { launch(WorkoutSelectionActivity::class.java); return }
        val type = WorkoutTrackingService.activeActivityType
        val target = if (type.contains("Home", true) || type.contains("Strength", true)) StrengthWorkoutActivity::class.java else ActiveWorkoutActivity::class.java
        runCatching { activity.startActivity(Intent(activity, target).putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, type)) }
            .onFailure { toast("Workout could not open") }
    }

    private fun openHealth() {
        runCatching { activity.startActivity(Intent(activity, WatchOptionsActivity::class.java)
            .putExtra(WatchOptionsActivity.EXTRA_SCREEN, WatchOptionsActivity.SCREEN_HEALTH)) }
            .onFailure { toast("Health measurements could not open") }
    }
    private fun launch(target: Class<*>) = runCatching { activity.startActivity(Intent(activity, target)) }
        .onFailure { toast("Screen could not open") }
    private fun dateKey() = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    private fun panel() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = shape(0xFF151D19.toInt())
    }
    private fun metric(parent: LinearLayout, title: String, color: Int): TextView {
        val box = panel().apply { gravity = Gravity.CENTER }
        val value = text(box, "—", 19f, color, true).apply { gravity = Gravity.CENTER }
        text(box, title, 10f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
        parent.addView(box, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        return value
    }
    private fun action(title: String, color: Int, click: () -> Unit) = TextView(activity).apply {
        text = title; textSize = 14f; setTextColor(color); setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER; minHeight = dp(48); setPadding(dp(9), dp(12), dp(9), dp(12))
        background = shape(0xFF18221D.toInt()); isFocusable = true
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); click() }
        content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    private fun text(parent: LinearLayout, title: String, size: Float, color: Int, bold: Boolean = false) = TextView(activity).apply {
        text = title; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) setTypeface(null, Typeface.BOLD)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat() }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
}

private class StepGoalRing(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var steps = 0
    private var goal = 10_000
    fun update(value: Int, target: Int) {
        if (steps == value && goal == target) return
        steps = value; goal = target.coerceAtLeast(1)
        contentDescription = "$steps of $goal steps today"
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val radius = minOf(width, height) * .42f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .115f; paint.strokeCap = Paint.Cap.ROUND
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.color = 0xFF1B3327.toInt(); canvas.drawArc(bounds, -90f, 360f, false, paint)
        paint.color = 0xFF8CE7C8.toInt(); canvas.drawArc(bounds, -90f, 360f * (steps.toFloat() / goal).coerceIn(0f, 1f), false, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.color = Color.WHITE
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD); paint.textSize = radius * .44f
        canvas.drawText(NumberFormat.getIntegerInstance().format(steps), cx, cy + radius * .035f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL); paint.textSize = radius * .14f; paint.color = 0xFF9CAFA7.toInt()
        canvas.drawText("of ${NumberFormat.getIntegerInstance().format(goal)} steps", cx, cy + radius * .31f, paint)
        if (steps >= goal) {
            paint.color = 0xFF8CE7C8.toInt(); paint.textSize = radius * .145f
            canvas.drawText("Goal complete", cx, cy - radius * .4f, paint)
        }
    }
}

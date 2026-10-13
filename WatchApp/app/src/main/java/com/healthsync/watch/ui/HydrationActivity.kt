package com.healthsync.watch.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.healthsync.watch.data.HydrationPayload
import com.healthsync.watch.data.MessageType
import com.healthsync.watch.data.SyncMessage
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/** Water is explicitly logged by the user; it is never presented as a sensor reading. */
class HydrationActivity : LauncherPanelActivity() {
    private val prefs by lazy { getSharedPreferences("healthsync_hydration", MODE_PRIVATE) }
    private lateinit var wave: HydrationWaveView
    private lateinit var amount: TextView
    private lateinit var progress: TextView
    private lateinit var lastLog: TextView
    private lateinit var undo: Button
    private var state = HydrationLogPolicy.State("")
    private var goalMl = 2_000
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private val hydrationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadData()
            refresh()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            loadData()
            refresh()
            handler.postDelayed(this, 30_000L)
        }
    }
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Legacy totals could include an automatic 750 ml baseline. Keep only new explicit logs.
        if (prefs.getInt("manual_log_schema", 0) != 1) {
            val migration = prefs.edit()
            if (prefs.contains("intake_ml")) migration.putInt("legacy_unverified_intake_ml", prefs.getInt("intake_ml", 0))
            if (prefs.contains("saved_date")) migration.putString("legacy_unverified_saved_date", prefs.getString("saved_date", null))
            if (prefs.contains("intake_ml") || prefs.contains("saved_date")) migration.putLong("legacy_unverified_archived_at", System.currentTimeMillis())
            migration.remove("saved_date").remove("intake_ml").putInt("manual_log_schema", 1).apply()
        }
        loadData()
        val content = roundContent()
        navigationHeader(content, "Water log")
        val logCard = card(content)
        label(logCard, "TODAY · MANUAL LOG", 10f, accent, bold = true)
        wave = HydrationWaveView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val diameter = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val size = minOf(dp(120), (diameter * .58f).toInt())
        logCard.addView(wave, LinearLayout.LayoutParams(-1, size))
        amount = label(logCard, "0 ml", 28f, Color.WHITE, bold = true)
        progress = label(logCard, "", 12f, accent)
        lastLog = label(logCard, "No water logged today", 11f)
        primaryAction(content, "Log 250 ml") { record(250) }
        action(content, "Log 500 ml") { record(500) }
        undo = action(content, "Undo last entry") {
            state = HydrationLogPolicy.undo(state, today())
            saveData()
            refresh()
            lastLog.announceForAccessibility("Last water entry removed")
        }
        val goalCard = card(content)
        label(goalCard, "Your daily goal", 15f, Color.WHITE, bold = true)
        action(goalCard, "− 250 ml goal") { changeGoal(-250) }
        action(goalCard, "+ 250 ml goal") { changeGoal(250) }
        label(goalCard, "Set a goal that suits you. Only entries you log count toward this total.", 11f)
        action(content, "Back to clock") { backToClock() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        handler.removeCallbacks(tick)
        handler.post(tick)
        runCatching {
            ContextCompat.registerReceiver(this, hydrationReceiver,
                IntentFilter("com.healthsync.watch.HYDRATION_UPDATE"), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(hydrationReceiver) }
        super.onPause()
    }

    private fun loadData() {
        val saved = HydrationLogPolicy.State(prefs.getString("saved_date", "").orEmpty(),
            prefs.getInt("intake_ml", 0), prefs.getInt("undo_ml", 0), prefs.getLong("entry_time", 0))
        state = HydrationLogPolicy.forDate(saved, today())
        goalMl = HydrationLogPolicy.goal(prefs.getInt("goal_ml", 2_000))
        if (state != saved) saveData()
    }

    private fun saveData() {
        prefs.edit().putString("saved_date", state.date).putInt("intake_ml", state.amountMl)
            .putInt("undo_ml", state.undoAmountMl).putLong("entry_time", state.capturedAt).apply()
        // Sync to Phone App
        try {
            val payload = HydrationPayload(state.date, state.amountMl, goalMl, state.capturedAt)
            BluetoothClientService.sendRawMsg(SyncMessage(MessageType.HYDRATION_UPDATE, payload = Gson().toJson(payload)))
        } catch (_: Exception) {}
    }

    private fun record(ml: Int) {
        state = HydrationLogPolicy.record(state, today(), ml, System.currentTimeMillis())
        saveData()
        refresh()
        amount.announceForAccessibility("Logged $ml millilitres. ${state.amountMl} millilitres today")
    }

    private fun changeGoal(delta: Int) {
        goalMl = HydrationLogPolicy.goal(goalMl + delta)
        prefs.edit().putInt("goal_ml", goalMl).apply()
        refresh()
        try {
            val payload = HydrationPayload(state.date, state.amountMl, goalMl, System.currentTimeMillis())
            BluetoothClientService.sendRawMsg(SyncMessage(MessageType.HYDRATION_UPDATE, payload = Gson().toJson(payload)))
        } catch (_: Exception) {}
    }

    private fun refresh() {
        if (!::amount.isInitialized) return
        val format = NumberFormat.getIntegerInstance()
        amount.text = "${format.format(state.amountMl)} ml"
        val percentage = (state.amountMl.toDouble() * 100 / goalMl).roundToInt()
        progress.text = "Goal ${format.format(goalMl)} ml · $percentage%"
        lastLog.text = when {
            state.capturedAt > 0 -> "Last logged ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.capturedAt))}"
            state.amountMl == 0 -> "No water logged today"
            else -> "Today's manually logged total"
        }
        undo.isEnabled = state.undoAmountMl > 0
        undo.text = if (state.undoAmountMl > 0) "Undo last ${state.undoAmountMl} ml" else "No entry to undo"
        wave.setProgress(state.amountMl.toFloat() / goalMl)
    }

    private class HydrationWaveView(context: Context) : View(context) {
        private var progress = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clip = Path()
        private val water = Path()
        fun setProgress(value: Float) { progress = value.coerceIn(0f, 1f); invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            val cx = width / 2f; val cy = height / 2f
            val radius = (minOf(cx, cy) - 4f * resources.displayMetrics.density).coerceAtLeast(0f)
            clip.reset(); clip.addCircle(cx, cy, radius, Path.Direction.CW)
            canvas.save(); canvas.clipPath(clip)
            paint.style = Paint.Style.FILL; paint.color = 0xFF102027.toInt()
            canvas.drawCircle(cx, cy, radius, paint)
            // Empty and full logs have flat levels, so the decorative wave cannot imply intake.
            val level = cy + radius - 2f * radius * progress
            val amplitude = if (progress > 0f && progress < 1f) 3f * resources.displayMetrics.density else 0f
            water.reset(); water.moveTo(0f, height.toFloat()); water.lineTo(0f, level)
            for (x in 0..width step 4) water.lineTo(x.toFloat(), level + (sin(x.toFloat() / width * 2 * Math.PI) * amplitude).toFloat())
            water.lineTo(width.toFloat(), height.toFloat()); water.close()
            paint.color = 0xAA00B0FF.toInt(); canvas.drawPath(water, paint)
            canvas.restore()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * resources.displayMetrics.density
            paint.color = 0xFF00B0FF.toInt(); canvas.drawCircle(cx, cy, radius, paint)
        }
    }
}

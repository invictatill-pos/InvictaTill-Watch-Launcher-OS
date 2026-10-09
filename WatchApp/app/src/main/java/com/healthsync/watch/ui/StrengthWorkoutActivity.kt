package com.healthsync.watch.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.healthsync.watch.R
import com.healthsync.watch.sensor.HeartRatePolicy
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.service.WorkoutTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class StrengthWorkoutActivity : AppCompatActivity() {
    private lateinit var tvSetInfo: TextView
    private lateinit var tvReps: TextView
    private lateinit var tvLiveHr: TextView
    private lateinit var tvRestTimer: TextView
    private lateinit var tvTotalReps: TextView
    private lateinit var btnRest: Button
    private var currentSet = 1
    private var totalReps = 0
    private var setStartReps = 0
    private var restDeadline = 0L
    private var workoutStartMs = 0L
    private var durationMs = 0L
    private var calories = 0.0
    private var ending = false
    private var paused = false
    private val resting: Boolean get() = restDeadline > 0L
    private val uiPrefs by lazy { getSharedPreferences("watch_strength_ui", MODE_PRIVATE) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WorkoutTrackingService.ACTION_UPDATE || ending) return
            totalReps = intent.getIntExtra("reps", totalReps).coerceAtLeast(0)
            currentSet = intent.getIntExtra("sets", currentSet).coerceAtLeast(1)
            durationMs = intent.getLongExtra(WorkoutTrackingService.EXTRA_DURATION, durationMs)
            calories = intent.getDoubleExtra("calories", calories)
            paused = intent.getBooleanExtra(WorkoutTrackingService.EXTRA_IS_PAUSED, paused)
            workoutStartMs = WorkoutTrackingService.activeStartTime.takeIf { it > 0 } ?: workoutStartMs
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_active_strength)
        tvSetInfo = findViewById(R.id.tvSetInfo)
        tvReps = findViewById(R.id.tvReps)
        tvLiveHr = findViewById(R.id.tvLiveHr)
        tvRestTimer = findViewById(R.id.tvRestTimer)
        tvTotalReps = findViewById(R.id.tvTotalReps)
        btnRest = findViewById(R.id.btnRest)
        workoutStartMs = WorkoutTrackingService.activeStartTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        if (WorkoutTrackingService.isActive && uiPrefs.getLong("session", 0) == workoutStartMs) {
            currentSet = uiPrefs.getInt("set", 1)
            setStartReps = uiPrefs.getInt("set_start_reps", 0)
            totalReps = uiPrefs.getInt("total_reps", 0)
            restDeadline = uiPrefs.getLong("rest_deadline", 0)
            // elapsedRealtime resets after a reboot.
            if (restDeadline > SystemClock.elapsedRealtime() + 60_000L) restDeadline = 0L
        }
        findViewById<View>(R.id.btnStopWorkout).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Finish strength?")
                .setMessage("Save $totalReps reps across $currentSet sets?")
                .setNegativeButton("Continue", null).setPositiveButton("Finish") { _, _ -> finishWorkout() }.show()
        }
        btnRest.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (resting) endRest() else if (paused) {
                paused = false
                command(WorkoutTrackingService.ACTION_RESUME)
                updateUi()
            } else {
                restDeadline = SystemClock.elapsedRealtime() + 60_000L
                paused = true
                command(WorkoutTrackingService.ACTION_PAUSE)
                saveUiState()
                updateUi()
            }
        }
        findViewById<View>(R.id.btnAddRep).setOnClickListener {
            if (!resting) adjustReps(1)
        }
        findViewById<View>(R.id.btnRemoveRep).setOnClickListener {
            if (!resting && totalReps > setStartReps) adjustReps(-1)
        }
        val sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        findViewById<TextView>(R.id.tvRepHint).text = if (sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null)
            "No motion sensor. Use + / − to count reps." else "Motion counts reps. Use + / − to correct."
        if (!WorkoutTrackingService.isActive) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, WorkoutTrackingService::class.java).apply {
                    action = WorkoutTrackingService.ACTION_START
                    putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, "Home Workout")
                })
            }.onFailure {
                Toast.makeText(this, "Unable to start workout. Check sensor permissions.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (resting && SystemClock.elapsedRealtime() >= restDeadline) endRest()
                    updateUi()
                    delay(500L)
                }
            }
        }
        updateUi()
    }

    private fun command(action: String) {
        startService(Intent(this, WorkoutTrackingService::class.java).setAction(action))
    }

    private fun adjustReps(delta: Int) {
        totalReps = (totalReps + delta).coerceAtLeast(setStartReps)
        findViewById<View>(R.id.tvReps).performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        sendReps()
        updateUi()
    }

    private fun sendReps() {
        startService(Intent(this, WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_SET_REPS
            putExtra(WorkoutTrackingService.EXTRA_REPS, totalReps)
            putExtra(WorkoutTrackingService.EXTRA_SETS, currentSet)
        })
        saveUiState()
    }

    private fun endRest() {
        restDeadline = 0L
        paused = false
        setStartReps = totalReps
        currentSet++
        command(WorkoutTrackingService.ACTION_RESUME)
        sendReps()
        tvReps.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        updateUi()
    }

    private fun updateUi() {
        tvSetInfo.text = "SET $currentSet"
        tvReps.text = (totalReps - setStartReps).coerceAtLeast(0).toString()
        tvTotalReps.text = "$totalReps total reps"
        val nowMs = System.currentTimeMillis()
        val heart = SensorCollectorService.watchFaceHeartRate(nowMs)
        val unit = if (heart.bpm > 0) HeartRatePolicy.complicationUnit(heart.capturedAt, nowMs) else "BPM"
        val label = if (unit == "BPM") "bpm" else unit
        tvLiveHr.text = if (heart.bpm > 0) "♥ ${heart.bpm} $label" else "♥ -- bpm"
        tvLiveHr.contentDescription = if (heart.bpm > 0) {
            "${heart.bpm} beats per minute${if (heart.sensorReading) ", sensor reading" else ""}. " +
                HeartRatePolicy.ageLabel(heart.capturedAt, nowMs)
        } else "Heart rate unavailable"
        if (resting) {
            val sec = ((restDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999L) / 1000
            tvRestTimer.text = String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60)
            tvRestTimer.setTextColor(0xFFFFBC6A.toInt())
            btnRest.text = "SKIP REST"
        } else {
            tvRestTimer.text = if (paused) "Paused" else "Active"
            tvRestTimer.setTextColor(0xFF8CE7C8.toInt())
            btnRest.text = if (paused) "RESUME WORKOUT" else "REST 60 SEC"
        }
        findViewById<View>(R.id.btnAddRep).isEnabled = !resting && !paused
        findViewById<View>(R.id.btnRemoveRep).isEnabled = !resting && !paused && totalReps > setStartReps
    }

    private fun saveUiState() {
        uiPrefs.edit().putLong("session", workoutStartMs).putInt("set", currentSet)
            .putInt("set_start_reps", setStartReps).putInt("total_reps", totalReps)
            .putLong("rest_deadline", restDeadline).apply()
    }

    private fun finishWorkout() {
        if (ending) return
        ending = true
        command(WorkoutTrackingService.ACTION_STOP)
        startActivity(Intent(this, WorkoutSummaryActivity::class.java).apply {
            putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, "Home Workout")
            putExtra(WorkoutSummaryActivity.EXTRA_REPS, totalReps)
            putExtra(WorkoutSummaryActivity.EXTRA_SETS, currentSet)
            putExtra("summary_duration_ms", durationMs)
            putExtra("summary_calories", calories)
            putExtra("summary_hr", SensorCollectorService.freshBpm())
            putExtra("summary_start_ms", workoutStartMs)
            putExtra("summary_await_save", true)
        })
        uiPrefs.edit().clear().apply()
        finish()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, receiver, IntentFilter(WorkoutTrackingService.ACTION_UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (WorkoutTrackingService.isActive) command(WorkoutTrackingService.ACTION_REQUEST_UPDATE)
    }

    override fun onPause() {
        if (!ending) saveUiState()
        runCatching { unregisterReceiver(receiver) }
        super.onPause()
    }
}

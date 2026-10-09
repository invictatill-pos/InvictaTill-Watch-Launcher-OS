package com.healthsync.watch.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.healthsync.watch.R
import com.healthsync.watch.data.WorkoutPayload
import com.healthsync.watch.data.WorkoutSessionPayload
import com.healthsync.watch.service.WorkoutTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class WorkoutSummaryActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_REPS = "summary_reps"
        const val EXTRA_SETS = "summary_sets"
    }
    private var expectedStart = 0L
    private var awaitingSave = false
    private var saveFailed = false
    private var loadJob: Job? = null
    private lateinit var tvStatus: TextView
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!awaitingSave) return
            if (intent?.action == WorkoutTrackingService.ACTION_SAVE_FAILED) {
                saveFailed = true
                tvStatus.text = "Save failed. Return to your session and try again."
                findViewById<Button>(R.id.btnSaveSummary).text = "RETURN TO WORKOUT"
                return
            }
            val payload = runCatching { Gson().fromJson(intent?.getStringExtra("payload_json"), WorkoutPayload::class.java) }.getOrNull()
            if (payload != null && (expectedStart == 0L || expectedStart == payload.startTime)) loadSavedSession()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_workout_summary)
        tvStatus = findViewById(R.id.tvSummaryStatus)
        expectedStart = intent.getLongExtra("summary_start_ms", 0L)
        awaitingSave = intent.getBooleanExtra("summary_await_save", false)
        tvStatus.text = if (awaitingSave) "Saving your session…" else "Session details"
        findViewById<Button>(R.id.btnSaveSummary).setOnClickListener {
            if (saveFailed || awaitingSave && WorkoutTrackingService.isActive) {
                val type = WorkoutTrackingService.activeActivityType
                val screen = if (type.contains("Home", true) || type.contains("Strength", true))
                    StrengthWorkoutActivity::class.java else ActiveWorkoutActivity::class.java
                startActivity(Intent(this, screen).putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, type))
            }
            finish()
        }
        val duration = intent.getLongExtra("summary_duration_ms", -1L)
        if (duration >= 0L) {
            display(
                type = intent.getStringExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE) ?: "Workout",
                start = expectedStart, durationSec = duration / 1000,
                distance = intent.getFloatExtra("summary_distance_m", 0f),
                hr = intent.getIntExtra("summary_hr", 0), calories = intent.getDoubleExtra("summary_calories", 0.0),
                steps = intent.getIntExtra("summary_steps", 0),
                reps = intent.getIntExtra(EXTRA_REPS, 0).takeIf { it > 0 }
                    ?: (intent.getStringExtra("summary_metric1")?.takeIf { it.contains("rep", true) || it.contains("swing", true) }
                        ?.filter { it.isDigit() }?.toIntOrNull() ?: 0)
            )
        }
        loadSavedSession()
    }

    private fun loadSavedSession() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            // Also handles a save that finished before this activity registered its receiver.
            repeat(if (awaitingSave) 6 else 1) { attempt ->
                if (attempt > 0) delay(500L)
                val session = withContext(Dispatchers.IO) {
                    val items = WorkoutHistoryStore.read(applicationContext)
                    if (expectedStart > 0) items.firstOrNull { it.start_time == expectedStart } else items.firstOrNull()
                }
                if (session != null) {
                    displaySession(session)
                    awaitingSave = false
                    saveFailed = false
                    tvStatus.text = "Saved on watch"
                    findViewById<Button>(R.id.btnSaveSummary).text = "DONE"
                    return@launch
                }
                if (saveFailed) return@launch
            }
            if (!awaitingSave) tvStatus.text = "No saved session found"
            else if (WorkoutTrackingService.isActive) {
                saveFailed = true
                tvStatus.text = "Session is still active. Return and try saving again."
                findViewById<Button>(R.id.btnSaveSummary).text = "RETURN TO WORKOUT"
            }
        }
    }

    private fun displaySession(session: WorkoutSessionPayload) {
        val pts = runCatching {
            Gson().fromJson<List<com.healthsync.watch.data.LatLngPoint>>(session.route_json,
                object : com.google.gson.reflect.TypeToken<List<com.healthsync.watch.data.LatLngPoint>>() {}.type)?.size ?: 0
        }.getOrDefault(0)
        display(session.activity_type, session.start_time, session.duration_sec.toLong(),
            session.distance_m, session.avg_hr, session.calories, session.step_count, session.swings, pts)
    }

    private fun display(type: String, start: Long, durationSec: Long, distance: Float, hr: Int, calories: Double, steps: Int, reps: Int, routePoints: Int = intent.getIntExtra("summary_route_count", 0)) {
        val normalized = type.lowercase(Locale.ROOT)
        val seconds = durationSec.coerceAtLeast(0)
        findViewById<TextView>(R.id.tvSummaryType).text = type.replace("_", " ")
            .lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        findViewById<TextView>(R.id.tvSummaryDate).text = if (start > 0)
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(start)) else ""
        findViewById<TextView>(R.id.tvStatDuration).text = if (seconds >= 3600)
            String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
        findViewById<TextView>(R.id.tvStatCal).text = String.format(Locale.getDefault(), "%.0f kcal", calories.coerceAtLeast(0.0))
        findViewById<TextView>(R.id.tvStatHr).text = if (hr > 0) "$hr bpm" else "-- bpm"
        val strength = normalized.contains("home") || normalized.contains("strength")
        val swings = normalized.contains("badminton") || normalized.contains("cricket") || normalized.contains("basketball")
        findViewById<TextView>(R.id.tvStatStepsLabel).text = when {
            strength -> "Total repetitions"
            normalized.contains("basketball") -> "Detected movements"
            swings -> "Detected swings"
            else -> "Steps"
        }
        findViewById<TextView>(R.id.tvSummaryStats).text = if (strength || swings) reps.toString() else steps.coerceAtLeast(0).toString()
        val sets = intent.getIntExtra(EXTRA_SETS, 0)
        val detail = when {
            strength && sets > 0 -> "$sets sets completed"
            distance > 0f -> {
                val km = distance / 1000.0
                val base = String.format(Locale.getDefault(), "%.2f km", km)
                if (normalized.contains("run") || normalized.contains("walk")) {
                    val paceSeconds = (seconds / km).toLong()
                    "$base  •  ${paceSeconds / 60}:${(paceSeconds % 60).toString().padStart(2, '0')} /km"
                } else base
            }
            else -> ""
        }
        val count = if (routePoints > 0) routePoints else intent.getIntExtra("summary_route_count", 0)
        val routeSuffix = if (count > 0) "\n✓ $count route points recorded" else ""
        findViewById<TextView>(R.id.tvSummaryPrimary).text = detail + routeSuffix
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction("com.healthsync.watch.WORKOUT_SAVED")
            addAction(WorkoutTrackingService.ACTION_SAVE_FAILED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (awaitingSave) loadSavedSession()
    }

    override fun onPause() {
        runCatching { unregisterReceiver(receiver) }
        super.onPause()
    }
}

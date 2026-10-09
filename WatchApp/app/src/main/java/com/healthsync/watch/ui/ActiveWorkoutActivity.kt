package com.healthsync.watch.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.healthsync.watch.R
import com.healthsync.watch.algorithm.WorkoutLocationState
import com.healthsync.watch.algorithm.workoutUsesLocation
import com.healthsync.watch.algorithm.workoutUsesStepDistance
import com.healthsync.watch.sensor.HeartRatePolicy
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.service.WorkoutTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class ActiveWorkoutActivity : AppCompatActivity() {
    private lateinit var tvDuration: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvMainDistance: TextView
    private lateinit var tvLiveHr: TextView
    private lateinit var tvLiveHrUnit: TextView
    private lateinit var tvLiveKcal: TextView
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvMetric1: TextView
    private lateinit var tvMetric1Label: TextView
    private lateinit var tvPauseLabel: TextView
    private lateinit var tvWorkoutState: TextView
    private lateinit var tvMetric2: TextView
    private lateinit var layoutControls: LinearLayout
    private lateinit var btnPauseResumeWorkout: ImageButton
    private lateinit var btnWorkoutControls: View

    private var activityType = "Walk"
    private var paused = false
    private var ending = false
    private var durationMs = 0L
    private var distanceM = 0f
    private var calories = 0.0
    private var steps = 0
    private var metric1 = ""
    private var metric2 = ""
    // Keep the summary fallback separate from the current sensor value displayed below.
    private var heartRate = 0
    private var workoutStartMs = 0L
    private var routePointsCount = 0
    private var gpsLocked = false
    private var gpsState = WorkoutLocationState.SEARCHING
    private var gpsAccuracy = -1f
    private var locationPermissionChecked = false
    private var locationPermissionPending = false
    private var workoutStartRequested = false
    private val locationPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationPermissionPending = false
        startOrRefreshWorkout()
        updateGpsStatus()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            if (intent.action == WorkoutTrackingService.ACTION_GPS_STATUS) {
                gpsLocked = intent.getBooleanExtra(WorkoutTrackingService.EXTRA_GPS_LOCKED, false)
                gpsState = runCatching { WorkoutLocationState.valueOf(intent.getStringExtra(WorkoutTrackingService.EXTRA_GPS_STATE).orEmpty()) }
                    .getOrDefault(if (gpsLocked) WorkoutLocationState.GPS_FIX else WorkoutLocationState.SEARCHING)
                gpsAccuracy = intent.getFloatExtra(WorkoutTrackingService.EXTRA_GPS_ACCURACY, -1f)
                routePointsCount = intent.getIntExtra(WorkoutTrackingService.EXTRA_ROUTE_COUNT, routePointsCount)
                updateGpsStatus()
                return
            }
            if (intent.action != WorkoutTrackingService.ACTION_UPDATE || ending) return
            paused = intent.getBooleanExtra(WorkoutTrackingService.EXTRA_IS_PAUSED, paused)
            durationMs = intent.getLongExtra(WorkoutTrackingService.EXTRA_DURATION, durationMs).coerceAtLeast(0)
            distanceM = intent.getFloatExtra(WorkoutTrackingService.EXTRA_DISTANCE, distanceM).coerceAtLeast(0f)
            metric1 = intent.getStringExtra(WorkoutTrackingService.EXTRA_METRIC_1) ?: metric1
            metric2 = intent.getStringExtra(WorkoutTrackingService.EXTRA_METRIC_2) ?: metric2
            calories = intent.getDoubleExtra("calories", calories).coerceAtLeast(0.0)
            steps = intent.getIntExtra("steps", steps).coerceAtLeast(0)
            heartRate = intent.getIntExtra("bpm", SensorCollectorService.freshBpm())
            workoutStartMs = WorkoutTrackingService.activeStartTime.takeIf { it > 0 } ?: workoutStartMs
            routePointsCount = intent.getIntExtra(WorkoutTrackingService.EXTRA_ROUTE_COUNT, routePointsCount)
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Older builds saved a headless Wear ambient fragment. Restoring that
        // fragment can load unavailable Wear classes on plain Android watches.
        // The workout service owns the session; start_ms is restored below.
        super.onCreate(null)
        // Tracking continues in the foreground service when this screen is hidden.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_active_workout)
        tvDuration = findViewById(R.id.tvDuration)
        tvDistance = findViewById(R.id.tvDistance)
        tvMainDistance = findViewById(R.id.tvMainDistance)
        tvLiveHr = findViewById(R.id.tvLiveHr)
        tvLiveHrUnit = findViewById(R.id.tvLiveHrUnit)
        tvLiveKcal = findViewById(R.id.tvLiveKcal)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvMetric1 = findViewById(R.id.tvMetric1)
        tvMetric1Label = findViewById(R.id.tvMetric1Label)
        tvPauseLabel = findViewById(R.id.tvPauseLabel)
        tvWorkoutState = findViewById(R.id.tvWorkoutState)
        tvMetric2 = findViewById(R.id.tvMetric2)
        layoutControls = findViewById(R.id.layoutControls)
        btnPauseResumeWorkout = findViewById(R.id.btnPauseResumeWorkout)
        btnWorkoutControls = findViewById(R.id.btnWorkoutControls)

        activityType = if (WorkoutTrackingService.isActive) WorkoutTrackingService.activeActivityType
            else intent.getStringExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE) ?: "Walk"
        workoutStartMs = WorkoutTrackingService.activeStartTime.takeIf { it > 0 }
            ?: savedInstanceState?.getLong("start_ms") ?: System.currentTimeMillis()
        paused = WorkoutTrackingService.currentlyPaused
        findViewById<TextView>(R.id.tvActivityType).text = activityType.replace("_", " ")
        tvMetric1Label.text = when (activityType.lowercase(Locale.ROOT)) {
            "cycling" -> "SPEED"
            "badminton", "cricket" -> "SWINGS"
            "basketball" -> "MOVES"
            "yoga" -> "SESSION"
            else -> "PACE"
        }
        val outdoors = workoutUsesLocation(activityType)
        tvGpsStatus.visibility = if (outdoors) View.VISIBLE else View.GONE
        tvMainDistance.visibility = if (outdoors) View.VISIBLE else View.GONE
        locationPermissionChecked = savedInstanceState?.getBoolean("location_checked")
            ?: intent.getBooleanExtra(WorkoutTrackingService.EXTRA_LOCATION_CHECKED, false)
        gpsState = if (WorkoutTrackingService.isActive) WorkoutTrackingService.currentLocationState else WorkoutLocationState.SEARCHING
        tvGpsStatus.setOnClickListener { repairLocationTracking() }
        tvGpsStatus.isFocusable = outdoors
        btnWorkoutControls.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            layoutControls.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.btnCloseControls).setOnClickListener { layoutControls.visibility = View.GONE }
        btnPauseResumeWorkout.setOnClickListener { togglePause() }
        findViewById<View>(R.id.btnEndWorkout).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Finish workout?")
                .setMessage("Your session will be saved to history.")
                .setNegativeButton("Continue", null).setPositiveButton("Finish") { _, _ -> finishWorkout() }.show()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutControls.visibility == View.VISIBLE) layoutControls.visibility = View.GONE else finish()
            }
        })
        if (outdoors && !fineLocationGranted() && !locationPermissionChecked) requestLocationPermission()
        else startOrRefreshWorkout()
        updateUi()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    heartRate = SensorCollectorService.freshBpm()
                    updateCurrentHeartRate()
                    delay(1000L)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(WorkoutTrackingService.ACTION_UPDATE)
            addAction(WorkoutTrackingService.ACTION_GPS_STATUS)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        startOrRefreshWorkout()
    }

    override fun onPause() {
        runCatching { unregisterReceiver(receiver) }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("start_ms", workoutStartMs)
        outState.putBoolean("location_checked", locationPermissionChecked)
        super.onSaveInstanceState(outState)
    }

    private fun updateUi() {
        val sec = durationMs / 1000
        tvDuration.text = if (sec >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
            else String.format(Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60)
        tvMainDistance.text = String.format(Locale.getDefault(), "%.2f km", distanceM / 1000f)
        tvDistance.text = steps.toString()
        tvLiveKcal.text = String.format(Locale.getDefault(), "%.0f", calories)
        tvMetric1.text = metric1.ifBlank { "--" }
        tvMetric2.text = metric2
        updateCurrentHeartRate()
        tvWorkoutState.text = if (paused) "PAUSED" else "TRACKING"
        tvWorkoutState.setTextColor(if (paused) 0xFFFFBC6A.toInt() else 0xFF8CE7C8.toInt())
        tvDuration.alpha = if (paused) 0.6f else 1f
        btnPauseResumeWorkout.setImageResource(if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
        btnPauseResumeWorkout.contentDescription = if (paused) "Resume workout" else "Pause workout"
        tvPauseLabel.text = if (paused) "RESUME" else "PAUSE"

        updateGpsStatus()
    }

    private fun updateCurrentHeartRate() {
        val nowMs = System.currentTimeMillis()
        val reading = SensorCollectorService.watchFaceHeartRate(nowMs)
        tvLiveHr.text = if (reading.bpm > 0) reading.bpm.toString() else "--"
        val unit = if (reading.bpm > 0) HeartRatePolicy.complicationUnit(reading.capturedAt, nowMs) else "BPM"
        tvLiveHrUnit.text = if (unit == "BPM") "♥ bpm" else "♥ $unit"
        tvLiveHr.contentDescription = if (reading.bpm > 0) {
            "${reading.bpm} beats per minute${if (reading.sensorReading) ", sensor reading" else ""}. " +
                HeartRatePolicy.ageLabel(reading.capturedAt, nowMs)
        } else "Heart rate unavailable"
        // Run uses cadence here; badminton uses a recorded session peak. Other
        // activities use this secondary line for the same current HR display.
        if (activityType.lowercase(Locale.ROOT) !in listOf("run", "running", "badminton")) {
            tvMetric2.text = if (reading.bpm > 0) "${reading.bpm} $unit" else "-- BPM"
            tvMetric2.contentDescription = tvLiveHr.contentDescription
        }
    }

    private fun updateGpsStatus() {
        if (tvGpsStatus.visibility != View.VISIBLE) return
        val state = when {
            paused -> WorkoutLocationState.PAUSED
            !fineLocationGranted() -> if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                WorkoutLocationState.PRECISE_REQUIRED else WorkoutLocationState.PERMISSION_REQUIRED
            else -> gpsState
        }
        val estimate = if (workoutUsesStepDistance(activityType)) " • step estimate" else " • no route distance"
        val accuracy = gpsAccuracy.takeIf { it.isFinite() && it >= 0 }?.let { " ±${it.toInt()}m" }.orEmpty()
        tvGpsStatus.text = when (state) {
            WorkoutLocationState.GPS_FIX -> "GPS locked$accuracy • $routePointsCount pts"
            WorkoutLocationState.LOCATION_FIX -> "Location fix$accuracy • $routePointsCount pts"
            WorkoutLocationState.PAUSED -> "GPS paused • resume to record"
            WorkoutLocationState.PERMISSION_REQUIRED -> "Allow location • tap to fix"
            WorkoutLocationState.PRECISE_REQUIRED -> "Allow precise location • tap to fix"
            WorkoutLocationState.DISABLED -> "Location off • tap to enable"
            WorkoutLocationState.GPS_DISABLED -> "GPS off • tap to enable"
            WorkoutLocationState.NO_PROVIDER -> "Watch has no location provider$estimate"
            WorkoutLocationState.UNAVAILABLE -> "Location unavailable • tap to retry"
            WorkoutLocationState.STALE -> "GPS signal lost • go outdoors$estimate"
            else -> "GPS searching • go outdoors$estimate"
        }
        val fixed = state == WorkoutLocationState.GPS_FIX || state == WorkoutLocationState.LOCATION_FIX
        tvGpsStatus.setTextColor(if (fixed) 0xFF8CE7C8.toInt() else 0xFFFFBC6A.toInt())
        tvGpsStatus.contentDescription = tvGpsStatus.text.toString()
    }

    private fun fineLocationGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        locationPermissionChecked = true
        locationPermissionPending = true
        locationPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun startOrRefreshWorkout() {
        if (locationPermissionPending || ending) return
        if (WorkoutTrackingService.isActive) {
            runCatching { startService(Intent(this, WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_REQUEST_UPDATE)) }
        } else if (!workoutStartRequested) {
            workoutStartRequested = true
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, WorkoutTrackingService::class.java)
                    .setAction(WorkoutTrackingService.ACTION_START).putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, activityType))
            }.onFailure {
                workoutStartRequested = false
                Toast.makeText(this, "Unable to start workout. Check sensor and location permissions.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun repairLocationTracking() {
        if (paused) { Toast.makeText(this, "Resume your workout to record a route.", Toast.LENGTH_SHORT).show(); return }
        if (!fineLocationGranted()) {
            if (locationPermissionChecked && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                    .onFailure { Toast.makeText(this, "Enable precise location in Android app settings.", Toast.LENGTH_LONG).show() }
            } else requestLocationPermission()
        } else if (gpsState == WorkoutLocationState.DISABLED || gpsState == WorkoutLocationState.GPS_DISABLED) {
            runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .onFailure { Toast.makeText(this, "Enable Location in Android settings.", Toast.LENGTH_LONG).show() }
        } else {
            startOrRefreshWorkout()
            Toast.makeText(this, if (gpsState == WorkoutLocationState.NO_PROVIDER) "This watch does not expose a location provider."
                else "Move outdoors and keep the watch clear of obstructions while GPS finds a fix.", Toast.LENGTH_LONG).show()
        }
    }

    private fun togglePause() {
        if (ending) return
        startService(Intent(this, WorkoutTrackingService::class.java).setAction(
            if (paused) WorkoutTrackingService.ACTION_RESUME else WorkoutTrackingService.ACTION_PAUSE))
        paused = !paused
        updateUi()
        layoutControls.visibility = View.GONE
    }

    private fun finishWorkout() {
        if (ending) return
        ending = true
        startService(Intent(this, WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_STOP))
        startActivity(Intent(this, WorkoutSummaryActivity::class.java).apply {
            putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, activityType)
            putExtra("summary_duration_ms", durationMs)
            putExtra("summary_distance_m", distanceM)
            putExtra("summary_metric1", metric1)
            putExtra("summary_metric2", metric2)
            putExtra("summary_hr", heartRate)
            putExtra("summary_calories", calories)
            putExtra("summary_steps", steps)
            putExtra("summary_start_ms", workoutStartMs)
            putExtra("summary_route_count", routePointsCount)
            putExtra("summary_await_save", true)
        })
        finish()
    }
}

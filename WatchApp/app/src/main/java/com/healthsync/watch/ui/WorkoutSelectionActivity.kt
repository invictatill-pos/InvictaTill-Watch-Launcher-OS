package com.healthsync.watch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.healthsync.watch.R
import com.healthsync.watch.algorithm.workoutUsesLocation
import com.healthsync.watch.algorithm.workoutUsesStepDistance
import com.healthsync.watch.service.WorkoutTrackingService

class WorkoutSelectionActivity : AppCompatActivity() {
    private var pendingWorkoutType: String? = null
    private var launching = false
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val type = pendingWorkoutType
        pendingWorkoutType = null
        if (type != null) {
            if (needsGps(type) && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, if (workoutUsesStepDistance(type)) "Precise location not granted. Distance uses step estimates; no GPS route."
                    else "Precise location not granted. This workout will not record a GPS route or distance.", Toast.LENGTH_LONG).show()
            }
            launchWorkout(type)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_selection)
        val scroll = findViewById<RoundScrollView>(R.id.workoutScroll)
        val content = findViewById<LinearLayout>(R.id.workoutList)
        // Some standalone Android watches report a square screen even with a circular bezel.
        // Keep the list inside an inscribed circle without relying on isScreenRound.
        scroll.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            val diameter = minOf(width, height)
            val horizontalInset = ((width - diameter) / 2f + diameter * 0.16f).toInt()
            val verticalInset = ((height - diameter) / 2f + diameter * 0.18f).toInt()
            if (scroll.paddingLeft != horizontalInset || scroll.paddingRight != horizontalInset) {
                scroll.setPadding(horizontalInset, 0, horizontalInset, 0)
            }
            if (content.paddingTop != verticalInset || content.paddingBottom != verticalInset) {
                content.setPadding(0, verticalInset, 0, verticalInset)
            }
        }
        pendingWorkoutType = savedInstanceState?.getString("pending_workout")
        listOf(R.id.btnSportWalk to "Walk", R.id.btnSportRun to "Run",
            R.id.btnSportCycling to "Cycling", R.id.btnSportBasketball to "Basketball",
            R.id.btnSportStrength to "Home Workout", R.id.btnSportBadminton to "Badminton",
            R.id.btnSportCricket to "Cricket", R.id.btnSportYoga to "Yoga").forEach { (id, type) ->
            findViewById<View>(id).apply {
                contentDescription = "Start $type workout"
                setOnClickListener {
                    if (!launching && pendingWorkoutType == null) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        startWorkout(type)
                    }
                }
            }
        }
    }

    private fun needsGps(type: String) = workoutUsesLocation(type)

    override fun onResume() {
        super.onResume()
        useWatchViewport()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) useWatchViewport()
    }

    @Suppress("DEPRECATION")
    private fun useWatchViewport() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun startWorkout(type: String) {
        if (WorkoutTrackingService.isActive) {
            launchWorkout(WorkoutTrackingService.activeActivityType)
            return
        }
        val permissions = mutableListOf<String>()
        fun addIfMissing(permission: String) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) permissions += permission
        }
        if (needsGps(type)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Android 12 ignores fine-only upgrade requests, even when coarse is already granted.
                permissions += Manifest.permission.ACCESS_FINE_LOCATION
                permissions += Manifest.permission.ACCESS_COARSE_LOCATION
            }
        }
        addIfMissing(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) addIfMissing(Manifest.permission.ACTIVITY_RECOGNITION)
        if (permissions.isEmpty()) launchWorkout(type) else {
            pendingWorkoutType = type
            permLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun launchWorkout(type: String) {
        if (launching) return
        launching = true
        val target = if (type.contains("Home", true) || type.contains("Strength", true))
            StrengthWorkoutActivity::class.java else ActiveWorkoutActivity::class.java
        startActivity(Intent(this, target).putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, type)
            .putExtra(WorkoutTrackingService.EXTRA_LOCATION_CHECKED, true))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_workout", pendingWorkoutType)
        super.onSaveInstanceState(outState)
    }
}

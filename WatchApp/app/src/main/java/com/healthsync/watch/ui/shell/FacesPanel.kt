package com.healthsync.watch.ui.shell

import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.healthsync.watch.data.WatchPreferences
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.service.WorkoutTrackingService
import com.healthsync.watch.ui.CasioWatchFaceView
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.WatchFaceCatalog
import com.healthsync.watch.ui.WatchOptionsActivity
import java.util.Calendar

class FacesPanel(private val activity: AppCompatActivity, private val onClose: () -> Unit, private val onSelected: () -> Unit) {
    private val prefs = WatchPreferences(activity)
    private val previews = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private val previewTick = object : Runnable {
        override fun run() {
            if (!resumed) return
            refreshPreviews()
            handler.postDelayed(this, 1_000L)
        }
    }
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val diameter = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        setPadding((diameter * .17f).toInt(), (diameter * .16f).toInt(), (diameter * .17f).toInt(), (diameter * .17f).toInt())
    }
    val view: View = RoundScrollView(activity).apply {
        setBackgroundColor(Color.BLACK)
        addView(content)
        isFillViewport = true
    }

    init {
        label("Watch faces", 23f)
        label("${WatchFaceCatalog.entries.size} faces · ${WatchFaceCatalog.ambientEntries.size} dim styles", 11f)

        for (entry in WatchFaceCatalog.entries) {
            val style = entry.id
            val preview: View? = when (style) {
                "orbit" -> OrbitWatchFaceView(activity).apply { setStyle("orbit") }
                "classic" -> CasioWatchFaceView(activity)
                else -> null
            }
            if (preview != null) {
                preview.isClickable = false
                preview.isFocusable = false
                preview.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                previews.add(preview)
                val size = (minOf(activity.resources.displayMetrics.widthPixels, activity.resources.displayMetrics.heightPixels) * .59f).toInt()
                content.addView(PreviewFrame(activity).apply {
                    contentDescription = "Select ${entry.title}. ${entry.description}"
                    isClickable = true
                    isFocusable = true
                    addView(preview, FrameLayout.LayoutParams(-1, -1))
                    setOnClickListener { choose(style) }
                }, LinearLayout.LayoutParams(size, size).apply { bottomMargin = dp(6) })
            }
            button(entry.title + if (prefs.watchFaceStyle == style) "  ✓" else "") { choose(style) }
            label(entry.description, 10f)
        }
        refreshPreviews()
        button("Display & AOD") {
            activity.startActivity(Intent(activity, WatchOptionsActivity::class.java).putExtra(WatchOptionsActivity.EXTRA_SCREEN, WatchOptionsActivity.SCREEN_DISPLAY))
        }
        button("Back") { onClose() }
    }

    private fun choose(style: String) {
        prefs.watchFaceStyle = style
        onSelected()
    }

    private fun label(value: String, size: Float) {
        content.addView(TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(10))
        })
    }

    private fun button(value: String, action: () -> Unit) {
        content.addView(TextView(activity).apply {
            text = value
            textSize = 15f
            setTextColor(0xFFC4EBD6.toInt())
            gravity = Gravity.CENTER
            minimumHeight = dp(46)
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(23).toFloat()
                setColor(0xFF14221B.toInt())
            }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                action()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2).apply { bottomMargin = dp(10) })
    }

    private fun refreshPreviews() {
        val time = Calendar.getInstance()
        val steps = SensorCollectorService.latestSteps.coerceAtLeast(0)
        val heart = SensorCollectorService.watchFaceHeartRate(time.timeInMillis)
        val linked = BluetoothClientService.isConnected
        val battery = runCatching {
            (activity.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }.getOrDefault(-1)
        for (preview in previews) when (preview) {
            is OrbitWatchFaceView -> {
                preview.setHeartRateTime(heart.capturedAt)
                preview.setHeartRateUnverified(heart.sensorReading)
                preview.updateTime(time)
                preview.setData(steps, prefs.stepGoal, heart.bpm, battery, linked, 0, WorkoutTrackingService.isActive)
            }
            is CasioWatchFaceView -> {
                preview.setHeartRateTime(heart.capturedAt)
                preview.setHeartRateUnverified(heart.sensorReading)
                preview.updateTime(time)
                preview.setHealthData(heart.bpm, steps, prefs.stepGoal, 0, 0f)
                preview.setBluetoothConnected(linked)
                preview.setBatteryLevel(battery)
            }
        }
    }

    fun onResume() {
        resumed = true
        handler.removeCallbacks(previewTick)
        refreshPreviews()
        handler.postDelayed(previewTick, 1_000L)
    }

    fun onPause() {
        resumed = false
        handler.removeCallbacks(previewTick)
    }

    fun destroy() = onPause()
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private class PreviewFrame(context: Context) : FrameLayout(context) {
        override fun onInterceptTouchEvent(event: MotionEvent) = true
    }
}

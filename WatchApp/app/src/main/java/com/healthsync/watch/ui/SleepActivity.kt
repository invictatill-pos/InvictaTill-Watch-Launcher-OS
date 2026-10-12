package com.healthsync.watch.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.healthsync.watch.sensor.SleepTracker
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import java.text.DateFormat
import java.util.Date

/** Shows recorded actigraphy separately from measurements the watch cannot provide. */
class SleepActivity : LauncherPanelActivity() {
    private lateinit var duration: TextView
    private lateinit var recorded: TextView
    private lateinit var status: TextView
    private var registered = false
    private val updates = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = roundContent()
        navigationHeader(content, "Sleep & rest")
        val readingCard = card(content)
        label(readingCard, "OVERNIGHT REST", 10f, accent, bold = true)
        duration = label(readingCard, "—", 32f, Color.WHITE, bold = true)
        recorded = label(readingCard, "No recorded session", 11f)
        status = label(readingCard, "Wear the watch overnight to capture motion.", 12f)
        primaryAction(content, "Refresh recorded session") { refresh() }
        val details = card(content)
        label(details, "About this estimate", 13f, Color.WHITE, bold = true)
        label(details, "Captured watch motion estimates a rest interval. Stillness may include awake rest.")
        label(details, "Sleep stages & quality score are unavailable.", 11f)
        action(content, "Sensor settings") {
            launchFirst(listOf(Intent(this, WatchOptionsActivity::class.java)
                .putExtra(WatchOptionsActivity.EXTRA_SCREEN, WatchOptionsActivity.SCREEN_SENSORS)),
                "Sensor settings could not open.")
        }
        action(content, "App permissions") { openAppPermissions() }
        action(content, "Back to clock") { backToClock() }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        registered = runCatching {
            ContextCompat.registerReceiver(this, updates, IntentFilter("com.healthsync.watch.SLEEP_UPDATE"),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            true
        }.getOrDefault(false)
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onStop() {
        if (registered) runCatching { unregisterReceiver(updates) }
        registered = false
        super.onStop()
    }

    private fun refresh() {
        if (!::duration.isInitialized) return
        val tracker = SleepTracker(this)
        val payload = tracker.getLatestPayload()
        if (payload == null) {
            duration.text = "—"
            recorded.text = "No captured rest session"
            status.text = when {
                SensorCollectorService.restEstimateInProgress() -> "Rest estimate in progress · check after the interval ends"
                SensorCollectorService.restMonitoringActive() -> "Motion monitoring active · wear overnight for an estimate"
                else -> "Wear overnight with sensor access allowed. A result appears after a recorded rest interval ends."
            }
            return
        }
        val minutes = payload.estimatedMinutes.coerceAtLeast(0)
        duration.text = "${minutes / 60}h ${minutes % 60}m"
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        recorded.text = "${format.format(Date(payload.startTime))}\n${format.format(Date(payload.endTime))}"
        status.text = "Estimated from watch motion${if (SensorCollectorService.restEstimateInProgress()) "\nA new interval is recording" else ""}"
    }
}

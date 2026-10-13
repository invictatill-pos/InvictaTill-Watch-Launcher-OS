package com.healthsync.watch.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.healthsync.watch.algorithm.FallDetection
import com.healthsync.watch.algorithm.SignalProcessing
import com.healthsync.watch.algorithm.validWorkoutMotionSample
import com.healthsync.watch.data.*
import com.healthsync.watch.sensor.ForegroundPolicy
import com.healthsync.watch.sensor.HeartRatePolicy
import com.healthsync.watch.sensor.HeartRateSensor
import com.healthsync.watch.sensor.HeartRateAttemptDiagnostics
import com.healthsync.watch.sensor.HeartRatePreviewTracker
import com.healthsync.watch.sensor.HeartRateSensorPreview
import com.healthsync.watch.sensor.HeartRateFacePolicy
import com.healthsync.watch.sensor.HardwareStepCounterGuard
import com.healthsync.watch.sensor.OxygenCapturePolicy
import com.healthsync.watch.sensor.StepDetectorGuard
import com.healthsync.watch.sensor.bodySensorAccessStatus
import com.healthsync.watch.sensor.shouldSendActivityReminder
import com.healthsync.watch.sensor.observedActivityMonitoringDelta
import com.healthsync.watch.sensor.StepCapturePolicy
import com.healthsync.watch.timer.WatchTimerStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.os.Vibrator
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import com.healthsync.watch.sensor.SleepTracker
import com.healthsync.watch.sensor.HealthAppMetric
import com.healthsync.watch.sensor.HealthAppReading
import com.healthsync.watch.sensor.InstalledHealthAppFallback
import com.healthsync.watch.sensor.SensorCandidateRetryPolicy
import com.healthsync.watch.sensor.WatchHealthSourcePolicy
import com.healthsync.watch.sensor.HeartRateFaceReading
import com.healthsync.watch.sensor.OxygenSensorPreview
import com.healthsync.watch.sensor.OxygenDisplayReading
import java.io.File

class SensorCollectorService : Service() {
    companion object {
        private const val TAG = "SensorCollector"
        private const val CHANNEL_ID = "sensor_channel"
        private const val NOTIF_ID = 2002
        private const val MEASURE_WINDOW_MS = 30_000L
        const val HR_MEASURE_WINDOW_MS = 60_000L
        const val ACTION_SETTINGS_UPDATED = "com.healthsync.watch.SETTINGS_UPDATED"
        const val ACTION_FORCE_MEASURE_HR = "com.healthsync.watch.FORCE_MEASURE_HR"
        const val ACTION_FORCE_MEASURE_SPO2 = "com.healthsync.watch.FORCE_MEASURE_SPO2"
        private const val ACTION_CHECK_APP_HR = "com.healthsync.watch.CHECK_APP_HR"
        private const val ACTION_CHECK_APP_OXYGEN = "com.healthsync.watch.CHECK_APP_OXYGEN"
        const val ACTION_SYNC_HEART_RATE = "com.healthsync.watch.SYNC_HEART_RATE"
        const val ACTION_POWER_SAVE_UPDATED = "com.healthsync.watch.POWER_SAVE_UPDATED"
        private const val ACTION_AUTO_HR = "com.healthsync.watch.AUTO_HR"
        private const val ACTION_AUTO_OXYGEN = "com.healthsync.watch.AUTO_OXYGEN"
        private const val DIAGNOSTIC_LIMIT = 8_192
        @Volatile private var latestHrAttemptReport: String? = null

        private fun command(context: Context, action: String) {
            val appMetric = when (action) {
                ACTION_FORCE_MEASURE_HR, ACTION_CHECK_APP_HR -> HealthAppMetric.HEART_RATE
                ACTION_FORCE_MEASURE_SPO2, ACTION_CHECK_APP_OXYGEN -> HealthAppMetric.OXYGEN
                else -> null
            }
            if (appMetric != null && Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED &&
                bodySensorAccessStatus(context) != null) {
                restoreHealthCaptureCache(context)
                HealthAppImportService.start(context, appMetric)
                return
            }
            try { ContextCompat.startForegroundService(context, Intent(context, SensorCollectorService::class.java).setAction(action)) }
            catch (e: Exception) {
                hrStatus = "service_unavailable"
                val protocol = Gson()
                BluetoothClientService.sendRawMsg(SyncMessage(type = MessageType.HEART_RATE_STATUS,
                    payload = protocol.toJson(HeartRateStatusPayload(hrStatus, displayBpm(), latestBpmTimeMs,
                        heartRateDiagnosticReport(context)))), protocol)
                persistDiagnosticReport(context)
                context.sendBroadcast(Intent("com.healthsync.watch.HR_UPDATE").setPackage(context.packageName)
                    .putExtra("bpm", freshBpm()).putExtra("status", hrStatus).putExtra("reading_time", latestBpmTimeMs))
                Log.w(TAG, "Sensor command could not start: ${e.message}")
            }
        }
        fun notifySettingsUpdated(context: Context) = command(context, ACTION_SETTINGS_UPDATED)
        fun notifyPowerSaveChanged(context: Context) = command(context, ACTION_POWER_SAVE_UPDATED)
        fun forceMeasureHr(context: Context) = command(context, ACTION_FORCE_MEASURE_HR)
        fun forceMeasureSpO2(context: Context) = command(context, ACTION_FORCE_MEASURE_SPO2)
        fun checkHealthAppHeartRate(context: Context) = command(context, ACTION_CHECK_APP_HR)
        fun checkHealthAppOxygen(context: Context) = command(context, ACTION_CHECK_APP_OXYGEN)
        fun syncHeartRateToPhone(context: Context) {
            if (Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED &&
                bodySensorAccessStatus(context) != null) {
                restoreHealthCaptureCache(context)
                if (displayBpm() > 0) {
                    sendImportedMsg(MessageType.HEART_RATE,
                        HeartRatePayload(latestBpm, latestAcc, latestHrSource, latestHrSourcePackage, latestHrSourceLabel), latestBpmTimeMs)
                    publishAppFallbackStatus(context, HealthAppMetric.HEART_RATE,
                        if (latestHrSource == WatchHealthSourcePolicy.HEALTH_APP) "app_reading" else "reading")
                }
                if (displaySpO2() > 0f) sendImportedMsg(MessageType.SPO2,
                    SpO2Payload(latestSpo2, latestOxygenSource, latestOxygenSourcePackage, latestOxygenSourceLabel), latestSpo2TimeMs)
            } else command(context, ACTION_SYNC_HEART_RATE)
        }

        @Volatile var latestBpm = -1
        @Volatile var latestAcc = 0
        @Volatile var latestBpmTimeMs = 0L
        @Volatile var latestHrSource = WatchHealthSourcePolicy.SENSOR; private set
        @Volatile var latestHrSourcePackage = ""; private set
        @Volatile var latestHrSourceLabel = ""; private set
        @Volatile var latestSteps = 0
        @Volatile var latestStepsCaptureTimeMs = 0L; private set
        @Volatile var stepCaptureSource = "NONE"; private set
        @Volatile var stepCaptureStatus = "starting"; private set
        @Volatile var stepSensorAvailable = false; private set
        @Volatile private var latestStepCaptureDay = ""
        @Volatile var latestLifetimeSteps = -1L
        @Volatile var latestSpo2 = -1f
        @Volatile var latestSpo2TimeMs = 0L
        @Volatile var latestOxygenSource = WatchHealthSourcePolicy.SENSOR; private set
        @Volatile var latestOxygenSourcePackage = ""; private set
        @Volatile var latestOxygenSourceLabel = ""; private set
        @Volatile private var hrAppFallbackMessage = ""
        @Volatile private var oxygenAppFallbackMessage = ""
        @Volatile var heartRateAvailable = false
        @Volatile var spo2Available = false
        @Volatile var spo2Status = "idle"
        @Volatile private var latestRestEstimateActive = false
        @Volatile private var latestMotionCapturedElapsed = -1L
        @Volatile var hrStatus = "idle"
        @Volatile private var latestSensorPreview: HeartRateSensorPreview? = null
        @Volatile private var latestOxygenPreview: OxygenSensorPreview? = null

        fun watchOxygenReading(nowMs: Long = System.currentTimeMillis()): OxygenDisplayReading {
            val recorded = displaySpO2(nowMs)
            val preview = latestOxygenPreview?.takeIf { it.display(nowMs) > 0f }
            if (preview == null) latestOxygenPreview = null
            if (preview != null && (recorded <= 0f || preview.capturedAt > latestSpo2TimeMs))
                return OxygenDisplayReading(preview.percentage, preview.capturedAt, unverified = true)
            return OxygenDisplayReading(recorded, if (recorded > 0f) latestSpo2TimeMs else 0L, unverified = false)
        }

        fun sensorPreview(nowMs: Long = System.currentTimeMillis()): HeartRateSensorPreview? {
            val preview = latestSensorPreview ?: return null
            if (preview.displayBpm(nowMs) > 0) return preview
            // Once expired/invalidated by clock changes, it cannot reappear after a rollback.
            if (latestSensorPreview === preview) latestSensorPreview = null
            return null
        }
        fun heartRateValueText(nowMs: Long = System.currentTimeMillis()): String {
            val display = watchFaceHeartRate(nowMs)
            if (display.sensorReading) return HeartRateSensorPreview(display.bpm, display.capturedAt).valueText(nowMs)
            return if (display.bpm > 0) "${display.bpm} bpm" else "— bpm"
        }
        fun watchFaceHeartRate(nowMs: Long = System.currentTimeMillis()): HeartRateFaceReading {
            val preview = sensorPreview(nowMs)
            if (latestHrSource == WatchHealthSourcePolicy.HEALTH_APP && displayBpm(nowMs) > 0 &&
                (preview == null || preview.capturedAt <= latestBpmTimeMs))
                return HeartRateFaceReading(latestBpm, latestBpmTimeMs, sensorReading = false)
            return HeartRateFacePolicy.select(latestBpm, latestAcc, latestBpmTimeMs, preview, nowMs)
        }

        fun freshBpm(nowMs: Long = System.currentTimeMillis()): Int =
            WatchHealthSourcePolicy.heartRate(latestBpm, latestAcc, latestBpmTimeMs, nowMs, HeartRatePolicy.FRESH_MS,
                latestHrSource, latestHrSourcePackage, latestHrSourceLabel)
        fun displayBpm(nowMs: Long = System.currentTimeMillis()): Int =
            WatchHealthSourcePolicy.heartRate(latestBpm, latestAcc, latestBpmTimeMs, nowMs, HeartRatePolicy.DISPLAY_MS,
                latestHrSource, latestHrSourcePackage, latestHrSourceLabel)
        fun freshSpO2(nowMs: Long = System.currentTimeMillis()): Float =
            WatchHealthSourcePolicy.oxygen(latestSpo2, latestSpo2TimeMs, nowMs, HeartRatePolicy.FRESH_MS,
                latestOxygenSource, latestOxygenSourcePackage, latestOxygenSourceLabel)
        fun displaySpO2(nowMs: Long = System.currentTimeMillis()): Float =
            WatchHealthSourcePolicy.oxygen(latestSpo2, latestSpo2TimeMs, nowMs, HeartRatePolicy.DISPLAY_MS,
                latestOxygenSource, latestOxygenSourcePackage, latestOxygenSourceLabel)
        fun hasCapturedStepsToday(nowMs: Long = System.currentTimeMillis()): Boolean =
            StepCapturePolicy.visibleCapture(StepCapturePolicy.capturedToday(latestStepsCaptureTimeMs,
                latestStepCaptureDay, SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(nowMs)),
                stepCaptureSource, nowMs), stepCaptureStatus)
        fun stepStatusText(context: Context): String = when (stepCaptureStatus) {
            "permission_required" -> "Allow Physical activity access in App permissions to record steps."
            "missing_sensor" -> "No compatible step or motion sensor is exposed by this watch."
            "monitoring_unavailable" -> "Step monitoring could not start. Open HealthSync and check App permissions."
            "captured" -> if (!hasCapturedStepsToday()) "No verified steps recorded for today yet." else when (stepCaptureSource) {
                "MOTION_ESTIMATE" -> "Recorded steps · motion estimate during monitoring"
                "MIXED_CAPTURE" -> "Recorded steps · sensor captures and motion estimates"
                else -> "Recorded steps · watch sensor during monitoring"
            }
            else -> "Waiting for a real step or motion sensor observation."
        }
        fun restMonitoringActive(nowElapsed: Long = SystemClock.elapsedRealtime()): Boolean =
            latestMotionCapturedElapsed >= 0L && nowElapsed - latestMotionCapturedElapsed in 0L..5_000L
        fun restEstimateInProgress(nowElapsed: Long = SystemClock.elapsedRealtime()): Boolean =
            latestRestEstimateActive && restMonitoringActive(nowElapsed)
        fun oxygenStatusText(context: Context): String = when {
            spo2Status == "checking_health_apps" -> "Checking readings shared by installed health apps…"
            watchOxygenReading().unverified -> "Unverified sensor · ${HeartRatePolicy.ageLabel(watchOxygenReading().capturedAt, System.currentTimeMillis())}"
            spo2Status == "app_reading" && displaySpO2() > 0f ->
                "From $latestOxygenSourceLabel · ${HeartRatePolicy.ageLabel(latestSpo2TimeMs, System.currentTimeMillis())}"
            oxygenAppFallbackMessage.isNotBlank() && spo2Status !in setOf("measuring", "received") -> oxygenAppFallbackMessage
            ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED ->
                "Allow sensor access in App permissions to measure oxygen."
            !spo2Available -> "This watch exposes no compatible oxygen saturation sensor. No reading can be captured."
            spo2Status == "background_permission_required" -> "Open HealthSync to measure, or allow all-time sensor access in App permissions for background readings."
            spo2Status == "measuring" -> "Measuring… Wear snugly and stay still."
            spo2Status == "timeout" -> "No valid oxygen reading was captured. Check wrist contact and try again."
            spo2Status == "sensor_error" -> "The watch could not start the oxygen sensor. Check sensor permissions."
            displaySpO2() > 0f -> HeartRatePolicy.ageLabel(latestSpo2TimeMs, System.currentTimeMillis())
            else -> "No sensor reading yet. Tap Measure and stay still."
        }
        fun heartRateAgeLabel(nowMs: Long = System.currentTimeMillis()) = HeartRatePolicy.ageLabel(latestBpmTimeMs, nowMs)
        fun hasHeartRateSensor(context: Context) = runCatching {
            HeartRateSensor.candidates(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).isNotEmpty()
        }.getOrDefault(false)
        fun heartRateDiagnosticReport(context: Context): String {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
            val candidates = runCatching {
                HeartRateSensor.candidates(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            }
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
            val attempt = latestHrAttemptReport ?: context.getSharedPreferences("HealthSync_Steps", Context.MODE_PRIVATE)
                .getString("last_hr_attempt_report", null) ?: "No heart rate acquisition has been requested yet."
            return buildString {
                appendLine("HealthSync heart rate diagnostic")
                appendLine("App: $version; package: ${context.packageName}")
                appendLine("Watch: ${Build.MANUFACTURER} ${Build.MODEL}; device: ${Build.DEVICE}; Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}")
                appendLine("BODY_SENSORS: ${if (granted) "granted" else "denied"}; current status: $hrStatus")
                val interval = WatchPreferences(context).hrIntervalMs
                appendLine("Automatic HR interval: ${if (interval <= 0L) "off (manual only)" else "${interval / 1_000L} seconds"}; each request stops after one capture or timeout")
                appendLine("Currently exposed compatible HR sensors: ${candidates.getOrNull()?.size ?: "discovery failed"}")
                candidates.getOrNull()?.take(4)?.forEach { appendLine("  ${sensorDescription(it).take(300)}") }
                if (candidates.isFailure) appendLine("  ${candidates.exceptionOrNull()?.javaClass?.simpleName}")
                appendLine("Accuracy: -1=no contact, 0=unreliable, 1..3=usable. Only validated BPM sensor frames with usable accuracy are recorded.")
                appendLine("App fallback: ${hrAppFallbackMessage.ifBlank { "No failed app export check." }}")
                appendLine("Latest captured source: $latestHrSource${if (latestHrSourcePackage.isNotBlank()) "; $latestHrSourcePackage ($latestHrSourceLabel)" else ""}")
                appendLine("No raw heart rate values are included in this report.")
                appendLine()
                append(attempt)
            }.take(DIAGNOSTIC_LIMIT)
        }
        private fun sensorDescription(sensor: Sensor): String =
            "${sensor.name}; vendor=${sensor.vendor}; type=${sensor.type}; stringType=${sensor.stringType}; version=${sensor.version}; reportingMode=${sensor.reportingMode}; wakeUp=${sensor.isWakeUpSensor}; minDelayUs=${sensor.minDelay}; maxDelayUs=${sensor.maxDelay}".take(480)
        private fun persistDiagnosticReport(context: Context) {
            runCatching { File(context.filesDir, "heart-rate-diagnostic.txt").writeText(heartRateDiagnosticReport(context)) }
                .onFailure { Log.w(TAG, "Could not save HR diagnostic: ${it.javaClass.simpleName}") }
        }
        fun heartRateStatusText(context: Context, measurementDetails: Boolean = false): String {
            if (hrStatus == "checking_health_apps") return "Checking readings shared by installed health apps…"
            if (hrStatus == "app_reading" && displayBpm() > 0)
                return "From $latestHrSourceLabel · ${heartRateAgeLabel()}"
            if (hrStatus == "power_saver") {
                val displayed = watchFaceHeartRate()
                val ageLabel = HeartRatePolicy.ageLabel(displayed.capturedAt, System.currentTimeMillis())
                return if (displayed.bpm > 0) "Power Saver mode · $ageLabel" else "Power Saver mode · Optical sensor off"
            }
            if (hrAppFallbackMessage.isNotBlank() && hrStatus !in setOf("measuring", "reading"))
                return hrAppFallbackMessage
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
                return "Allow sensor access in App permissions to measure heart rate."
            if (!hasHeartRateSensor(context)) return "No compatible heart rate sensor is exposed by this watch. Open Heart rate details to check this watch's sensor support."
            val displayed = watchFaceHeartRate()
            val ageLabel = HeartRatePolicy.ageLabel(displayed.capturedAt, System.currentTimeMillis())
            val current = when(hrStatus) {
                "measuring" -> if (displayed.bpm > 0) "Measuring… Latest: ${displayed.bpm} bpm · $ageLabel" else "Measuring… Wear snugly and stay still."
                "no_contact" -> "No new reading. Check wrist contact and measure again."
                "no_events" -> "The sensor registered but sent no readings. Open Heart rate details and try Measure again."
                "invalid_samples" -> "The sensor sent readings that could not be validated. Open Heart rate details and try again."
                "unreliable_accuracy" -> "The sensor reported unreliable accuracy. Stay still and measure again."
                "sensor_error" -> "The watch did not allow sensor registration. Open Heart rate details and check sensor permission."
                "background_permission_required" -> "Open HealthSync to measure, or allow all-time sensor access in App permissions for background readings."
                "service_unavailable" -> "Sensor monitoring could not start. Open HealthSync and check sensor permission."
                "disabled" -> "Automatic measurement is off. Tap Measure for a reading."
                else -> if (displayed.bpm > 0) ageLabel else "No reading yet. Tap Measure and stay still."
            }
            return if (displayed.bpm > 0 && hrStatus in setOf("no_contact", "no_events", "invalid_samples", "sensor_error", "service_unavailable", "disabled"))
                "$current $ageLabel" else current
        }

        private fun sendImportedMsg(type: MessageType, payload: Any, capturedAt: Long) {
            val protocol = Gson()
            BluetoothClientService.sendRawMsg(SyncMessage(type, capturedAt, protocol.toJson(payload)), protocol)
        }

        fun publishAppFallbackStatus(context: Context, metric: HealthAppMetric, status: String, message: String = "") {
            if (metric == HealthAppMetric.HEART_RATE) {
                hrStatus = status; hrAppFallbackMessage = message
                context.sendBroadcast(Intent("com.healthsync.watch.HR_UPDATE").setPackage(context.packageName)
                    .putExtra("bpm", freshBpm()).putExtra("available", heartRateAvailable).putExtra("status", status)
                    .putExtra("reading_time", latestBpmTimeMs))
                sendImportedMsg(MessageType.HEART_RATE_STATUS, HeartRateStatusPayload(status, displayBpm(), latestBpmTimeMs,
                    heartRateDiagnosticReport(context)), System.currentTimeMillis())
                persistDiagnosticReport(context)
            } else {
                spo2Status = status; oxygenAppFallbackMessage = message
                context.sendBroadcast(Intent("com.healthsync.watch.SPO2_UPDATE").setPackage(context.packageName)
                    .putExtra("spo2", displaySpO2()).putExtra("available", spo2Available).putExtra("status", status)
                    .putExtra("reading_time", latestSpo2TimeMs))
            }
        }

        private fun restoreHealthCaptureCache(context: Context) {
            val saved = context.getSharedPreferences("HealthSync_Steps", Context.MODE_PRIVATE)
            if (latestBpmTimeMs == 0L && saved.getInt("hr_capture_schema", 0) == 2) {
                latestBpm = saved.getInt("last_hr_bpm", -1); latestAcc = saved.getInt("last_hr_accuracy", 0)
                latestBpmTimeMs = saved.getLong("last_hr_time", 0L)
                latestHrSource = saved.getString("last_hr_source", WatchHealthSourcePolicy.SENSOR).orEmpty()
                latestHrSourcePackage = saved.getString("last_hr_source_package", "").orEmpty()
                latestHrSourceLabel = saved.getString("last_hr_source_label", "").orEmpty()
                if (displayBpm() < 0) { latestBpm = -1; latestAcc = 0; latestBpmTimeMs = 0L }
            }
            if (latestSpo2TimeMs == 0L && saved.getBoolean("oxygen_sensor_captured", false)) {
                latestSpo2 = saved.getFloat("sensor_oxygen_value", -1f); latestSpo2TimeMs = saved.getLong("sensor_oxygen_time", 0L)
                latestOxygenSource = saved.getString("oxygen_source", WatchHealthSourcePolicy.SENSOR).orEmpty()
                latestOxygenSourcePackage = saved.getString("oxygen_source_package", "").orEmpty()
                latestOxygenSourceLabel = saved.getString("oxygen_source_label", "").orEmpty()
                if (displaySpO2() < 0f) { latestSpo2 = -1f; latestSpo2TimeMs = 0L }
            }
        }
        /** Preserve the export's capture time and source; never assign it sensor accuracy. */
        fun importHealthAppReading(context: Context, reading: HealthAppReading): Boolean {
            val prefs = context.getSharedPreferences("HealthSync_Steps", Context.MODE_PRIVATE)
            restoreHealthCaptureCache(context)
            if (!WatchHealthSourcePolicy.validAppIdentity(reading.sourcePackage, reading.sourceLabel)) return false
            val now = System.currentTimeMillis()
            if (reading.metric == HealthAppMetric.HEART_RATE) {
                val bpm = WatchHealthSourcePolicy.heartRate(reading.value.toInt(), 0, reading.capturedAt, now,
                    HeartRatePolicy.DISPLAY_MS, WatchHealthSourcePolicy.HEALTH_APP, reading.sourcePackage, reading.sourceLabel)
                if (!reading.value.isFinite() || reading.value !in 25f..240f || bpm < 0) return false
                // A late/duplicated app export cannot replace a newer sensor capture.
                if (displayBpm(now) > 0 && (reading.capturedAt < latestBpmTimeMs ||
                        (reading.capturedAt == latestBpmTimeMs && latestHrSource != WatchHealthSourcePolicy.HEALTH_APP))) return false
                if (latestHrSource == WatchHealthSourcePolicy.HEALTH_APP && latestHrSourcePackage == reading.sourcePackage &&
                    latestBpmTimeMs == reading.capturedAt && latestBpm == bpm) return true
                latestBpm = bpm; latestAcc = 0; latestBpmTimeMs = reading.capturedAt
                latestHrSource = WatchHealthSourcePolicy.HEALTH_APP
                latestHrSourcePackage = reading.sourcePackage; latestHrSourceLabel = reading.sourceLabel
                prefs.edit().putInt("hr_capture_schema", 2).putInt("last_hr_bpm", bpm).putInt("last_hr_accuracy", 0)
                    .putLong("last_hr_time", reading.capturedAt).putString("last_hr_source", latestHrSource)
                    .putString("last_hr_source_package", latestHrSourcePackage).putString("last_hr_source_label", latestHrSourceLabel).apply()
                sendImportedMsg(MessageType.HEART_RATE, HeartRatePayload(latestBpm, latestAcc, latestHrSource, latestHrSourcePackage, latestHrSourceLabel), reading.capturedAt)
                try { WatchDatabaseHelper.getInstance(context).insertHeartRate(latestBpm, latestAcc, reading.capturedAt, latestHrSource, latestHrSourcePackage, latestHrSourceLabel, isSynced = BluetoothClientService.isConnected) } catch (_: Exception) {}
            } else {
                if (WatchHealthSourcePolicy.oxygen(reading.value, reading.capturedAt, now, HeartRatePolicy.DISPLAY_MS,
                        WatchHealthSourcePolicy.HEALTH_APP, reading.sourcePackage, reading.sourceLabel) < 0f) return false
                if (displaySpO2(now) > 0f && (reading.capturedAt < latestSpo2TimeMs ||
                        (reading.capturedAt == latestSpo2TimeMs && latestOxygenSource != WatchHealthSourcePolicy.HEALTH_APP))) return false
                if (latestOxygenSource == WatchHealthSourcePolicy.HEALTH_APP && latestOxygenSourcePackage == reading.sourcePackage &&
                    latestSpo2TimeMs == reading.capturedAt && latestSpo2 == reading.value) return true
                latestSpo2 = reading.value; latestSpo2TimeMs = reading.capturedAt
                latestOxygenSource = WatchHealthSourcePolicy.HEALTH_APP
                latestOxygenSourcePackage = reading.sourcePackage; latestOxygenSourceLabel = reading.sourceLabel
                prefs.edit().putBoolean("oxygen_sensor_captured", true).putFloat("sensor_oxygen_value", latestSpo2)
                    .putLong("sensor_oxygen_time", latestSpo2TimeMs).putString("oxygen_source", latestOxygenSource)
                    .putString("oxygen_source_package", latestOxygenSourcePackage).putString("oxygen_source_label", latestOxygenSourceLabel).apply()
                sendImportedMsg(MessageType.SPO2, SpO2Payload(latestSpo2, latestOxygenSource, latestOxygenSourcePackage, latestOxygenSourceLabel), reading.capturedAt)
                try { WatchDatabaseHelper.getInstance(context).insertSpO2(latestSpo2, reading.capturedAt, latestOxygenSource, latestOxygenSourcePackage, latestOxygenSourceLabel, isSynced = BluetoothClientService.isConnected) } catch (_: Exception) {}
            }
            return true
        }


    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var sensorManager: SensorManager
    private lateinit var watchPrefs: WatchPreferences
    private val prefs by lazy { getSharedPreferences("HealthSync_Steps", Context.MODE_PRIVATE) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastHardwareSteps = -1
    private var hardwareStepGuard = HardwareStepCounterGuard()
    private var counterRegisteredAtMs = 0L
    private var detectorRegisteredAtMs = 0L
    private val stepDetectorGuard = StepDetectorGuard()
    private var motionRegisteredAtMs = 0L
    private var lastMotionMs = -1L
    private var dayKey = ""
    private var hardwareStepsRegistered = false
    private var stepDetectorRegistered = false
    private var motionSensorRegistered = false
    private var lastStepCapturePersistedAt = 0L
    private var hrJob: Job? = null
    private var spo2Job: Job? = null
    private val measurementJobs = mutableMapOf<MeasurementKind, Job>()
    private val appFallbackMutex = Mutex()
    private val measurementSensors = mutableMapOf<MeasurementKind, List<Sensor>>()
    private val manualMeasurements = mutableMapOf<MeasurementKind, Boolean>()
    private val completionStatuses = mutableMapOf<MeasurementKind, String>()
    private val measurementSchedule = MeasurementSchedule()
    private var scheduleBootCount = -1
    private data class AlarmSlot(val interval: Long, val revision: Long, val due: Long)
    private val alarmSlots = mutableMapOf<MeasurementKind, AlarmSlot>()
    private val measurementWindows = MeasurementWindows(MEASURE_WINDOW_MS)
    private val measurementStartedNanos = mutableMapOf<MeasurementKind, Long>()
    private var lastHrEventNanos = 0L
    private var lastOxygenEventNanos = 0L
    private var lastOxygenObservedNanos = 0L
    private var lastHrSentMs = 0L
    private var foregroundStarted = false
    private val oxygenSensors = mutableListOf<Sensor>()
    private var heartSensors = emptyList<Sensor>()
    private var activeHeartSensors = emptyList<Sensor>()
    private var heartRateAttempt: HeartRateAttemptDiagnostics? = null
    private val heartRatePreviewTracker = HeartRatePreviewTracker()
    private var lastDiagnosticRenderMs = -1L
    private var lastHardwareStepTimeMs = 0L
    private val stepTracker = SignalProcessing.AntiFalsePositiveStepTracker { delta ->
        if (!hardwareStepsRegistered && !stepDetectorRegistered) {
            incrementSteps(delta)
        }
    }
    private val fallDetector = FallDetection { severity -> sendMsg(MessageType.FALL_DETECTED, FallDetectedPayload(severity)) }
    private val sleepTracker by lazy {
        SleepTracker(this).apply {
            onSleepFinalized = { payload ->
                sendMsg(MessageType.SLEEP, payload)
                sendBroadcast(Intent("com.healthsync.watch.SLEEP_UPDATE").setPackage(packageName))
            }
        }
    }
    private var currentHourSteps = 0
    private var currentTrackingHour = -1
    private var hourObservedMonitoringMs = 0L
    private var lastActivityMonitoringSample = -1L
    private var lastStandAlertHour = -1

    override fun onCreate() {
        super.onCreate()
        watchPrefs = WatchPreferences(this)
        latestSensorPreview = null
        latestOxygenPreview = null
        hrAppFallbackMessage = ""; oxygenAppFallbackMessage = ""
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createChannel()
        foregroundStarted = ForegroundPolicy.start(this, NOTIF_ID, buildNotif("Steps and sensor monitoring"))
        if (!foregroundStarted) {
            stepCaptureStatus = "monitoring_unavailable"; stepSensorAvailable = false
            updateHrStatus("service_unavailable"); stopSelf(); return
        }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HealthSync:Measurement").apply { setReferenceCounted(false) }

        migrateUnverifiedStepCache()
        latestSteps = prefs.getInt("saved_steps", 0).coerceAtLeast(0)
        latestStepsCaptureTimeMs = prefs.getLong("steps_captured_at", 0L)
        latestStepCaptureDay = prefs.getString("steps_capture_date", "").orEmpty()
        stepCaptureSource = prefs.getString("steps_capture_source", "NONE").orEmpty()
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        if (!StepCapturePolicy.capturedToday(latestStepsCaptureTimeMs, latestStepCaptureDay, currentDate,
                stepCaptureSource, System.currentTimeMillis())) {
            latestSteps = 0; latestStepsCaptureTimeMs = 0L; latestStepCaptureDay = ""; stepCaptureSource = "NONE"
        }
        latestLifetimeSteps = prefs.getLong("lifetime_steps", latestSteps.toLong()).coerceAtLeast(0L)
        lastHardwareSteps = prefs.getInt("last_hardware_steps", -1)
        dayKey = prefs.getString("saved_date", "") ?: ""
        if (dayKey.isEmpty() && prefs.getInt("saved_day", -1) == java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)) {
            dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            prefs.edit().putString("saved_date", dayKey).apply()
        }
        val bootWallMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val previousBoot = prefs.getLong("boot_wall_ms", 0L)
        if (previousBoot == 0L || kotlin.math.abs(previousBoot - bootWallMs) > 300_000L) lastHardwareSteps = -1
        prefs.edit().putLong("boot_wall_ms", bootWallMs).apply()
        resetDayIfNeeded()
        val savedCounterTime = prefs.getLong("last_hardware_time_ms", 0L)
            .takeIf { it in 1L..SystemClock.elapsedRealtime() } ?: 0L
        hardwareStepGuard = HardwareStepCounterGuard(lastHardwareSteps, savedCounterTime)
        val validatedCaptureSchema = prefs.getInt("hr_capture_schema", 0) == 2
        latestBpm = if (validatedCaptureSchema) prefs.getInt("last_hr_bpm", -1) else -1
        latestAcc = if (validatedCaptureSchema) prefs.getInt("last_hr_accuracy", 0) else 0
        latestBpmTimeMs = if (validatedCaptureSchema) prefs.getLong("last_hr_time", 0L) else 0L
        latestHrSource = prefs.getString("last_hr_source", WatchHealthSourcePolicy.SENSOR).orEmpty()
        latestHrSourcePackage = prefs.getString("last_hr_source_package", "").orEmpty()
        latestHrSourceLabel = prefs.getString("last_hr_source_label", "").orEmpty()
        if (displayBpm() < 0) {
            latestBpm = -1; latestAcc = 0; latestBpmTimeMs = 0L
        }
        // Legacy unverified previews and arbitrary oxygen values are not captured health records.
        latestSensorPreview = null
        prefs.edit().remove("last_sensor_hr_bpm").remove("last_sensor_hr_time").apply()
        latestSpo2 = if (prefs.getBoolean("oxygen_sensor_captured", false)) prefs.getFloat("sensor_oxygen_value", -1f) else -1f
        latestSpo2TimeMs = if (latestSpo2 > 0f) prefs.getLong("sensor_oxygen_time", 0L) else 0L
        latestOxygenSource = prefs.getString("oxygen_source", WatchHealthSourcePolicy.SENSOR).orEmpty()
        latestOxygenSourcePackage = prefs.getString("oxygen_source_package", "").orEmpty()
        latestOxygenSourceLabel = prefs.getString("oxygen_source_label", "").orEmpty()
        if (displaySpO2() < 0f) { latestSpo2 = -1f; latestSpo2TimeMs = 0L }
        spo2Status = if (latestOxygenSource == WatchHealthSourcePolicy.HEALTH_APP && displaySpO2() > 0f) "app_reading" else "idle"
        findOxygenSensors()
        refreshHeartSensors()
        spo2Available = oxygenSensors.isNotEmpty()

        scheduleBootCount = WatchTimerStore.bootCount(this)
        val sameBoot = scheduleBootCount >= 0 && prefs.getInt("schedule_boot_count", -1) == scheduleBootCount
        for (kind in MeasurementKind.values()) {
            val interval = if (kind == MeasurementKind.HEART_RATE) watchPrefs.hrIntervalMs else watchPrefs.spo2IntervalMs
            measurementSchedule.configure(kind, interval, SystemClock.elapsedRealtime())
            if (sameBoot) measurementSchedule.restoreDue(kind, prefs.getLong("schedule_${kind.name}_interval", -1L),
                prefs.getLong("schedule_${kind.name}_due", 0L))
        }
        if (watchPrefs.isPowerSaveActive) {
            applyPowerSaveState()
        } else {
            hardwareStepsRegistered = tryRegister(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL)
            stepDetectorRegistered = !hardwareStepsRegistered && tryRegister(Sensor.TYPE_STEP_DETECTOR, SensorManager.SENSOR_DELAY_NORMAL)
            motionSensorRegistered = tryRegister(Sensor.TYPE_ACCELEROMETER, 20_000)
            restartIntervalJobs()
            if (measurementJobs[MeasurementKind.HEART_RATE]?.isActive != true) updateHrStatus(initialHrStatus())
        }
        scope.launch {
            while (isActive) { delay(30_000L); resetDayIfNeeded(); refreshStepCaptureStatus() }
        }
        broadcastSteps()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) { stopSelf(); return START_NOT_STICKY }
        // Permissions may have been granted since creation. Repeated Home starts leave
        // registered sensors and live measurement windows alone.
        if (!watchPrefs.isPowerSaveActive) {
            if (!hardwareStepsRegistered) hardwareStepsRegistered = tryRegister(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL)
            if (hardwareStepsRegistered && stepDetectorRegistered) {
                sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let { sensorManager.unregisterListener(listener, it) }
                stepDetectorRegistered = false
            }
            if (!hardwareStepsRegistered && !stepDetectorRegistered) stepDetectorRegistered = tryRegister(Sensor.TYPE_STEP_DETECTOR, SensorManager.SENSOR_DELAY_NORMAL)
            if (!motionSensorRegistered) motionSensorRegistered = tryRegister(Sensor.TYPE_ACCELEROMETER, 20_000)
        } else if (watchPrefs.isExtremePowerSave && !hardwareStepsRegistered) {
            hardwareStepsRegistered = tryRegister(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL)
        }
        refreshStepCaptureStatus()
        val previousAvailability = heartRateAvailable
        refreshHeartSensors()
        if (measurementJobs[MeasurementKind.HEART_RATE]?.isActive != true &&
            (previousAvailability != heartRateAvailable || hrStatus in setOf("permission_required", "missing_sensor"))) {
            updateHrStatus(initialHrStatus())
        }
        when (intent?.action) {
            ACTION_SETTINGS_UPDATED -> {
                restartIntervalJobs()
                BluetoothClientService.syncSensorIntervals(this)
                if (measurementJobs[MeasurementKind.HEART_RATE]?.isActive != true) updateHrStatus(initialHrStatus())
            }
            ACTION_POWER_SAVE_UPDATED -> {
                applyPowerSaveState()
            }
            ACTION_FORCE_MEASURE_HR -> requestMeasurement(MeasurementKind.HEART_RATE)
            ACTION_CHECK_APP_HR -> requestAppExport(MeasurementKind.HEART_RATE)
            ACTION_CHECK_APP_OXYGEN -> requestAppExport(MeasurementKind.OXYGEN)
            ACTION_AUTO_HR -> handleScheduledAlarm(MeasurementKind.HEART_RATE, intent)
            ACTION_AUTO_OXYGEN -> handleScheduledAlarm(MeasurementKind.OXYGEN, intent)
            ACTION_SYNC_HEART_RATE -> {
                val bpm = displayBpm()
                if (bpm > 0) BluetoothClientService.sendRawMsg(SyncMessage(type = MessageType.HEART_RATE,
                    timestamp = latestBpmTimeMs, payload = gson.toJson(heartRatePayload())), gson)
                // Sensor events can complete while Bluetooth is disconnected. Replay only
                // real saved captures with their original timestamps on reconnect.
                val oxygen = displaySpO2()
                if (oxygen > 0f) sendMsg(MessageType.SPO2, oxygenPayload(), latestSpo2TimeMs)
                sleepTracker.getLatestPayload()?.let { sendMsg(MessageType.SLEEP, it, it.endTime) }
                if (hasCapturedStepsToday()) sendMsg(MessageType.STEPS,
                    StepsPayload(latestSteps, latestSteps * estimatedCaloriesPerStep()), latestStepsCaptureTimeMs)
                broadcastHr()
            }
            ACTION_FORCE_MEASURE_SPO2 -> {
                requestMeasurement(MeasurementKind.OXYGEN)
            }
        }
        ensureIntervalJobs()
        return START_STICKY
    }

    private fun refreshHeartSensors() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        heartSensors = if (granted) runCatching { HeartRateSensor.candidates(sensorManager) }
            .onFailure { Log.w(TAG, "Heart rate discovery failed: ${it.javaClass.simpleName}") }.getOrDefault(emptyList()) else emptyList()
        heartRateAvailable = heartSensors.isNotEmpty()
    }

    private fun publishHeartRateAttempt(status: String, completed: Boolean = false, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!completed && !force && lastDiagnosticRenderMs >= 0L && now - lastDiagnosticRenderMs < 1000L) return
        val report = heartRateAttempt?.report(status) ?: return
        lastDiagnosticRenderMs = now
        latestHrAttemptReport = report
        if (completed) prefs.edit().putString("last_hr_attempt_report", report).apply()
    }

    private fun findOxygenSensors() {
        // Only explicit oxygen names qualify: heartbeat and private integer IDs
        // alone do not identify oxygen measurements.
        oxygenSensors.clear()
        runCatching { sensorManager.getSensorList(Sensor.TYPE_ALL) }.getOrDefault(emptyList()).filterTo(oxygenSensors) { sensor ->
            OxygenCapturePolicy.isOxygenSensor(sensor.type, sensor.name, sensor.stringType)
        }
    }

    private fun tryRegister(type: Int, delay: Int): Boolean {
        if (type in setOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR) && Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return false
        val sensor = sensorManager.getDefaultSensor(type) ?: return false
        return try {
            val registeredAt = SystemClock.elapsedRealtime()
            sensorManager.registerListener(listener, sensor, delay).also { registered ->
                if (registered) when(type) {
                    Sensor.TYPE_STEP_COUNTER -> {
                        counterRegisteredAtMs = registeredAt
                        // A counter returning after motion fallback includes that same interval.
                        // Rebase it so those already recorded motion steps cannot be counted twice.
                        if (stepCaptureSource in setOf("MOTION_ESTIMATE", "MIXED_CAPTURE")) {
                            hardwareStepGuard.reset(); lastHardwareSteps = -1
                        }
                    }
                    Sensor.TYPE_STEP_DETECTOR -> { detectorRegisteredAtMs = registeredAt; stepDetectorGuard.reset() }
                    Sensor.TYPE_ACCELEROMETER -> { motionRegisteredAtMs = registeredAt; lastMotionMs = -1L }
                }
            }
        }
        catch (e: Exception) { Log.w(TAG, "Sensor $type unavailable: ${e.message}"); false }
    }

    private fun restartIntervalJobs() {
        val now = SystemClock.elapsedRealtime()
        val isPowerSave = watchPrefs.isPowerSaveActive
        for (kind in MeasurementKind.values()) {
            val configured = if (isPowerSave) -1L else (if (kind == MeasurementKind.HEART_RATE) watchPrefs.hrIntervalMs else watchPrefs.spo2IntervalMs)
            val previous = measurementSchedule.intervalMs(kind)
            measurementSchedule.configure(kind, configured, now)
            if (previous != measurementSchedule.intervalMs(kind)) {
                if (kind == MeasurementKind.HEART_RATE) { hrJob?.cancel(); hrJob = null }
                else { spo2Job?.cancel(); spo2Job = null }
                if (manualMeasurements[kind] == false) finishMeasurement(kind,
                    if (kind == MeasurementKind.HEART_RATE) initialHrStatus() else null)
            }
        }
        ensureIntervalJobs()
    }

    private fun applyPowerSaveState() {
        val mode = watchPrefs.powerSaveMode
        when (mode) {
            PowerSaveMode.SUPER_EXTREME -> {
                hrJob?.cancel(); hrJob = null
                spo2Job?.cancel(); spo2Job = null
                if (hardwareStepsRegistered) {
                    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sensorManager.unregisterListener(listener, it) }
                    hardwareStepsRegistered = false
                }
                if (stepDetectorRegistered) {
                    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let { sensorManager.unregisterListener(listener, it) }
                    stepDetectorRegistered = false
                }
                if (motionSensorRegistered) {
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensorManager.unregisterListener(listener, it) }
                    motionSensorRegistered = false
                }
                unregisterMeasurementSensors(MeasurementKind.HEART_RATE)
                unregisterMeasurementSensors(MeasurementKind.OXYGEN)
                updateHrStatus("power_saver")
                spo2Status = "power_saver"
                updateMeasurementAlarms()
            }
            PowerSaveMode.EXTREME -> {
                if (motionSensorRegistered) {
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensorManager.unregisterListener(listener, it) }
                    motionSensorRegistered = false
                }
                hrJob?.cancel(); hrJob = null
                spo2Job?.cancel(); spo2Job = null
                if (!hardwareStepsRegistered) hardwareStepsRegistered = tryRegister(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL)
                if (measurementJobs[MeasurementKind.HEART_RATE]?.isActive != true) updateHrStatus("power_saver")
                updateMeasurementAlarms()
            }
            PowerSaveMode.NORMAL -> {
                if (!hardwareStepsRegistered) hardwareStepsRegistered = tryRegister(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL)
                if (!hardwareStepsRegistered && !stepDetectorRegistered) stepDetectorRegistered = tryRegister(Sensor.TYPE_STEP_DETECTOR, SensorManager.SENSOR_DELAY_NORMAL)
                if (!motionSensorRegistered) motionSensorRegistered = tryRegister(Sensor.TYPE_ACCELEROMETER, 20_000)
                restartIntervalJobs()
                if (measurementJobs[MeasurementKind.HEART_RATE]?.isActive != true) updateHrStatus(initialHrStatus())
            }
        }
    }

    private fun ensureIntervalJobs() {
        if (watchPrefs.isPowerSaveActive) {
            hrJob?.cancel(); hrJob = null
            spo2Job?.cancel(); spo2Job = null
            updateMeasurementAlarms()
            return
        }
        if (measurementSchedule.isEnabled(MeasurementKind.HEART_RATE) && hrJob?.isActive != true) {
            hrJob = intervalJob(MeasurementKind.HEART_RATE)
        }
        if (measurementSchedule.isEnabled(MeasurementKind.OXYGEN) && spo2Job?.isActive != true) {
            spo2Job = intervalJob(MeasurementKind.OXYGEN)
        }
        updateMeasurementAlarms()
    }

    private fun intervalJob(kind: MeasurementKind) = scope.launch {
        while (isActive) {
            val remaining = measurementSchedule.dueDelayMs(kind, SystemClock.elapsedRealtime()) ?: break
            // Alarms wake a sleeping watch; this coroutine only serves the already-awake app.
            if (remaining > 0L) { delay(remaining); continue }
            requestMeasurement(kind, manualRequest = false)
        }
    }

    private fun requestMeasurement(kind: MeasurementKind, manualRequest: Boolean = true) {
        if (watchPrefs.isSuperExtremePowerSave) return
        if (watchPrefs.isExtremePowerSave && !manualRequest) return
        val existing = measurementJobs[kind]
        if (existing != null && !existing.isCompleted) {
            if (!manualRequest) {
                measurementSchedule.markFinished(kind, SystemClock.elapsedRealtime())
                updateMeasurementAlarms()
            }
            else if (!measurementWindows.isActive(kind, SystemClock.elapsedRealtime())) {
                scope.launch { existing.join(); requestMeasurement(kind, manualRequest = true) }
            }
            return
        }
        // Failed permission/registration attempts also consume a scheduled slot.
        measurementSchedule.markStarted(kind, SystemClock.elapsedRealtime())
        updateMeasurementAlarms()
        val accessStatus = bodySensorAccessStatus(this)
        if (accessStatus != null) {
            startAppFallback(kind, manualRequest, accessStatus)
            return
        }
        if (kind == MeasurementKind.HEART_RATE) refreshHeartSensors()
        if (kind == MeasurementKind.OXYGEN) {
            findOxygenSensors()
            spo2Available = oxygenSensors.isNotEmpty()
        }
        val requestedSensors = when (kind) {
            MeasurementKind.HEART_RATE -> heartSensors
            MeasurementKind.OXYGEN -> oxygenSensors.toList()
        }
        if (kind == MeasurementKind.HEART_RATE) {
            heartRatePreviewTracker.reset()
            lastDiagnosticRenderMs = -1L
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
            heartRateAttempt = HeartRateAttemptDiagnostics(System.currentTimeMillis(), requestedSensors.map { sensorDescription(it) }, granted)
            if (!granted) {
                publishHeartRateAttempt("permission_required", completed = true)
                startAppFallback(kind, manualRequest, "permission_required"); return
            }
        }
        val acquisitionStartNanos = SystemClock.elapsedRealtimeNanos()
        var nextCandidate = 0
        var selectedAtMs = acquisitionStartNanos / 1_000_000L
        fun selectNextCandidate(): Boolean {
            unregisterMeasurementSensors(kind)
            while (nextCandidate < requestedSensors.size) {
                val sensor = requestedSensors[nextCandidate++]
                val registeredAt = SystemClock.elapsedRealtimeNanos()
                val registered = try {
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL).also {
                        if (kind == MeasurementKind.HEART_RATE) heartRateAttempt?.registration(sensorDescription(sensor),
                            if (it) "registered / selected" else "registration returned false", selected = it)
                    }
                } catch (e: Exception) {
                    if (kind == MeasurementKind.HEART_RATE) heartRateAttempt?.registration(sensorDescription(sensor), "registration failed: ${e.javaClass.simpleName}")
                    Log.w(TAG, "$kind sensor unavailable: ${e.javaClass.simpleName}"); false
                }
                if (!registered) continue
                measurementSensors[kind] = listOf(sensor)
                if (kind == MeasurementKind.HEART_RATE) {
                    activeHeartSensors = listOf(sensor)
                    heartRatePreviewTracker.reset()
                } else lastOxygenObservedNanos = 0L
                measurementStartedNanos[kind] = registeredAt
                selectedAtMs = registeredAt / 1_000_000L
                completionStatuses.remove(kind)
                return true
            }
            return false
        }
        if (!selectNextCandidate()) {
            val status = if (requestedSensors.isEmpty()) "missing_sensor" else "sensor_error"
            if (kind == MeasurementKind.HEART_RATE) {
                publishHeartRateAttempt(status, completed = true)
            }
            startAppFallback(kind, manualRequest, status)
            return
        }
        manualMeasurements[kind] = manualRequest
        measurementWindows.request(kind, acquisitionStartNanos / 1_000_000L, if(kind == MeasurementKind.HEART_RATE) HR_MEASURE_WINDOW_MS else MEASURE_WINDOW_MS)
        if (kind == MeasurementKind.HEART_RATE) {
            hrAppFallbackMessage = ""
            publishHeartRateAttempt("measuring", force = true)
            updateHrStatus("measuring")
        } else { oxygenAppFallbackMessage = ""; updateOxygenStatus("measuring") }
        refreshMeasurementWakeLock()
        measurementJobs[kind] = scope.launch {
            try {
                while (isActive) {
                    val remainingMs = measurementWindows.remainingMs(kind, SystemClock.elapsedRealtime())
                    if (remainingMs <= 0L) break
                    if (SensorCandidateRetryPolicy.shouldAdvance(SystemClock.elapsedRealtime(), selectedAtMs,
                            if (kind == MeasurementKind.HEART_RATE) HR_MEASURE_WINDOW_MS else MEASURE_WINDOW_MS,
                            requestedSensors.size, nextCandidate < requestedSensors.size)) {
                        if (!selectNextCandidate()) break
                        if (kind == MeasurementKind.HEART_RATE) updateHrStatus("measuring")
                    }
                    delay(minOf(1000L, remainingMs))
                }
                // Release hardware and its wake lock before a bounded, read-only app query.
                unregisterMeasurementSensors(kind)
                measurementWindows.clear(kind)
                refreshMeasurementWakeLock()
                completionStatuses[kind] = readAppFallback(kind,
                    completionStatuses[kind] ?: if (kind == MeasurementKind.HEART_RATE)
                        heartRateAttempt?.finalStatus() ?: "no_events" else "timeout")
            } finally {
                unregisterMeasurementSensors(kind)
                measurementWindows.clear(kind)
                measurementSchedule.markFinished(kind, SystemClock.elapsedRealtime())
                updateMeasurementAlarms()
                measurementStartedNanos.remove(kind)
                measurementJobs.remove(kind)
                manualMeasurements.remove(kind)
                refreshMeasurementWakeLock()
                if (kind == MeasurementKind.HEART_RATE) {
                    val status = completionStatuses.remove(kind)
                        ?: if (lastHrEventNanos >= acquisitionStartNanos) "reading" else bodySensorAccessStatus(this@SensorCollectorService) ?: heartRateAttempt?.finalStatus() ?: "no_events"
                    publishHeartRateAttempt(status, completed = true)
                    updateHrStatus(status)
                } else updateOxygenStatus(completionStatuses.remove(kind) ?: bodySensorAccessStatus(this@SensorCollectorService) ?: "timeout")
            }
        }
    }

    private fun unregisterMeasurementSensors(kind: MeasurementKind) {
        if (kind == MeasurementKind.HEART_RATE) activeHeartSensors = emptyList()
        measurementSensors.remove(kind)?.forEach { sensor ->
            try { sensorManager.unregisterListener(listener, sensor) }
            catch (e: Exception) { Log.w(TAG, "$kind sensor could not unregister: ${e.javaClass.simpleName}") }
        }
    }

    private fun startAppFallback(kind: MeasurementKind, manualRequest: Boolean, sensorStatus: String) {
        manualMeasurements[kind] = manualRequest
        measurementJobs[kind] = scope.launch {
            var status = sensorStatus
            try { status = readAppFallback(kind, sensorStatus) }
            finally {
                measurementJobs.remove(kind)
                manualMeasurements.remove(kind)
                measurementSchedule.markFinished(kind, SystemClock.elapsedRealtime())
                updateMeasurementAlarms()
                if (kind == MeasurementKind.HEART_RATE) updateHrStatus(status) else updateOxygenStatus(status)
            }
        }
    }

    private fun requestAppExport(kind: MeasurementKind) {
        if (measurementJobs[kind]?.isCompleted == false) return
        measurementSchedule.markStarted(kind, SystemClock.elapsedRealtime())
        updateMeasurementAlarms()
        startAppFallback(kind, manualRequest = true, sensorStatus = bodySensorAccessStatus(this) ?: "no_events")
    }

    private suspend fun readAppFallback(kind: MeasurementKind, sensorStatus: String): String {
        if (kind == MeasurementKind.HEART_RATE) updateHrStatus("checking_health_apps")
        else updateOxygenStatus("checking_health_apps")
        val metric = if (kind == MeasurementKind.HEART_RATE) HealthAppMetric.HEART_RATE else HealthAppMetric.OXYGEN
        val result = appFallbackMutex.withLock {
            withContext(Dispatchers.IO) { InstalledHealthAppFallback(this@SensorCollectorService).readLatest(metric) }
        }
        if (result.reading?.let { acceptHealthAppReading(it) } == true) {
            if (kind == MeasurementKind.HEART_RATE) hrAppFallbackMessage = "" else oxygenAppFallbackMessage = ""
            return "app_reading"
        }
        val reason = when (sensorStatus) {
            "permission_required" -> "Allow sensor access in App permissions."
            "background_permission_required" -> "Open HealthSync or allow all-time sensor access."
            "missing_sensor" -> "No compatible sensor is exposed by this watch."
            "sensor_error" -> "The watch could not start a compatible sensor."
            "unreliable_accuracy" -> "The sensor's reading is unverified."
            else -> "No valid sensor reading was received."
        }
        val message = "$reason ${result.message}"
        if (kind == MeasurementKind.HEART_RATE) hrAppFallbackMessage = message else oxygenAppFallbackMessage = message
        return sensorStatus
    }

    private fun acceptHealthAppReading(reading: HealthAppReading) = importHealthAppReading(this, reading)

    private fun heartRatePayload() = HeartRatePayload(latestBpm, latestAcc, latestHrSource, latestHrSourcePackage, latestHrSourceLabel)
    private fun oxygenPayload() = SpO2Payload(latestSpo2, latestOxygenSource, latestOxygenSourcePackage, latestOxygenSourceLabel)

    private fun finishMeasurement(kind: MeasurementKind, status: String? = null) {
        if (status != null) completionStatuses[kind] = status
        // Stop the driver while the CPU is still awake, before releasing the measurement lock.
        unregisterMeasurementSensors(kind)
        measurementWindows.clear(kind)
        measurementSchedule.markFinished(kind, SystemClock.elapsedRealtime())
        updateMeasurementAlarms()
        refreshMeasurementWakeLock()
        measurementJobs[kind]?.cancel()
    }

    private fun handleScheduledAlarm(kind: MeasurementKind, intent: Intent) {
        if (watchPrefs.isPowerSaveActive) return
        val intervals = watchPrefs.sensorIntervals()
        if (ScheduledMeasurementPolicy.matches(intent.getLongExtra("interval", -1L), intent.getLongExtra("revision", -1L),
                intent.getLongExtra("due", 0L), measurementSchedule.intervalMs(kind) ?: -1L, intervals.revision,
                measurementSchedule.dueAtMs(kind) ?: 0L, SystemClock.elapsedRealtime())) {
            requestMeasurement(kind, manualRequest = false)
        }
    }

    private fun measurementAlarm(kind: MeasurementKind, slot: AlarmSlot): PendingIntent {
        val intent = Intent(this, SensorCollectorService::class.java)
            .setAction(if (kind == MeasurementKind.HEART_RATE) ACTION_AUTO_HR else ACTION_AUTO_OXYGEN)
            .putExtra("interval", slot.interval).putExtra("revision", slot.revision).putExtra("due", slot.due)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(this, 4200 + kind.ordinal, intent, flags)
            else PendingIntent.getService(this, 4200 + kind.ordinal, intent, flags)
    }

    private fun updateMeasurementAlarms() {
        val manager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val revision = watchPrefs.sensorIntervals().revision
        val editor = prefs.edit()
        var changed = prefs.getInt("schedule_boot_count", -1) != scheduleBootCount
        if (changed) editor.putInt("schedule_boot_count", scheduleBootCount)
        if (watchPrefs.isPowerSaveActive) {
            for (kind in MeasurementKind.values()) {
                val interval = measurementSchedule.intervalMs(kind) ?: -1L
                val due = measurementSchedule.dueAtMs(kind) ?: 0L
                val slot = AlarmSlot(interval, revision, due)
                alarmSlots.remove(kind)
                runCatching { manager.cancel(measurementAlarm(kind, slot)) }
            }
            if (changed) editor.apply()
            return
        }
        for (kind in MeasurementKind.values()) {
            val interval = measurementSchedule.intervalMs(kind) ?: -1L
            val due = measurementSchedule.dueAtMs(kind) ?: 0L
            if (prefs.getLong("schedule_${kind.name}_interval", -1L) != interval || prefs.getLong("schedule_${kind.name}_due", 0L) != due) {
                editor.putLong("schedule_${kind.name}_interval", interval).putLong("schedule_${kind.name}_due", due)
                changed = true
            }
            val slot = AlarmSlot(interval, revision, due)
            if (interval <= 0L) {
                alarmSlots.remove(kind)
                runCatching { manager.cancel(measurementAlarm(kind, slot)) }
                continue
            }
            if (alarmSlots[kind] == slot) continue
            val operation = measurementAlarm(kind, slot)
            val trigger = maxOf(due, SystemClock.elapsedRealtime() + 1L)
            var scheduled = false
            val canScheduleExact = Build.VERSION.SDK_INT < 31 ||
                runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
            if (canScheduleExact) {
                scheduled = runCatching {
                    manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, operation)
                    true
                }.getOrDefault(false)
            }
            if (!scheduled) scheduled = runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, operation)
                true
            }.getOrDefault(false)
            if (scheduled) alarmSlots[kind] = slot
            else Log.w(TAG, "$kind automatic alarm could not be scheduled")
        }
        if (changed) editor.apply()
    }

    private fun refreshMeasurementWakeLock() {
        val remainingMs = measurementWindows.remainingMs(SystemClock.elapsedRealtime())
        try {
            if (remainingMs > 0) wakeLock?.acquire(remainingMs + 5000L)
            else if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
    }

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            val isHeartRateEvent = event.sensor in activeHeartSensors
            if (event.values.isEmpty() && !isHeartRateEvent) return
            val nowWall = System.currentTimeMillis()
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            val nowMotion = event.timestamp / 1_000_000L
            when {
                isHeartRateEvent -> {
                    if (!measurementWindows.isActive(MeasurementKind.HEART_RATE, SystemClock.elapsedRealtime())) return
                    val reading = event.values.firstOrNull() ?: return
                    val timestampIssue = heartRatePreviewTracker.observe(reading, event.accuracy,
                        event.timestamp, nowNanos, nowWall, measurementStartedNanos[MeasurementKind.HEART_RATE] ?: 0L)
                    val rejection = HeartRatePolicy.frameRejection(reading, event.accuracy,
                        event.timestamp, nowNanos, nowWall, measurementStartedNanos[MeasurementKind.HEART_RATE] ?: 0L,
                        lastHrEventNanos, timestampIssue)
                    heartRateAttempt?.frame(rejection, event.accuracy, event.timestamp, nowNanos,
                        measurementStartedNanos[MeasurementKind.HEART_RATE] ?: 0L, reading,
                        timestampIssue, heartRatePreviewTracker.preview != null)
                    val isUsableReading = rejection == null && HeartRatePolicy.validReading(reading, event.accuracy)
                    if (!isUsableReading) {
                        latestSensorPreview = heartRatePreviewTracker.preview
                        if (event.accuracy == -1) updateHrStatus("no_contact")
                        if (event.accuracy == 0) {
                            completionStatuses[MeasurementKind.HEART_RATE] = "unreliable_accuracy"
                            if (latestSensorPreview != null && nowWall - lastHrSentMs >= 1_000L) {
                                lastHrSentMs = nowWall; broadcastHr()
                            }
                        }
                        return
                    }
                    val capturedAt = HeartRatePolicy.capturedWallTime(event.timestamp, nowNanos, nowWall,
                        measurementStartedNanos[MeasurementKind.HEART_RATE] ?: 0L, lastHrEventNanos) ?: return
                    lastHrEventNanos = event.timestamp
                    val bpm = reading.toInt()
                    val changed = bpm != latestBpm
                    latestBpm = bpm; latestAcc = event.accuracy; latestBpmTimeMs = capturedAt
                    latestHrSource = WatchHealthSourcePolicy.SENSOR; latestHrSourcePackage = ""; latestHrSourceLabel = ""
                    latestSensorPreview = null
                    completionStatuses.remove(MeasurementKind.HEART_RATE)
                    prefs.edit().putInt("hr_capture_schema", 2).putInt("last_hr_bpm", bpm).putInt("last_hr_accuracy", latestAcc).putLong("last_hr_time", capturedAt)
                        .putString("last_hr_source", latestHrSource).remove("last_hr_source_package").remove("last_hr_source_label")
                        .remove("last_sensor_hr_bpm").remove("last_sensor_hr_time").apply()
                    publishHeartRateAttempt("measuring", force = hrStatus != "measuring")
                    if (hrStatus != "measuring") hrStatus = "measuring"
                    if (changed || nowWall - lastHrSentMs >= 1000L) {
                        lastHrSentMs = nowWall
                        sendMsg(MessageType.HEART_RATE, HeartRatePayload(bpm, latestAcc), capturedAt)
                        try { WatchDatabaseHelper.getInstance(this@SensorCollectorService).insertHeartRate(bpm, latestAcc, capturedAt, isSynced = BluetoothClientService.isConnected) } catch (_: Exception) {}
                        broadcastHr()
                    }
                    finishMeasurement(MeasurementKind.HEART_RATE, "reading")
                }
                event.sensor.type == Sensor.TYPE_STEP_DETECTOR -> {
                    if (hardwareStepsRegistered || !stepDetectorRegistered || !stepDetectorGuard.accept(event.values[0], nowMotion,
                            SystemClock.elapsedRealtime(), detectorRegisteredAtMs)) return
                    resetDayIfNeeded()
                    lastHardwareStepTimeMs = SystemClock.elapsedRealtime()
                    val capturedWall = nowWall - (nowNanos - event.timestamp) / 1_000_000L
                    if (!markStepCapture("HARDWARE_DETECTOR", capturedWall)) return
                    incrementSteps(1)
                }
                event.sensor.type == Sensor.TYPE_STEP_COUNTER -> {
                    val raw = event.values[0]
                    resetDayIfNeeded()
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val delta = hardwareStepGuard.sample(raw, event.sensor.maximumRange, nowMotion,
                        nowElapsed, counterRegisteredAtMs, maxLatencyMs = 120_000L)
                    if (delta != null) {
                        lastHardwareStepTimeMs = nowElapsed
                        lastHardwareSteps = hardwareStepGuard.baselineCount
                        prefs.edit().putInt("last_hardware_steps", lastHardwareSteps)
                            .putLong("last_hardware_time_ms", hardwareStepGuard.baselineTimeMs).apply()
                        val capturedWall = nowWall - (nowNanos - event.timestamp) / 1_000_000L
                        if (!markStepCapture("HARDWARE_COUNTER", capturedWall, publishBaseline = delta == 0)) return
                        if (delta > 0) {
                            incrementSteps(delta)
                        }
                    }
                }
                event.sensor.type == Sensor.TYPE_ACCELEROMETER -> {
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val nowWall = System.currentTimeMillis()
                    if (validWorkoutMotionSample(event.values, event.sensor.maximumRange, nowMotion, nowElapsed, lastMotionMs, motionRegisteredAtMs)) {
                        lastMotionMs = nowMotion
                        latestMotionCapturedElapsed = nowMotion
                        val capturedWall = nowWall - (nowElapsed - nowMotion)
                        if (!hardwareStepsRegistered && !stepDetectorRegistered &&
                            markStepCapture("MOTION_ESTIMATE", capturedWall, publishBaseline = true))
                            stepTracker.processAccel(event.values[0], event.values[1], event.values[2], nowMotion)
                        fallDetector.process(event.values[0], event.values[1], event.values[2], nowMotion)
                        sleepTracker.processAccel(event.values[0], event.values[1], event.values[2], nowMotion, capturedWall, freshBpm())
                        latestRestEstimateActive = sleepTracker.isCurrentlyAsleep()
                        checkHourlyStand(nowWall)
                    }
                }
                else -> if (measurementSensors[MeasurementKind.OXYGEN]?.contains(event.sensor) == true) {
                    if (!measurementWindows.isActive(MeasurementKind.OXYGEN, SystemClock.elapsedRealtime())) return
                    val reading = event.values[0]
                    val startedAt = measurementStartedNanos[MeasurementKind.OXYGEN] ?: 0L
                    val previousObserved = maxOf(lastOxygenEventNanos, lastOxygenObservedNanos)
                    if (event.timestamp > lastOxygenObservedNanos && event.timestamp in startedAt..nowNanos)
                        lastOxygenObservedNanos = event.timestamp
                    if (!HeartRatePolicy.validOxygen(reading, event.accuracy)) {
                        latestOxygenPreview = OxygenSensorPreview.fromFrame(reading, event.accuracy,
                            event.timestamp, nowNanos, nowWall, startedAt, previousObserved)
                        if (latestOxygenPreview != null) updateOxygenStatus("unreliable_accuracy")
                        return
                    }
                    if (HeartRatePolicy.validOxygen(reading, event.accuracy)) {
                        val capturedAt = HeartRatePolicy.capturedWallTime(event.timestamp, nowNanos, nowWall,
                            startedAt, previousObserved) ?: return
                        lastOxygenEventNanos = event.timestamp
                        latestSpo2 = reading; latestSpo2TimeMs = capturedAt
                        latestOxygenPreview = null
                        latestOxygenSource = WatchHealthSourcePolicy.SENSOR; latestOxygenSourcePackage = ""; latestOxygenSourceLabel = ""
                        prefs.edit().putBoolean("oxygen_sensor_captured", true).putFloat("sensor_oxygen_value", reading)
                            .putLong("sensor_oxygen_time", capturedAt).putString("oxygen_source", latestOxygenSource)
                            .remove("oxygen_source_package").remove("oxygen_source_label").apply()
                        finishMeasurement(MeasurementKind.OXYGEN, "received")
                        sendMsg(MessageType.SPO2, SpO2Payload(reading), capturedAt)
                        try { WatchDatabaseHelper.getInstance(this@SensorCollectorService).insertSpO2(reading, capturedAt, isSynced = BluetoothClientService.isConnected) } catch (_: Exception) {}
                        updateOxygenStatus("received")
                    }
                }
            }
        }
    }

    private fun updateOxygenStatus(status: String) {
        spo2Status = status
        sendBroadcast(Intent("com.healthsync.watch.SPO2_UPDATE").setPackage(packageName)
            .putExtra("spo2", displaySpO2()).putExtra("available", spo2Available)
            .putExtra("status", status).putExtra("reading_time", latestSpo2TimeMs))
    }

    private fun migrateUnverifiedStepCache() {
        if (prefs.getInt("steps_capture_schema", 0) == 2) return
        val editor = prefs.edit()
        if (prefs.contains("saved_steps")) {
            // Preserve the old live cache as an audit snapshot, outside displayed health data.
            editor.putInt("legacy_step_cache_count", prefs.getInt("saved_steps", 0))
                .putString("legacy_step_cache_date", prefs.getString("saved_date", ""))
                .putLong("legacy_step_cache_archived_at", System.currentTimeMillis())
                .putInt("legacy_active_hours_mask", prefs.getInt("stand_hours_mask", 0))
        }
        editor.putInt("steps_capture_schema", 2).putInt("saved_steps", 0).putInt("stand_hours_mask", 0)
            .remove("steps_captured_at").remove("steps_capture_date").remove("steps_capture_source").commit()
    }

    private fun refreshStepCaptureStatus() {
        val activityAllowed = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val hardwareUsable = activityAllowed && (hardwareStepsRegistered || stepDetectorRegistered)
        stepSensorAvailable = hardwareUsable || motionSensorRegistered
        val hardwareExposed = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
        val anySensorExposed = hardwareExposed || sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val previousStatus = stepCaptureStatus
        stepCaptureStatus = when {
            !activityAllowed && hardwareExposed && !motionSensorRegistered -> "permission_required"
            !anySensorExposed -> "missing_sensor"
            !stepSensorAvailable -> "monitoring_unavailable"
            StepCapturePolicy.capturedToday(latestStepsCaptureTimeMs, latestStepCaptureDay, dayKey,
                stepCaptureSource, System.currentTimeMillis()) -> "captured"
            else -> "starting"
        }
        if (previousStatus != stepCaptureStatus) broadcastSteps()
    }

    /** Only call after validating the original sensor frame; zero is observed, not assumed. */
    private fun markStepCapture(source: String, capturedAt: Long, publishBaseline: Boolean = false): Boolean {
        resetDayIfNeeded()
        val capturedDate = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(capturedAt))
        if (!StepCapturePolicy.capturedToday(capturedAt, capturedDate, dayKey, source, System.currentTimeMillis())) return false
        val firstCapture = latestStepsCaptureTimeMs <= 0L || latestStepCaptureDay != dayKey
        val nextSource = StepCapturePolicy.sourceAfterObservation(stepCaptureSource, source)
        val sourceChanged = nextSource != stepCaptureSource
        latestStepsCaptureTimeMs = capturedAt; latestStepCaptureDay = capturedDate
        stepCaptureSource = nextSource; stepCaptureStatus = "captured"
        if (firstCapture || sourceChanged || capturedAt - lastStepCapturePersistedAt >= 10_000L) {
            lastStepCapturePersistedAt = capturedAt
            prefs.edit().putInt("steps_capture_schema", 2).putInt("saved_steps", latestSteps).putLong("steps_captured_at", capturedAt)
                .putString("steps_capture_date", capturedDate).putString("steps_capture_source", nextSource).apply()
        }
        if (firstCapture && publishBaseline) {
            sendMsg(MessageType.STEPS, StepsPayload(latestSteps, latestSteps * estimatedCaloriesPerStep()), capturedAt)
            broadcastSteps()
        }
        return true
    }

    private fun resetDayIfNeeded() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        if (dayKey == today) return
        dayKey = today; latestSteps = 0; lastHardwareSteps = -1; hardwareStepGuard.reset(); stepTracker.reset()
        latestStepsCaptureTimeMs = 0L; latestStepCaptureDay = ""; stepCaptureSource = "NONE"
        stepCaptureStatus = "starting"; lastStepCapturePersistedAt = 0L
        currentHourSteps = 0
        lastStandAlertHour = -1
        prefs.edit().putString("saved_date", today).putInt("saved_steps", 0).putInt("last_hardware_steps", -1)
            .putInt("stand_hours_mask", 0).putLong("last_hardware_time_ms", 0L)
            .remove("steps_captured_at").remove("steps_capture_date").remove("steps_capture_source").apply()
        broadcastSteps()
    }

    private fun incrementSteps(delta: Int) {
        if (delta <= 0) return
        resetDayIfNeeded()
        if (!hasCapturedStepsToday()) return
        updateTrackingHour(System.currentTimeMillis())
        currentHourSteps += delta
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHourSteps >= 100) {
            val mask = prefs.getInt("stand_hours_mask", 0)
            val bit = 1 shl hour
            if ((mask and bit) == 0) {
                prefs.edit().putInt("stand_hours_mask", mask or bit).apply()
            }
        }
        latestSteps = (latestSteps.toLong() + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        latestLifetimeSteps += delta.toLong()
        prefs.edit().putInt("saved_steps", latestSteps).putLong("lifetime_steps", latestLifetimeSteps)
            .putLong("steps_captured_at", latestStepsCaptureTimeMs).putString("steps_capture_date", latestStepCaptureDay)
            .putString("steps_capture_source", stepCaptureSource).apply()
        val bucket = latestStepsCaptureTimeMs / 300_000L * 300_000L
        val calPerStep = estimatedCaloriesPerStep()
        val deltaCal = delta * calPerStep
        val totalCal = latestSteps * calPerStep
        WatchDatabaseHelper.getInstance(this).insertOrUpdateStepBucket(bucket, delta, deltaCal)
        sendMsg(MessageType.STEPS, StepsPayload(latestSteps, totalCal), latestStepsCaptureTimeMs)
        broadcastSteps()
    }

    private fun checkHourlyStand(nowWall: Long) {
        updateTrackingHour(nowWall)
        hourObservedMonitoringMs += observedActivityMonitoringDelta(lastActivityMonitoringSample, lastMotionMs)
        lastActivityMonitoringSample = lastMotionMs
        val cal = Calendar.getInstance().apply { timeInMillis = nowWall }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        if (shouldSendActivityReminder(hour, minute, currentHourSteps, hourObservedMonitoringMs,
                hardwareStepsRegistered || stepDetectorRegistered || lastMotionMs >= motionRegisteredAtMs, lastStandAlertHour)) {
            lastStandAlertHour = hour
            dispatchStandReminder()
        }
    }

    private fun updateTrackingHour(nowWall: Long) {
        val hour = Calendar.getInstance().apply { timeInMillis = nowWall }.get(Calendar.HOUR_OF_DAY)
        if (hour != currentTrackingHour) {
            currentTrackingHour = hour; currentHourSteps = 0
            hourObservedMonitoringMs = 0L; lastActivityMonitoringSample = -1L
        }
    }

    private fun dispatchStandReminder() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(longArrayOf(0, 180, 80, 180), -1)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Activity break")
            .setContentText("Few steps recorded this hour. Move when convenient.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm?.notify(3002, notif)
    }

    private inline fun <reified T> sendMsg(type: MessageType, payload: T, timestamp: Long = System.currentTimeMillis()) {
        BluetoothClientService.sendRawMsg(SyncMessage(type = type, timestamp = timestamp, payload = gson.toJson(payload)), gson)
    }
    private fun initialHrStatus() = when {
        watchPrefs.isPowerSaveActive -> "power_saver"
        latestHrSource == WatchHealthSourcePolicy.HEALTH_APP && displayBpm() > 0 -> "app_reading"
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED -> "permission_required"
        !heartRateAvailable -> "missing_sensor"
        watchPrefs.hrIntervalMs <= 0 -> "disabled"
        sensorPreview() != null -> "unreliable_accuracy"
        displayBpm() > 0 -> "reading"
        else -> "idle"
    }
    private fun updateHrStatus(value: String) { hrStatus = value; broadcastHr() }
    private fun broadcastHr() {
        val preview = sensorPreview()
        sendBroadcast(Intent("com.healthsync.watch.HR_UPDATE").setPackage(packageName)
            .putExtra("bpm", freshBpm()).putExtra("available", heartRateAvailable).putExtra("status", hrStatus)
            .putExtra("reading_time", latestBpmTimeMs))
        sendMsg(MessageType.HEART_RATE_STATUS, HeartRateStatusPayload(hrStatus, displayBpm(), latestBpmTimeMs,
            heartRateDiagnosticReport(this), preview?.bpm ?: -1, preview?.capturedAt ?: 0L))
        persistDiagnosticReport(this)
    }
    private fun estimatedCaloriesPerStep(): Double {
        val strideMeters = watchPrefs.userHeightCm * 0.415 / 100.0
        return (watchPrefs.userWeightKg * 0.00057 * strideMeters * 10.0).coerceIn(0.035, 0.075)
    }
    private fun broadcastSteps() = sendBroadcast(Intent("com.healthsync.watch.STEP_UPDATE").setPackage(packageName)
        .putExtra("steps", if (hasCapturedStepsToday()) latestSteps else -1)
        .putExtra("calories", if (hasCapturedStepsToday()) (latestSteps * estimatedCaloriesPerStep()).toFloat() else -1f)
        .putExtra("calories_estimated", true).putExtra("captured", hasCapturedStepsToday())
        .putExtra("source", stepCaptureSource).putExtra("status", stepCaptureStatus).putExtra("captured_at", latestStepsCaptureTimeMs))

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sensor Collection", NotificationManager.IMPORTANCE_LOW))
        }
    }
    private fun buildNotif(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("HealthSync Sensors")
        .setContentText(text).setOngoing(true).setSilent(true).build()
    override fun onDestroy() {
        latestSensorPreview = null
        latestOxygenPreview = null
        latestRestEstimateActive = false; latestMotionCapturedElapsed = -1L
        stepSensorAvailable = false; stepCaptureStatus = "monitoring_unavailable"
        scope.cancel(); sensorManager.unregisterListener(listener)
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

internal enum class MeasurementKind { HEART_RATE, OXYGEN }

/** Each sensor keeps its own finite window; overlap only affects the shared wake-lock timeout. */
internal class MeasurementWindows(private val windowMs: Long) {
    private val deadlines = mutableMapOf<MeasurementKind, Long>()

    fun request(kind: MeasurementKind, nowMs: Long, durationMs: Long = windowMs, extendActive: Boolean = true) {
        // A heart-rate attempt has one acquisition boundary, including repeated Measure taps.
        // Automatic requests also leave other active sensor windows at their original deadline.
        if (isActive(kind, nowMs) && (kind == MeasurementKind.HEART_RATE || !extendActive)) return
        deadlines[kind] = nowMs + durationMs
    }
    fun clear(kind: MeasurementKind) { deadlines.remove(kind) }
    fun isActive(kind: MeasurementKind, nowMs: Long): Boolean = (deadlines[kind] ?: 0L) > nowMs
    fun remainingMs(kind: MeasurementKind, nowMs: Long): Long = ((deadlines[kind] ?: 0L) - nowMs).coerceAtLeast(0L)
    fun remainingMs(nowMs: Long): Long = (deadlines.values.maxOrNull()?.minus(nowMs) ?: 0L).coerceAtLeast(0L)
}

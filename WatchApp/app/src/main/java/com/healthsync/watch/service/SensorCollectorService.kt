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
import com.healthsync.watch.timer.WatchTimerStore
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        const val ACTION_SYNC_HEART_RATE = "com.healthsync.watch.SYNC_HEART_RATE"
        private const val ACTION_AUTO_HR = "com.healthsync.watch.AUTO_HR"
        private const val ACTION_AUTO_OXYGEN = "com.healthsync.watch.AUTO_OXYGEN"
        private const val DIAGNOSTIC_LIMIT = 8_192
        @Volatile private var latestHrAttemptReport: String? = null

        private fun command(context: Context, action: String) {
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
        fun forceMeasureHr(context: Context) = command(context, ACTION_FORCE_MEASURE_HR)
        fun forceMeasureSpO2(context: Context) = command(context, ACTION_FORCE_MEASURE_SPO2)
        fun syncHeartRateToPhone(context: Context) = command(context, ACTION_SYNC_HEART_RATE)

        @Volatile var latestBpm = -1
        @Volatile var latestAcc = 0
        @Volatile var latestBpmTimeMs = 0L
        @Volatile var latestSteps = 0
        @Volatile var latestLifetimeSteps = -1L
        @Volatile var latestSpo2 = -1f
        @Volatile var latestSpo2TimeMs = 0L
        @Volatile var heartRateAvailable = false
        @Volatile var spo2Available = false
        @Volatile var hrStatus = "idle"
        @Volatile private var latestSensorPreview: HeartRateSensorPreview? = null

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
        fun watchFaceHeartRate(nowMs: Long = System.currentTimeMillis()) =
            HeartRateFacePolicy.select(latestBpm, latestAcc, latestBpmTimeMs, sensorPreview(nowMs), nowMs)

        fun freshBpm(nowMs: Long = System.currentTimeMillis()): Int =
            HeartRatePolicy.reliableReading(latestBpm, latestAcc, latestBpmTimeMs, nowMs, HeartRatePolicy.FRESH_MS)
        fun displayBpm(nowMs: Long = System.currentTimeMillis()): Int =
            HeartRatePolicy.reliableReading(latestBpm, latestAcc, latestBpmTimeMs, nowMs, HeartRatePolicy.DISPLAY_MS)
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
                appendLine("Accuracy: -1=no contact, 0=unreliable, 1..3=usable. Independent value/time checks run even on accuracy 0; rejection reasons list the first failing check.")
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
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
                return "Allow sensor access in App permissions to measure heart rate."
            if (!hasHeartRateSensor(context)) return "No compatible heart rate sensor is exposed by this watch. Open Heart rate details to check this watch's sensor support."
            val displayed = watchFaceHeartRate()
            val ageLabel = HeartRatePolicy.ageLabel(displayed.capturedAt, System.currentTimeMillis())
            val current = when(hrStatus) {
                "measuring" -> "Measuring… Wear snugly and stay still for up to 60 seconds."
                "no_contact" -> "No new reading. Check wrist contact and measure again."
                "no_events" -> "The sensor registered but sent no readings. Open Heart rate details and try Measure again."
                "invalid_samples" -> "The sensor sent readings that could not be validated. Open Heart rate details to check accuracy and timestamps."
                "unreliable_accuracy" -> when {
                    measurementDetails -> "Sensor accuracy flag: 0. This value is shown as sensor feedback and is excluded from recorded history and workouts."
                    sensorPreview() != null -> "Reading received from the watch sensor. Tap Measure to refresh."
                    else -> "The last sensor reading has expired. Tap Measure again."
                }
                "sensor_error" -> "The watch did not allow sensor registration. Open Heart rate details and check sensor permission."
                "service_unavailable" -> "Sensor monitoring could not start. Open HealthSync and check sensor permission."
                "disabled" -> "Automatic measurement is off. Tap Measure for a reading."
                else -> if (displayed.bpm > 0) ageLabel else "No reading yet. Tap Measure and stay still."
            }
            return if (displayed.bpm > 0 && hrStatus in setOf("measuring", "no_contact", "no_events", "invalid_samples", "sensor_error", "service_unavailable", "disabled"))
                "$current $ageLabel" else current
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
    private var motionRegisteredAtMs = 0L
    private var lastMotionMs = -1L
    private var dayKey = ""
    private var hardwareStepsRegistered = false
    private var hrJob: Job? = null
    private var spo2Job: Job? = null
    private val measurementJobs = mutableMapOf<MeasurementKind, Job>()
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
    private var lastHrSentMs = 0L
    private var foregroundStarted = false
    private val oxygenSensors = mutableListOf<Sensor>()
    private var heartSensors = emptyList<Sensor>()
    private var activeHeartSensors = emptyList<Sensor>()
    private var heartRateAttempt: HeartRateAttemptDiagnostics? = null
    private val heartRatePreviewTracker = HeartRatePreviewTracker()
    private var lastDiagnosticRenderMs = -1L
    private val stepTracker = SignalProcessing.AntiFalsePositiveStepTracker { incrementSteps(it) }
    private val fallDetector = FallDetection { severity -> sendMsg(MessageType.FALL_DETECTED, FallDetectedPayload(severity)) }

    override fun onCreate() {
        super.onCreate()
        watchPrefs = WatchPreferences(this)
        latestSensorPreview = null
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createChannel()
        foregroundStarted = ForegroundPolicy.start(this, NOTIF_ID, buildNotif("Steps and sensor monitoring"))
        if (!foregroundStarted) { updateHrStatus("service_unavailable"); stopSelf(); return }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HealthSync:Measurement").apply { setReferenceCounted(false) }

        latestSteps = prefs.getInt("saved_steps", 0).coerceAtLeast(0)
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
        latestBpm = prefs.getInt("last_hr_bpm", -1)
        latestAcc = prefs.getInt("last_hr_accuracy", 0)
        latestBpmTimeMs = prefs.getLong("last_hr_time", 0L)
        if (latestAcc !in 1..3 || displayBpm() < 0) {
            latestBpm = -1; latestAcc = 0; latestBpmTimeMs = 0L
        }
        latestSensorPreview = HeartRateSensorPreview(prefs.getInt("last_sensor_hr_bpm", -1),
            prefs.getLong("last_sensor_hr_time", 0L)).takeIf { it.displayBpm(System.currentTimeMillis()) > 0 }
        findOxygenSensors()
        refreshHeartSensors()
        spo2Available = oxygenSensors.isNotEmpty()

        hardwareStepsRegistered = tryRegister(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL)
        tryRegister(Sensor.TYPE_ACCELEROMETER, 20_000)
        scheduleBootCount = WatchTimerStore.bootCount(this)
        val sameBoot = scheduleBootCount >= 0 && prefs.getInt("schedule_boot_count", -1) == scheduleBootCount
        for (kind in MeasurementKind.values()) {
            val interval = if (kind == MeasurementKind.HEART_RATE) watchPrefs.hrIntervalMs else watchPrefs.spo2IntervalMs
            measurementSchedule.configure(kind, interval, SystemClock.elapsedRealtime())
            if (sameBoot) measurementSchedule.restoreDue(kind, prefs.getLong("schedule_${kind.name}_interval", -1L),
                prefs.getLong("schedule_${kind.name}_due", 0L))
        }
        restartIntervalJobs()
        if (measurementJobs[MeasurementKind.HEART_RATE]?.isActive != true) updateHrStatus(initialHrStatus())
        scope.launch {
            while (isActive) { delay(30_000L); resetDayIfNeeded() }
        }
        broadcastSteps()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) { stopSelf(); return START_NOT_STICKY }
        // Permissions may have been granted since creation. Repeated Home starts leave
        // registered sensors and live measurement windows alone.
        if (!hardwareStepsRegistered) hardwareStepsRegistered = tryRegister(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL)
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
            ACTION_FORCE_MEASURE_HR -> requestMeasurement(MeasurementKind.HEART_RATE)
            ACTION_AUTO_HR -> handleScheduledAlarm(MeasurementKind.HEART_RATE, intent)
            ACTION_AUTO_OXYGEN -> handleScheduledAlarm(MeasurementKind.OXYGEN, intent)
            ACTION_SYNC_HEART_RATE -> {
                val bpm = displayBpm()
                if (bpm > 0) BluetoothClientService.sendRawMsg(SyncMessage(type = MessageType.HEART_RATE,
                    timestamp = latestBpmTimeMs, payload = gson.toJson(HeartRatePayload(bpm, latestAcc))), gson)
                broadcastHr()
            }
            ACTION_FORCE_MEASURE_SPO2 -> {
                if (spo2Available) requestMeasurement(MeasurementKind.OXYGEN)
                else {
                    latestSpo2 = -1f
                    sendBroadcast(Intent("com.healthsync.watch.SPO2_UPDATE").setPackage(packageName)
                        .putExtra("spo2", -1f).putExtra("available", false))
                }
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
        sensorManager.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
            val label = "${sensor.name} ${sensor.stringType}".lowercase(Locale.ROOT)
            if (sensor.type != Sensor.TYPE_HEART_RATE && sensor.type != Sensor.TYPE_HEART_BEAT &&
                (label.contains("spo2") || label.contains("oxygen saturation") || label.contains("blood oxygen"))) oxygenSensors.add(sensor)
        }
    }

    private fun tryRegister(type: Int, delay: Int): Boolean {
        val sensor = sensorManager.getDefaultSensor(type) ?: return false
        return try {
            val registeredAt = SystemClock.elapsedRealtime()
            sensorManager.registerListener(listener, sensor, delay).also { registered ->
                if (registered) when(type) {
                    Sensor.TYPE_STEP_COUNTER -> counterRegisteredAtMs = registeredAt
                    Sensor.TYPE_ACCELEROMETER -> { motionRegisteredAtMs = registeredAt; lastMotionMs = -1L }
                }
            }
        }
        catch (e: Exception) { Log.w(TAG, "Sensor $type unavailable: ${e.message}"); false }
    }

    private fun restartIntervalJobs() {
        val now = SystemClock.elapsedRealtime()
        for (kind in MeasurementKind.values()) {
            val configured = if (kind == MeasurementKind.HEART_RATE) watchPrefs.hrIntervalMs else watchPrefs.spo2IntervalMs
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

    private fun ensureIntervalJobs() {
        if (measurementSchedule.isEnabled(MeasurementKind.HEART_RATE) && heartRateAvailable && hrJob?.isActive != true) {
            hrJob = intervalJob(MeasurementKind.HEART_RATE)
        }
        if (measurementSchedule.isEnabled(MeasurementKind.OXYGEN) && spo2Available && spo2Job?.isActive != true) {
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
        if (kind == MeasurementKind.HEART_RATE) refreshHeartSensors()
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
                measurementSchedule.markFinished(kind, SystemClock.elapsedRealtime())
                updateMeasurementAlarms()
                updateHrStatus("permission_required"); return
            }
        }
        val acquisitionStartNanos = SystemClock.elapsedRealtimeNanos()
        val registeredSensors = mutableListOf<Sensor>()
        for (sensor in requestedSensors) {
            val registered = try {
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL).also {
                    if (kind == MeasurementKind.HEART_RATE) heartRateAttempt?.registration(sensorDescription(sensor), if (it) "registered / selected" else "registration returned false", selected = it)
                }
            } catch (e: Exception) {
                if (kind == MeasurementKind.HEART_RATE) heartRateAttempt?.registration(sensorDescription(sensor), "registration failed: ${e.javaClass.simpleName}")
                Log.w(TAG, "$kind sensor unavailable: ${e.message}"); false
            }
            if (registered) {
                registeredSensors += sensor
                // Match the original HealthSync path: use the firmware default, with a
                // fallback only if registration fails, rather than drive multiple HR HALs.
                if (kind == MeasurementKind.HEART_RATE) break
            }
        }
        // Missing sensors or denied permission must not hold the CPU awake.
        if (registeredSensors.isEmpty()) {
            measurementSchedule.markFinished(kind, SystemClock.elapsedRealtime())
            updateMeasurementAlarms()
            if (kind == MeasurementKind.HEART_RATE) {
                val status = if (requestedSensors.isEmpty()) "missing_sensor" else "sensor_error"
                publishHeartRateAttempt(status, completed = true)
                updateHrStatus(status)
            }
            return
        }
        measurementSensors[kind] = registeredSensors.toList()
        manualMeasurements[kind] = manualRequest
        if (kind == MeasurementKind.HEART_RATE) activeHeartSensors = registeredSensors.toList()
        measurementStartedNanos[kind] = acquisitionStartNanos
        measurementWindows.request(kind, acquisitionStartNanos / 1_000_000L, if(kind == MeasurementKind.HEART_RATE) HR_MEASURE_WINDOW_MS else MEASURE_WINDOW_MS)
        if (kind == MeasurementKind.HEART_RATE) {
            publishHeartRateAttempt("measuring", force = true)
            updateHrStatus("measuring")
        }
        refreshMeasurementWakeLock()
        measurementJobs[kind] = scope.launch {
            try {
                while (isActive) {
                    val remainingMs = measurementWindows.remainingMs(kind, SystemClock.elapsedRealtime())
                    if (remainingMs <= 0L) break
                    delay(minOf(1000L, remainingMs))
                }
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
                        ?: heartRateAttempt?.finalStatus(heartRatePreviewTracker.preview != null) ?: "no_events"
                    publishHeartRateAttempt(status, completed = true)
                    updateHrStatus(status)
                }
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
        for (kind in MeasurementKind.values()) {
            val interval = measurementSchedule.intervalMs(kind) ?: -1L
            val due = measurementSchedule.dueAtMs(kind) ?: 0L
            if (prefs.getLong("schedule_${kind.name}_interval", -1L) != interval || prefs.getLong("schedule_${kind.name}_due", 0L) != due) {
                editor.putLong("schedule_${kind.name}_interval", interval).putLong("schedule_${kind.name}_due", due)
                changed = true
            }
            val available = if (kind == MeasurementKind.HEART_RATE) heartRateAvailable else spo2Available
            val slot = AlarmSlot(interval, revision, due)
            if (interval <= 0L || !available) {
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
                    val timestampIssue = heartRatePreviewTracker.observe(event.values.firstOrNull(), event.accuracy,
                        event.timestamp, nowNanos, nowWall, measurementStartedNanos[MeasurementKind.HEART_RATE] ?: 0L)
                    val capturedPreview = heartRatePreviewTracker.preview
                    if (capturedPreview != null) {
                        latestSensorPreview = capturedPreview
                        prefs.edit().putInt("last_sensor_hr_bpm", capturedPreview.bpm)
                            .putLong("last_sensor_hr_time", capturedPreview.capturedAt).apply()
                    }
                    val rejection = HeartRatePolicy.frameRejection(event.values.firstOrNull(), event.accuracy,
                        event.timestamp, nowNanos, nowWall, measurementStartedNanos[MeasurementKind.HEART_RATE] ?: 0L,
                        lastHrEventNanos, timestampIssue)
                    heartRateAttempt?.frame(rejection, event.accuracy, event.timestamp, nowNanos,
                        measurementStartedNanos[MeasurementKind.HEART_RATE] ?: 0L, event.values.firstOrNull(),
                        timestampIssue, capturedPreview != null)
                    val status = when {
                        rejection == null -> "reading"
                        capturedPreview != null -> "unreliable_accuracy"
                        else -> "measuring"
                    }
                    publishHeartRateAttempt(status, force = hrStatus != status)
                    if (rejection != null) {
                        if (capturedPreview != null) finishMeasurement(MeasurementKind.HEART_RATE)
                        if (hrStatus != status) updateHrStatus(status)
                        return
                    }
                    val reading = event.values[0]
                    val capturedAt = HeartRatePolicy.capturedWallTime(event.timestamp, nowNanos, nowWall,
                        measurementStartedNanos[MeasurementKind.HEART_RATE] ?: 0L, lastHrEventNanos) ?: return
                    lastHrEventNanos = event.timestamp
                    val bpm = reading.toInt()
                    val changed = bpm != latestBpm
                    latestBpm = bpm; latestAcc = event.accuracy; latestBpmTimeMs = capturedAt
                    latestSensorPreview = null
                    hrStatus = "reading"
                    prefs.edit().putInt("last_hr_bpm", bpm).putInt("last_hr_accuracy", latestAcc).putLong("last_hr_time", capturedAt)
                        .remove("last_sensor_hr_bpm").remove("last_sensor_hr_time").apply()
                    finishMeasurement(MeasurementKind.HEART_RATE)
                    if (changed || nowWall - lastHrSentMs >= 5000L) {
                        lastHrSentMs = nowWall
                        sendMsg(MessageType.HEART_RATE, HeartRatePayload(bpm, latestAcc), capturedAt)
                        broadcastHr()
                    }
                }
                event.sensor.type == Sensor.TYPE_STEP_COUNTER -> {
                    val raw = event.values[0]
                    resetDayIfNeeded()
                    val delta = hardwareStepGuard.sample(raw, event.sensor.maximumRange, nowMotion,
                        SystemClock.elapsedRealtime(), counterRegisteredAtMs) ?: return
                    lastHardwareSteps = hardwareStepGuard.baselineCount
                    prefs.edit().putInt("last_hardware_steps", lastHardwareSteps)
                        .putLong("last_hardware_time_ms", hardwareStepGuard.baselineTimeMs).apply()
                    if (delta > 0) incrementSteps(delta)
                }
                event.sensor.type == Sensor.TYPE_ACCELEROMETER -> {
                    if (!validWorkoutMotionSample(event.values, event.sensor.maximumRange, nowMotion,
                        SystemClock.elapsedRealtime(), lastMotionMs, motionRegisteredAtMs)) {
                        if (nowMotion > lastMotionMs) { stepTracker.reset(); fallDetector.reset() }
                        return
                    }
                    lastMotionMs = nowMotion
                    if (!hardwareStepsRegistered) stepTracker.processAccel(event.values[0], event.values[1], event.values[2], nowMotion)
                    fallDetector.process(event.values[0], event.values[1], event.values[2], nowMotion)
                }
                else -> if (oxygenSensors.contains(event.sensor)) {
                    if (!measurementWindows.isActive(MeasurementKind.OXYGEN, SystemClock.elapsedRealtime())) return
                    val reading = event.values[0]
                    if (HeartRatePolicy.validOxygen(reading, event.accuracy)) {
                        val capturedAt = HeartRatePolicy.capturedWallTime(event.timestamp, nowNanos, nowWall,
                            measurementStartedNanos[MeasurementKind.OXYGEN] ?: 0L, lastOxygenEventNanos) ?: return
                        lastOxygenEventNanos = event.timestamp
                        latestSpo2 = reading; latestSpo2TimeMs = capturedAt
                        finishMeasurement(MeasurementKind.OXYGEN)
                        sendMsg(MessageType.SPO2, SpO2Payload(reading), capturedAt)
                        sendBroadcast(Intent("com.healthsync.watch.SPO2_UPDATE").setPackage(packageName)
                            .putExtra("spo2", reading).putExtra("available", true))
                    }
                }
            }
        }
    }

    private fun resetDayIfNeeded() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        if (dayKey == today) return
        dayKey = today; latestSteps = 0; lastHardwareSteps = -1; hardwareStepGuard.reset(); stepTracker.reset()
        prefs.edit().putString("saved_date", today).putInt("saved_steps", 0).putInt("last_hardware_steps", -1)
            .putLong("last_hardware_time_ms", 0L).apply()
        sendMsg(MessageType.STEPS, StepsPayload(0, 0.0))
        broadcastSteps()
    }

    private fun incrementSteps(delta: Int) {
        if (delta <= 0) return
        resetDayIfNeeded()
        latestSteps = (latestSteps.toLong() + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        latestLifetimeSteps += delta.toLong()
        prefs.edit().putInt("saved_steps", latestSteps).putLong("lifetime_steps", latestLifetimeSteps).apply()
        val bucket = System.currentTimeMillis() / 300_000L * 300_000L
        WatchDatabaseHelper.getInstance(this).insertOrUpdateStepBucket(bucket, delta, delta * 0.04)
        sendMsg(MessageType.STEPS, StepsPayload(latestSteps, latestSteps * 0.04))
        broadcastSteps()
    }

    private inline fun <reified T> sendMsg(type: MessageType, payload: T, timestamp: Long = System.currentTimeMillis()) {
        BluetoothClientService.sendRawMsg(SyncMessage(type = type, timestamp = timestamp, payload = gson.toJson(payload)), gson)
    }
    private fun initialHrStatus() = when {
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
    private fun broadcastSteps() = sendBroadcast(Intent("com.healthsync.watch.STEP_UPDATE").setPackage(packageName)
        .putExtra("steps", latestSteps).putExtra("calories", (latestSteps * 0.04).toFloat()))

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

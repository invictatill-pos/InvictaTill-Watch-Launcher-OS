package com.healthsync.watch.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthsync.watch.algorithm.SportAlgorithms
import com.healthsync.watch.algorithm.*
import com.healthsync.watch.data.*
import com.healthsync.watch.ui.ActiveWorkoutActivity
import com.healthsync.watch.ui.StrengthWorkoutActivity
import com.healthsync.watch.sensor.ForegroundPolicy
import kotlinx.coroutines.*
import java.util.Locale
import java.util.UUID

class WorkoutTrackingService : Service(), LocationListener, SensorEventListener {
    companion object {
        private const val TAG = "WorkoutTracking"
        private const val NOTIFICATION_ID = 400
        private const val CHANNEL_ID = "workout_channel"
        private val historyCacheLock = Any()
        const val ACTION_START = "com.healthsync.watch.START_WORKOUT"
        const val ACTION_STOP = "com.healthsync.watch.STOP_WORKOUT"
        const val ACTION_PAUSE = "com.healthsync.watch.PAUSE_WORKOUT"
        const val ACTION_RESUME = "com.healthsync.watch.RESUME_WORKOUT"
        const val ACTION_REQUEST_UPDATE = "com.healthsync.watch.REQUEST_WORKOUT_UPDATE"
        const val ACTION_SET_REPS = "com.healthsync.watch.SET_WORKOUT_REPS"
        const val ACTION_UPDATE = "com.healthsync.watch.WORKOUT_UPDATE"
        const val ACTION_SAVE_FAILED = "com.healthsync.watch.WORKOUT_SAVE_FAILED"
        const val EXTRA_ACTIVITY_TYPE = "activity_type"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_DISTANCE = "distance"
        const val EXTRA_METRIC_1 = "metric_1"
        const val EXTRA_METRIC_2 = "metric_2"
        const val EXTRA_IS_PAUSED = "is_paused"
        const val EXTRA_CALORIES = "calories"
        const val EXTRA_STEPS = "steps"
        const val EXTRA_BPM = "bpm"
        const val EXTRA_CADENCE = "cadence"
        const val EXTRA_REPS = "reps"
        const val EXTRA_SETS = "sets"
        const val ACTION_GPS_STATUS = "com.healthsync.watch.GPS_STATUS"
        const val EXTRA_GPS_LOCKED = "gps_locked"
        const val EXTRA_GPS_SATS = "gps_sats"
        const val EXTRA_ROUTE_COUNT = "route_count"
        const val EXTRA_GPS_STATE = "gps_state"
        const val EXTRA_GPS_ACCURACY = "gps_accuracy"
        const val EXTRA_LOCATION_CHECKED = "location_permission_checked"

        @Volatile var isActive = false; private set
        @Volatile var activeActivityType = "Walk"; private set
        @Volatile var activeStartTime = 0L; private set
        @Volatile var activeStartSteps = 0; private set
        @Volatile var currentlyPaused = false; private set
        @Volatile var currentLocationState = WorkoutLocationState.NOT_USED; private set
    }

    // Sensor, location, command and ticker state all run on the main looper.
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val gson = Gson()
    private val sessionPrefs by lazy { getSharedPreferences("watch_active_workout", Context.MODE_PRIVATE) }
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var watchPrefs: WatchPreferences
    private var tracking = false
    private var paused = false
    private var activityType = "Walk"
    private var sessionId = ""
    private var startTimeMs = 0L
    private var elapsedBeforeAnchor = 0L
    private var anchorElapsedMs = 0L
    private var lastStepTotal = -1L
    private var workoutSteps = 0
    private var totalDistanceMeters = 0f
    private var fallbackDistanceMeters = 0f
    private var lastLocation: Location? = null
    private var lastLocationAnchorElapsed = -1L
    private var lastGpsFixElapsed = -1L
    private var lastFixElapsed = -1L
    private var gpsLocked = false
    private var gpsSatCount = 0
    private var lastLocationProvider = ""
    private var lastReportedAccuracy = -1f
    private val registeredLocationProviders = mutableSetOf<String>()
    private val locationProviderStart = mutableMapOf<String, Long>()
    private val routePoints = mutableListOf<LatLngPoint>()
    private var hrSum = 0L
    private var hrCount = 0L
    private var lastHrSampleTime = 0L
    private var peakHr = 0
    private var currentReps = 0
    private var currentSwings = 0
    private var currentSets = 0
    private var gyroX = 0f; private var gyroY = 0f; private var gyroZ = 0f
    private var lastGyroMs = -1L
    private var lastAccelMs = -1L
    private var motionRegisteredAt = 0L
    private var ticker: Job? = null
    private val cadenceTracker = SportAlgorithms.CadenceTracker()
    private val repDetector = SportAlgorithms.HomeWorkoutDetector { currentReps = it }
    private val swingDetector = SportAlgorithms.BadmintonDetector { currentSwings = it }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        watchPrefs = WatchPreferences(this)
        createNotificationChannel()
        restoreSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService request, including a stop, meets its deadline.
        val requestedType = normalizeActivityType(intent?.getStringExtra(EXTRA_ACTIVITY_TYPE) ?: activityType)
        val outdoors = workoutUsesLocation(requestedType)
        if (!ForegroundPolicy.start(this, NOTIFICATION_ID, buildNotification(), includeLocation = outdoors)) {
            checkpoint(); clearPublishedState(); stopSelf(); return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> if (!tracking) startTracking(normalizeActivityType(intent.getStringExtra(EXTRA_ACTIVITY_TYPE) ?: "Walk"))
            ACTION_PAUSE -> if (tracking && !paused) {
                collectSteps(); elapsedBeforeAnchor = activeDurationMs(); paused = true
                lastLocation = null; gpsLocked = false; lastFixElapsed = -1L; lastGpsFixElapsed = -1L
                cadenceTracker.reset(); unregisterTrackingSensors(); publishState(); checkpoint()
            }
            ACTION_RESUME -> if (tracking && paused) {
                paused = false; anchorElapsedMs = SystemClock.elapsedRealtime(); lastStepTotal = lifetimeSteps()
                lastLocation = null; gpsLocked = false; lastFixElapsed = -1L; lastGpsFixElapsed = -1L
                repDetector.reset(currentReps); swingDetector.reset(currentSwings)
                registerTrackingSensors(); publishState(); checkpoint()
            }
            ACTION_REQUEST_UPDATE -> if (tracking && !paused) registerLocationProviders()
            ACTION_SET_REPS -> if (tracking && activityType == "Home Workout") {
                currentReps = intent.getIntExtra(EXTRA_REPS, currentReps).coerceIn(0, 100_000)
                currentSets = intent.getIntExtra(EXTRA_SETS, currentSets).coerceIn(0, 10_000)
                repDetector.reset(currentReps); checkpoint()
            }
            ACTION_STOP -> {
                if (tracking && !saveWorkout()) {
                    paused = true; lastLocation = null; gpsLocked = false; lastFixElapsed = -1L; lastGpsFixElapsed = -1L; cadenceTracker.reset()
                    unregisterTrackingSensors()
                    publishState(); checkpoint(); broadcastUpdate(); broadcastGpsStatus()
                    return START_STICKY
                }
                tracking = false; clearPublishedState(); cleanupTracking()
                stopForeground(true); stopSelf(); return START_NOT_STICKY
            }
        }
        if (!tracking) { clearPublishedState(); stopForeground(true); stopSelf(); return START_NOT_STICKY }
        // Automatic heart rate follows the chosen sensor interval, including Off, during workouts.
        if (!paused) runCatching {
            ContextCompat.startForegroundService(this, Intent(this, SensorCollectorService::class.java))
        }.onFailure { Log.w(TAG, "Sensor collection could not start", it) }
        publishState()
        startTicker()
        broadcastUpdate(); broadcastGpsStatus()
        return START_STICKY
    }

    private fun normalizeActivityType(input: String) = when (input.lowercase(Locale.ROOT).trim()) {
        "run", "running" -> "Run"
        "home workout", "home_workout", "strength" -> "Home Workout"
        "badminton" -> "Badminton"
        "cycling", "cycle", "bike", "biking" -> "Cycling"
        "basketball" -> "Basketball"; "cricket" -> "Cricket"; "yoga" -> "Yoga"
        else -> "Walk"
    }

    private fun startTracking(type: String) {
        tracking = true; paused = false; activityType = type
        sessionId = "W_${UUID.randomUUID()}"
        startTimeMs = System.currentTimeMillis(); anchorElapsedMs = SystemClock.elapsedRealtime(); elapsedBeforeAnchor = 0L
        workoutSteps = 0; activeStartSteps = SensorCollectorService.latestSteps; lastStepTotal = lifetimeSteps()
        totalDistanceMeters = 0f; fallbackDistanceMeters = 0f; routePoints.clear(); lastLocation = null; gpsLocked = false; gpsSatCount = 0
        lastFixElapsed = -1L; lastGpsFixElapsed = -1L; lastLocationProvider = ""; lastReportedAccuracy = -1f
        hrSum = 0L; hrCount = 0L; lastHrSampleTime = 0L; peakHr = 0
        currentReps = 0; currentSwings = 0; currentSets = 0
        cadenceTracker.reset(); repDetector.reset(); swingDetector.reset()
        registerTrackingSensors(); checkpoint()
    }

    @SuppressLint("MissingPermission")
    private fun registerTrackingSensors() {
        motionRegisteredAt = SystemClock.elapsedRealtime()
        lastAccelMs = -1L; lastGyroMs = -1L
        if (activityType in setOf("Home Workout", "Badminton", "Basketball", "Cricket")) {
            try {
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensorManager.registerListener(this, it, 20_000) }
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensorManager.registerListener(this, it, 20_000) }
            } catch (e: Exception) { Log.w(TAG, "Motion sensors unavailable: ${e.message}") }
        }
        registerLocationProviders()
    }

    @SuppressLint("MissingPermission")
    private fun registerLocationProviders() {
        if (!tracking || paused || !workoutUsesLocation(activityType)) return
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            runCatching { locationManager.removeUpdates(this) }
            registeredLocationProviders.clear(); locationProviderStart.clear(); lastLocation = null; gpsLocked = false; lastFixElapsed = -1L; lastGpsFixElapsed = -1L
            broadcastGpsStatus(); return
        }
        // Register disabled providers too: Android resumes updates when the user enables Location.
        // Framework providers work on standalone watches without Google Play Services.
        val availableProviders = try { locationManager.allProviders }
        catch (e: RuntimeException) {
            Log.w(TAG, "Location providers unavailable: ${e.message}")
            return
        }
        val providers = availableProviders.filter { provider ->
            provider != LocationManager.PASSIVE_PROVIDER &&
                (fine || provider == LocationManager.NETWORK_PROVIDER)
        }
        val obsolete = registeredLocationProviders - providers.toSet()
        if (obsolete.isNotEmpty()) {
            runCatching { locationManager.removeUpdates(this) }
            registeredLocationProviders.clear(); locationProviderStart.clear(); lastLocation = null; lastFixElapsed = -1L; lastGpsFixElapsed = -1L
        }
        providers.distinct().filterNot { it in registeredLocationProviders }.forEach { provider ->
            try {
                locationManager.requestLocationUpdates(provider, 1000L, 0f, this, Looper.getMainLooper())
                registeredLocationProviders.add(provider)
                locationProviderStart[provider] = SystemClock.elapsedRealtime()
                Log.d(TAG, "Tracking location on provider: $provider")
            } catch (e: Exception) { Log.w(TAG, "$provider unavailable: ${e.message}") }
        }
        // Fresh live fixes establish route origin; old cached positions add no distance.
        broadcastGpsStatus()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var tick = 0
            while (tracking && isActive) {
                collectSteps()
                if (!paused) {
                    val sampleTime = SensorCollectorService.latestBpmTimeMs
                    val bpm = SensorCollectorService.freshBpm()
                    if (bpm > 0 && sampleTime >= startTimeMs && sampleTime != lastHrSampleTime) {
                        hrSum += bpm; hrCount++; peakHr = maxOf(peakHr, bpm); lastHrSampleTime = sampleTime
                    }
                }
                if (tick % 10 == 0 && !paused) registerLocationProviders()
                broadcastGpsStatus()
                broadcastUpdate()
                if (tick % 10 == 0) checkpoint()
                tick++; delay(1000L)
            }
        }
    }

    private fun lifetimeSteps(): Long = if (SensorCollectorService.latestLifetimeSteps >= 0) SensorCollectorService.latestLifetimeSteps
        else getSharedPreferences("HealthSync_Steps", Context.MODE_PRIVATE).getLong("lifetime_steps", -1L)

    private fun collectSteps() {
        val total = lifetimeSteps()
        val delta = if (total >= 0 && lastStepTotal >= 0) (total - lastStepTotal).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
        if (!paused && delta > 0) {
            workoutSteps = (workoutSteps.toLong() + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (lastLocation == null || lastFixElapsed < 0 || SystemClock.elapsedRealtime() - lastFixElapsed > 30_000L) {
                fallbackDistanceMeters += when (activityType) {
                    "Walk" -> SportAlgorithms.calculateWalkDistanceMeters(delta, watchPrefs.userHeightCm / 100.0).toFloat()
                    "Run" -> SportAlgorithms.calculateRunFallbackDistance(delta, watchPrefs.userHeightCm / 100.0).toFloat()
                    else -> 0f
                }
            }
        }
        if (total >= 0) lastStepTotal = total
        if (!paused) cadenceTracker.recordSteps(SystemClock.elapsedRealtime(), delta)
    }

    private fun activeDurationMs(): Long = elapsedBeforeAnchor + if (paused) 0L else
        (SystemClock.elapsedRealtime() - anchorElapsedMs).coerceAtLeast(0L)

    private fun distanceMeters(): Float = when (activityType) {
        "Walk", "Run" -> totalDistanceMeters + fallbackDistanceMeters
        "Cycling", "Basketball", "Cricket" -> totalDistanceMeters
        else -> 0f
    }

    private fun broadcastUpdate() {
        if (!tracking) return
        val duration = activeDurationMs(); val distance = distanceMeters()
        val bpm = SensorCollectorService.freshBpm(); val cadence = if (paused) 0 else cadenceTracker.currentCadence(SystemClock.elapsedRealtime())
        val metric1 = when (activityType) {
            "Run" -> if (distance >= 10f) String.format(Locale.getDefault(), "%.2f min/km", SportAlgorithms.calculatePaceMinPerKm(distance.toDouble(), duration / 1000)) else "-- min/km"
            "Cycling" -> String.format(Locale.getDefault(), "%.1f km/h", SportAlgorithms.calculateSpeedKmh(distance.toDouble(), duration / 1000))
            "Home Workout" -> "$currentReps Reps"
            "Badminton", "Cricket" -> "$currentSwings Swings"
            "Basketball" -> "$currentSwings Moves"
            "Yoga" -> "${duration / 60_000} min"
            else -> String.format(Locale.getDefault(), "%.0f", distance)
        }
        val metric2 = when (activityType) {
            "Run" -> "$cadence SPM"
            "Badminton" -> if (peakHr > 0) "Peak: $peakHr BPM" else "-- BPM"
            else -> if (bpm > 0) "$bpm BPM" else "-- BPM"
        }
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).apply {
            putExtra(EXTRA_DURATION, duration); putExtra(EXTRA_DISTANCE, distance)
            putExtra(EXTRA_METRIC_1, metric1); putExtra(EXTRA_METRIC_2, metric2); putExtra(EXTRA_IS_PAUSED, paused)
            putExtra(EXTRA_ACTIVITY_TYPE, activityType); putExtra(EXTRA_CALORIES, SportAlgorithms.estimateCalories(activityType, duration, watchPrefs.userWeightKg))
            putExtra(EXTRA_STEPS, workoutSteps); putExtra(EXTRA_BPM, bpm); putExtra(EXTRA_CADENCE, cadence)
            putExtra(EXTRA_REPS, currentReps); putExtra(EXTRA_SETS, currentSets)
            putExtra(EXTRA_ROUTE_COUNT, routePoints.size)
        })
    }

    private fun saveWorkout(): Boolean {
        collectSteps()
        val activeMs = activeDurationMs()
        val endMs = System.currentTimeMillis().coerceAtLeast(startTimeMs)
        val avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else 0
        val distance = distanceMeters(); val calories = SportAlgorithms.estimateCalories(activityType, activeMs, watchPrefs.userWeightKg)
        val count = if (activityType in setOf("Badminton", "Basketball", "Cricket")) currentSwings else currentReps
        val session = WorkoutSessionPayload(sessionId, activityType.uppercase(Locale.ROOT).replace(" ", "_"), startTimeMs, endMs,
            (activeMs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), distance, calories, workoutSteps, count, avgHr, gson.toJson(routePoints))
        if (!WatchDatabaseHelper.getInstance(this).insertWorkoutSession(session, false)) {
            elapsedBeforeAnchor = activeMs
            sendBroadcast(Intent(ACTION_SAVE_FAILED).setPackage(packageName))
            return false
        }
        val payload = WorkoutPayload(activityType, startTimeMs, endMs, distance, calories, avgHr, workoutSteps, count, routePoints.toList(), durationSeconds = session.duration_sec)
        val prefs = getSharedPreferences("watch_workouts", Context.MODE_PRIVATE)
        // SQLite is authoritative. Keep the legacy summary cache small and off the UI thread.
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(historyCacheLock) {
                try {
                    val type = object : TypeToken<MutableList<WorkoutPayload>>() {}.type
                    val history = runCatching { gson.fromJson<MutableList<WorkoutPayload>>(prefs.getString("history", "[]"), type) }.getOrNull() ?: mutableListOf()
                    history.removeAll { it.startTime == payload.startTime }
                    history.add(0, payload)
                    prefs.edit().putString("history", gson.toJson(history.take(50).map { it.copy(route = emptyList()) })).commit()
                } catch (e: Exception) { Log.w(TAG, "History cache could not save: ${e.message}") }
            }
        }
        sessionPrefs.edit().clear().commit()
        sendBroadcast(Intent("com.healthsync.watch.WORKOUT_SAVED").setPackage(packageName)
            .putExtra("payload_json", gson.toJson(payload)).putExtra("duration_sec", session.duration_sec))
        BluetoothClientService.syncBatchedPayloads(applicationContext)
        return true
    }

    private fun checkpoint() {
        if (!tracking) return
        sessionPrefs.edit().putBoolean("active", true).putString("session_id", sessionId).putString("activity", activityType)
            .putLong("start_time", startTimeMs).putLong("active_elapsed", activeDurationMs()).putInt("start_steps", activeStartSteps)
            .putInt("steps", workoutSteps).putFloat("distance", totalDistanceMeters).putFloat("fallback_distance", fallbackDistanceMeters).putLong("hr_sum", hrSum).putLong("hr_count", hrCount)
            .putInt("peak_hr", peakHr).putInt("reps", currentReps).putInt("swings", currentSwings).putInt("sets", currentSets)
            .putString("route", gson.toJson(routePoints)).commit()
    }

    private fun restoreSession() {
        if (!sessionPrefs.getBoolean("active", false)) return
        sessionId = sessionPrefs.getString("session_id", "") ?: ""
        startTimeMs = sessionPrefs.getLong("start_time", 0L)
        if (sessionId.isBlank() || startTimeMs <= 0) { sessionPrefs.edit().clear().apply(); return }
        activityType = normalizeActivityType(sessionPrefs.getString("activity", "Walk") ?: "Walk")
        tracking = true; paused = true // Downtime never adds workout time or distance.
        elapsedBeforeAnchor = sessionPrefs.getLong("active_elapsed", 0L).coerceAtLeast(0L)
        anchorElapsedMs = SystemClock.elapsedRealtime(); activeStartSteps = sessionPrefs.getInt("start_steps", 0)
        lastStepTotal = lifetimeSteps(); workoutSteps = sessionPrefs.getInt("steps", 0).coerceAtLeast(0)
        totalDistanceMeters = sessionPrefs.getFloat("distance", 0f).takeIf { it.isFinite() && it >= 0f } ?: 0f
        fallbackDistanceMeters = sessionPrefs.getFloat("fallback_distance", 0f).takeIf { it.isFinite() && it >= 0f } ?: 0f
        hrSum = sessionPrefs.getLong("hr_sum", 0L); hrCount = sessionPrefs.getLong("hr_count", 0L)
        peakHr = sessionPrefs.getInt("peak_hr", 0); currentReps = sessionPrefs.getInt("reps", 0)
        currentSwings = sessionPrefs.getInt("swings", 0); currentSets = sessionPrefs.getInt("sets", 0)
        runCatching {
            val type = object : TypeToken<List<LatLngPoint>>() {}.type
            gson.fromJson<List<LatLngPoint>>(sessionPrefs.getString("route", "[]"), type)?.let { routePoints.addAll(it.takeLast(5000)) }
        }
        repDetector.reset(currentReps); swingDetector.reset(currentSwings)
        publishState()
    }

    override fun onLocationChanged(location: Location) {
        if (!tracking || paused || !workoutUsesLocation(activityType) ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val fixElapsed = workoutFixElapsedTime(location.elapsedRealtimeNanos, location.time, nowElapsed, System.currentTimeMillis())
        val accuracy = location.accuracy.takeIf { location.hasAccuracy() }
        if (fixElapsed == null || !validWorkoutLocation(location.latitude, location.longitude, accuracy, fixElapsed, nowElapsed)) {
            // A discarded new fix breaks continuity; a secondary network callback does not break GPS.
            if (shouldBreakDiscardedWorkoutRoute(location.provider, lastLocation?.provider, fixElapsed, lastFixElapsed)) lastLocation = null
            if (lastFixElapsed < 0 || nowElapsed - lastFixElapsed > 30_000L)
                lastReportedAccuracy = accuracy?.takeIf { it.isFinite() } ?: -1f
            broadcastGpsStatus(); return
        }
        val acceptedAccuracy = accuracy ?: return
        val isGps = location.provider == LocationManager.GPS_PROVIDER
        // Avoid alternating network/GPS points and drawing a false zigzag while GPS is fresh.
        if (!isGps && lastGpsFixElapsed >= 0 && nowElapsed - lastGpsFixElapsed in 0L..15_000L) return
        if (!validWorkoutFixOrder(fixElapsed, locationProviderStart[location.provider] ?: return, lastFixElapsed)) return
        val routeTime = workoutRouteWallTime(fixElapsed, nowElapsed, System.currentTimeMillis()) ?: run { lastLocation = null; return }
        val previous = lastLocation
        val dt = if (previous == null) 0.0 else (fixElapsed - lastLocationAnchorElapsed) / 1000.0
        val segment = previous?.distanceTo(location) ?: 0f
        val decision = if (previous == null || previous.provider != location.provider ||
            (lastFixElapsed >= 0 && fixElapsed - lastFixElapsed > 30_000L)) RouteSegmentDecision.ORIGIN
            else workoutRouteSegmentDecision(dt, segment, previous.accuracy, acceptedAccuracy, activityType == "Cycling")
        if (decision == RouteSegmentDecision.REJECT) { lastLocation = null; return }
        markLocationFix(location, fixElapsed, isGps)
        if (decision == RouteSegmentDecision.MOVE || decision == RouteSegmentDecision.ORIGIN) {
            if (decision == RouteSegmentDecision.MOVE) totalDistanceMeters += segment
            lastLocation = Location(location)
            lastLocationAnchorElapsed = fixElapsed
            routePoints.add(LatLngPoint(location.latitude, location.longitude, routeTime, segmentStart = decision == RouteSegmentDecision.ORIGIN))
            if (routePoints.size > 5000) {
                val thinned = thinWorkoutRoute(routePoints)
                routePoints.clear()
                routePoints.addAll(thinned)
            }
            if (decision == RouteSegmentDecision.ORIGIN) checkpoint()
        }
        broadcastGpsStatus()
    }

    private fun markLocationFix(location: Location, nowElapsed: Long, gps: Boolean) {
        lastFixElapsed = nowElapsed
        if (gps) lastGpsFixElapsed = nowElapsed
        lastLocationProvider = location.provider.orEmpty()
        gpsLocked = gps
        lastReportedAccuracy = location.accuracy
        gpsSatCount = (location.extras?.getInt("satellites", 0) ?: 0).coerceAtLeast(0)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!tracking || paused || event == null || event.values.size < 3) return
        val now = event.timestamp / 1_000_000L
        val previous = if (event.sensor.type == Sensor.TYPE_GYROSCOPE) lastGyroMs else lastAccelMs
        if (!validWorkoutMotionSample(event.values, event.sensor.maximumRange, now, SystemClock.elapsedRealtime(), previous, motionRegisteredAt)) {
            if (now > previous && now >= motionRegisteredAt) {
                repDetector.reset(currentReps); swingDetector.reset(currentSwings)
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) lastGyroMs = -1L
            }
            return
        }
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            gyroX = event.values[0]; gyroY = event.values[1]; gyroZ = event.values[2]; lastGyroMs = now
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            lastAccelMs = now
            val freshGyro = lastGyroMs >= 0 && now - lastGyroMs in 0L..500L
            val gx = if (freshGyro) gyroX else 0f; val gy = if (freshGyro) gyroY else 0f; val gz = if (freshGyro) gyroZ else 0f
            when (activityType) {
                "Home Workout" -> repDetector.processSensor(event.values[0], event.values[1], event.values[2], gx, gy, gz, now)
                "Badminton", "Basketball", "Cricket" -> swingDetector.processSensor(event.values[0], event.values[1], event.values[2], gx, gy, gz, now)
            }
        }
    }

    private fun publishState() {
        isActive = tracking; activeActivityType = activityType; activeStartTime = startTimeMs; currentlyPaused = paused
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }
    private fun clearPublishedState() { isActive = false; currentlyPaused = false; activeStartTime = 0L; currentLocationState = WorkoutLocationState.NOT_USED }
    private fun broadcastGpsStatus() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val available = runCatching { locationManager.allProviders.filter { it != LocationManager.PASSIVE_PROVIDER }.toSet() }.getOrDefault(emptySet())
        val enabled = available.filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }.toSet()
        currentLocationState = workoutLocationState(workoutUsesLocation(activityType), paused, fine, coarse, available, enabled,
            registeredLocationProviders, lastFixElapsed, SystemClock.elapsedRealtime(), lastLocationProvider)
        gpsLocked = currentLocationState == WorkoutLocationState.GPS_FIX
        sendBroadcast(Intent(ACTION_GPS_STATUS).setPackage(packageName)
            .putExtra(EXTRA_GPS_LOCKED, gpsLocked).putExtra(EXTRA_GPS_SATS, gpsSatCount)
            .putExtra(EXTRA_GPS_STATE, currentLocationState.name).putExtra(EXTRA_GPS_ACCURACY, lastReportedAccuracy)
            .putExtra(EXTRA_ROUTE_COUNT, routePoints.size))
    }

    private fun buildNotification(): Notification {
        val screen = if (activityType == "Home Workout") StrengthWorkoutActivity::class.java else ActiveWorkoutActivity::class.java
        val open = PendingIntent.getActivity(this, 0, Intent(this, screen).putExtra(EXTRA_ACTIVITY_TYPE, activityType)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pauseAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val toggle = PendingIntent.getService(this, 1, Intent(this, WorkoutTrackingService::class.java).setAction(pauseAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("$activityType workout")
            .setContentText(if (paused) "Paused · tap to return" else "Tracking · tap to return")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, if (paused) "Resume" else "Pause", toggle).build()
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Workout Tracking", NotificationManager.IMPORTANCE_LOW))
    }
    private fun cleanupTracking() {
        ticker?.cancel(); ticker = null
        unregisterTrackingSensors()
    }
    private fun unregisterTrackingSensors() {
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        registeredLocationProviders.clear()
        locationProviderStart.clear(); lastAccelMs = -1L; lastGyroMs = -1L
        gyroX = 0f; gyroY = 0f; gyroZ = 0f
        sensorManager.unregisterListener(this)
    }
    override fun onTaskRemoved(rootIntent: Intent?) { checkpoint(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() { checkpoint(); cleanupTracking(); scope.cancel(); clearPublishedState(); super.onDestroy() }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onProviderEnabled(provider: String) {
        if (provider in registeredLocationProviders) locationProviderStart[provider] = SystemClock.elapsedRealtime()
        if (tracking && !paused) registerLocationProviders()
        broadcastGpsStatus()
    }
    override fun onProviderDisabled(provider: String) {
        if (lastLocationProvider == provider) { gpsLocked = false; lastLocation = null; lastFixElapsed = -1L; lastGpsFixElapsed = -1L }
        broadcastGpsStatus()
    }
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null
}

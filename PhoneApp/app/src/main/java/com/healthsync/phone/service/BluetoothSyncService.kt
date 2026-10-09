package com.healthsync.phone.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.healthsync.phone.data.FitnessRepository
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.JsonLineReader
import com.healthsync.phone.data.HeartRateFeedback
import com.healthsync.phone.data.SyncValidation
import com.healthsync.phone.data.SensorIntervalPolicy
import com.healthsync.phone.data.heartRateFeedbackFromJson
import com.healthsync.phone.data.currentHeartRateFeedback
import com.healthsync.phone.MainActivity
import com.healthsync.phone.data.model.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothSyncService : android.app.Service() {

    companion object {
        private const val TAG = "BTSyncService"
        val SERVICE_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        private const val CHANNEL_ID = "bt_sync_channel"
        private const val NOTIF_ID = 1001
        private const val FALL_CHANNEL_ID = "fall_alerts"

        // Intent actions
        const val ACTION_SEND_NOTIFICATION = "com.healthsync.phone.SEND_NOTIFICATION"
        const val ACTION_NOTIFICATION_REMOVED = "com.healthsync.phone.NOTIFICATION_REMOVED"
        const val ACTION_NOTIFICATION_SNAPSHOT = "com.healthsync.phone.NOTIFICATION_SNAPSHOT"
        const val ACTION_SEND_CALL         = "com.healthsync.phone.SEND_CALL"
        const val ACTION_SEND_SETTINGS     = "com.healthsync.phone.SEND_SETTINGS"
        const val ACTION_DISCONNECT        = "com.healthsync.phone.DISCONNECT"
        const val ACTION_RECONNECT         = "com.healthsync.phone.RECONNECT"
        const val ACTION_FORCE_MEASURE_HR  = "com.healthsync.phone.FORCE_MEASURE_HR"
        const val ACTION_FORCE_MEASURE_SPO2 = "com.healthsync.phone.FORCE_MEASURE_SPO2"
        const val EXTRA_PAYLOAD            = "payload"

        // ── Live connection state (observed by UI) ────────────────────────────
        private val _connectionState = MutableStateFlow(ConnectionInfo())
        val connectionState: StateFlow<ConnectionInfo> = _connectionState.asStateFlow()
        private val _heartRateFeedback = MutableStateFlow<HeartRateFeedback?>(null)
        val heartRateFeedback: StateFlow<HeartRateFeedback?> = _heartRateFeedback.asStateFlow()

        // ── Writer for outgoing messages ──────────────────────────────────────
        @Volatile private var sharedWriter: PrintWriter? = null
        @Volatile private var activeInstance: BluetoothSyncService? = null

        fun sendDirectSyncMessage(msg: SyncMessage): Boolean {
            return activeInstance?.sendRaw(msg) ?: false
        }

        // ── Public helpers ────────────────────────────────────────────────────
        fun sendNotificationToWatch(context: Context, payload: NotificationPayload) {
            val preferences = PhonePreferences(context)
            if (preferences.connectionPaused || !preferences.isAppAllowed(payload.packageName)) return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BluetoothSyncService::class.java).apply {
                        action = ACTION_SEND_NOTIFICATION
                        putExtra(EXTRA_PAYLOAD, Gson().toJson(payload))
                    }
                )
            } catch (e: Exception) { Log.e(TAG, "sendNotification error: ${e.message}") }
        }

        fun sendNotificationRemovedToWatch(context: Context, notificationKey: String) {
            if (notificationKey.isBlank() || PhonePreferences(context).connectionPaused) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, BluetoothSyncService::class.java).apply {
                    action = ACTION_NOTIFICATION_REMOVED
                    putExtra(EXTRA_PAYLOAD, Gson().toJson(NotificationRemovedPayload(notificationKey)))
                })
            } catch (e: Exception) { Log.w(TAG, "Notification removal sync unavailable", e) }
        }

        fun sendNotificationSnapshotToWatch(context: Context, activeKeys: List<String>) {
            if (PhonePreferences(context).connectionPaused) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, BluetoothSyncService::class.java).apply {
                    action = ACTION_NOTIFICATION_SNAPSHOT
                    putExtra(EXTRA_PAYLOAD, Gson().toJson(NotificationSnapshotPayload(activeKeys.take(1_000))))
                })
            } catch (e: Exception) { Log.w(TAG, "Notification snapshot sync unavailable", e) }
        }

        fun sendCallToWatch(context: Context, payload: CallPayload) {
            if (PhonePreferences(context).connectionPaused) return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BluetoothSyncService::class.java).apply {
                        action = ACTION_SEND_CALL
                        putExtra(EXTRA_PAYLOAD, Gson().toJson(payload))
                    }
                )
            } catch (e: Exception) { Log.e(TAG, "sendCall error: ${e.message}") }
        }

        fun sendSettingsToWatch(context: Context, payload: com.healthsync.phone.data.model.WatchSettingsPayload) {
            try {
                // An explicit Apply is a new user decision, even when the displayed choices match.
                val snapshot = PhonePreferences(context.applicationContext).updateSensorIntervalsLocally(
                    payload.hrIntervalMs, payload.spo2IntervalMs, forceRevision = true)
                val revised = payload.copy(hrIntervalMs = snapshot.hrIntervalMs, spo2IntervalMs = snapshot.spo2IntervalMs,
                    sensorSettingsRevision = snapshot.revision)
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BluetoothSyncService::class.java).apply {
                        action = ACTION_SEND_SETTINGS
                        putExtra(EXTRA_PAYLOAD, Gson().toJson(revised))
                    }
                )
            } catch (e: Exception) { Log.e(TAG, "sendSettings error: ${e.message}") }
        }

        fun sendForceMeasureHrToWatch(context: Context): Boolean {
            if (PhonePreferences(context).connectionPaused || !_connectionState.value.isConnected || sharedWriter == null) return false
            try {
                val requestedAt = System.currentTimeMillis()
                _heartRateFeedback.update { current ->
                    currentHeartRateFeedback(current, true, requestedAt)?.copy(status = "requesting", updatedAt = requestedAt)
                        ?: HeartRateFeedback("requesting", requestedAt)
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BluetoothSyncService::class.java).apply {
                        action = ACTION_FORCE_MEASURE_HR
                    }
                )
                return true
            } catch (e: Exception) {
                val failedAt = System.currentTimeMillis()
                _heartRateFeedback.update { current ->
                    currentHeartRateFeedback(current, true, failedAt)?.copy(status = "service_unavailable", updatedAt = failedAt)
                        ?: HeartRateFeedback("service_unavailable", failedAt)
                }
                Log.e(TAG, "sendForceMeasureHr error: ${e.message}")
                return false
            }
        }

        fun sendForceMeasureSpO2ToWatch(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BluetoothSyncService::class.java).apply {
                        action = ACTION_FORCE_MEASURE_SPO2
                    }
                )
            } catch (e: Exception) { Log.e(TAG, "sendForceMeasureSpO2 error: ${e.message}") }
        }

    }

    @Inject lateinit var repository: FitnessRepository

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var serverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private data class OutgoingFrame(val writer: PrintWriter, val message: SyncMessage)
    private val outbound = Channel<OutgoingFrame>(128)
    private val preferences by lazy { PhonePreferences(this) }
    private data class CachedReply(val signature: String, val result: ReplyResultPayload)
    private val replyResults = object : LinkedHashMap<Long, CachedReply>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CachedReply>?) = size > 64
    }
    private lateinit var remoteControls: PhoneRemoteControls

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        remoteControls = PhoneRemoteControls(this) { state ->
            sendRaw(SyncMessage(MessageType.PHONE_CONTROL_STATE, payload = gson.toJson(state)))
        }
        try {
            createNotificationChannel()
            startForeground(NOTIF_ID, buildNotification("Starting…"))
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HealthSync:PhoneBTWake")
            wakeLock?.setReferenceCounted(false)
            scope.launch {
                for (frame in outbound) {
                    if (sharedWriter !== frame.writer) continue
                    val writer = frame.writer
                    try {
                        wakeLock?.acquire(10_000)
                        writer.println(gson.toJson(frame.message))
                        if (writer.checkError()) clientSocket?.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Send failed", e)
                    } finally {
                        if (wakeLock?.isHeld == true) wakeLock?.release()
                    }
                }
            }
            scope.launch {
                while (isActive) {
                    delay(5_000L)
                    _heartRateFeedback.update {
                        currentHeartRateFeedback(it, _connectionState.value.isConnected, System.currentTimeMillis())
                    }
                }
            }
            if (!preferences.connectionPaused) startServer()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (preferences.connectionPaused && intent?.action != ACTION_RECONNECT) {
                stopSelf()
                return START_NOT_STICKY
            }
            when (intent?.action) {
                ACTION_SEND_NOTIFICATION -> {
                    val json = intent.getStringExtra(EXTRA_PAYLOAD) ?: return START_STICKY
                    val notification = gson.fromJson(json, NotificationPayload::class.java)
                    if (!preferences.isAppAllowed(notification.packageName.orEmpty())) return START_STICKY
                    sendRaw(SyncMessage(MessageType.NOTIFICATION, payload = json))
                }
                ACTION_NOTIFICATION_REMOVED -> {
                    val json = intent.getStringExtra(EXTRA_PAYLOAD) ?: return START_STICKY
                    // Removal must invalidate old replies even when notification sync has just been disabled.
                    sendRaw(SyncMessage(MessageType.NOTIFICATION_REMOVED, payload = json))
                }
                ACTION_NOTIFICATION_SNAPSHOT -> {
                    val json = intent.getStringExtra(EXTRA_PAYLOAD) ?: return START_STICKY
                    sendRaw(SyncMessage(MessageType.NOTIFICATION_SNAPSHOT, payload = json))
                }
                ACTION_SEND_CALL -> {
                    val json = intent.getStringExtra(EXTRA_PAYLOAD) ?: return START_STICKY
                    val call = gson.fromJson(json, CallPayload::class.java)
                    if (!preferences.syncCalls && call.state != CallState.ENDED && call.state != CallState.MISSED) return START_STICKY
                    sendRaw(SyncMessage(MessageType.CALL_EVENT, payload = json))
                }
                ACTION_DISCONNECT -> {
                    preferences.connectionPaused = true
                    Log.d(TAG, "Manual disconnect requested")
                    disconnectCurrent()
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_RECONNECT -> {
                    preferences.connectionPaused = false
                    Log.d(TAG, "Manual reconnect requested")
                    disconnectCurrent()
                    startServer()
                }
                ACTION_SEND_SETTINGS -> {
                    val json = intent.getStringExtra(EXTRA_PAYLOAD) ?: return START_STICKY
                    sendRaw(SyncMessage(MessageType.SETTINGS_UPDATE, payload = json))
                    Log.d(TAG, "Settings sent to watch")
                }
                ACTION_FORCE_MEASURE_HR -> {
                    if (!sendRaw(SyncMessage(MessageType.FORCE_MEASURE_HR, payload = "{}"))) {
                        _heartRateFeedback.value = HeartRateFeedback("service_unavailable", System.currentTimeMillis())
                    }
                    Log.d(TAG, "Force measure HR command sent to watch")
                }
                ACTION_FORCE_MEASURE_SPO2 -> {
                    sendRaw(SyncMessage(MessageType.FORCE_MEASURE_SPO2, payload = "{}"))
                    Log.d(TAG, "Force measure SpO2 command sent to watch")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand error: ${e.message}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        disconnectCurrent()
        remoteControls.destroy()
        outbound.close()
        scope.cancel()
        _connectionState.tryEmit(ConnectionInfo())
        try { wakeLock?.release() } catch (_: Exception) {}
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Server logic ──────────────────────────────────────────────────────────

    private fun startServer() {
        serverJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverJob = scope.launch {
            while (currentCoroutineContext().isActive) {
              var listeningSocket: BluetoothServerSocket? = null
              try {
                val adapter = getBluetoothAdapter()
                if (!hasBtPermission() || adapter == null || !adapter.isEnabled) {
                    updateState(ConnectionInfo())
                    updateNotification("Enable Bluetooth and allow nearby devices")
                    delay(5_000)
                    continue
                }
                listeningSocket = adapter.listenUsingRfcommWithServiceRecord("HealthSync", SERVICE_UUID)
                serverSocket = listeningSocket
                updateState(ConnectionInfo(isListening = true))
                updateNotification("Waiting for watch…")
                Log.d(TAG, "BT server listening…")

                while (currentCoroutineContext().isActive) {
                    try {
                        val socket = listeningSocket.accept()
                        handleConnection(socket)
                    } catch (e: Exception) {
                        if (!currentCoroutineContext().isActive) break
                        Log.e(TAG, "Accept error: ${e.message}")
                        delay(3_000)
                    }
                }
              } catch (e: CancellationException) {
                throw e
              } catch (e: SecurityException) {
                Log.e(TAG, "BT SecurityException: ${e.message}")
              } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
              } finally {
                try { listeningSocket?.close() } catch (_: Exception) {}
                if (serverSocket === listeningSocket) serverSocket = null
                if (currentCoroutineContext().isActive) updateState(ConnectionInfo())
              }
              delay(5_000)
            }
        }
    }

    private suspend fun handleConnection(socket: BluetoothSocket) {
        clientSocket = socket
        try {
            val name = if (hasBtPermission()) socket.remoteDevice?.name ?: "Watch" else "Watch"
            val addr = if (hasBtPermission()) socket.remoteDevice?.address else null
            sharedWriter = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)
            updateState(ConnectionInfo(isConnected = true, deviceName = name, deviceAddress = addr, isListening = true))
            updateNotification("✓ Connected: $name")
            Log.d(TAG, "Watch connected: $name")

            val intervals = preferences.sensorIntervals()
            sendRaw(SyncMessage(MessageType.SETTINGS_UPDATE, payload = gson.toJson(WatchSettingsPayload(
                intervals.hrIntervalMs, intervals.spo2IntervalMs, preferences.stepGoal, preferences.userWeightKg,
                preferences.userHeightCm, intervals.revision))))
            HealthNotificationListenerService.resyncNotifications(applicationContext)
            // Reconcile alerts after a disconnect, including an end event missed offline.
            WatchInCallService.refresh()
            val call = if (preferences.syncCalls) CallMonitorService.latestCallPayload
                else CallPayload(CallState.ENDED, "", "")
            sendRaw(SyncMessage(MessageType.CALL_EVENT, payload = gson.toJson(call)))
            val lastReceivedAt = AtomicLong(SystemClock.elapsedRealtime())
            val heartbeat = scope.launch {
                while (isActive) {
                    delay(20_000)
                    if (SystemClock.elapsedRealtime() - lastReceivedAt.get() > 75_000) { socket.close(); break }
                    sendRaw(SyncMessage(MessageType.PING, payload = "{}"))
                }
            }
            try {
                val reader = JsonLineReader(BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8)))
                while (currentCoroutineContext().isActive && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    lastReceivedAt.set(SystemClock.elapsedRealtime())
                    processMessage(line)
                }
            } finally { heartbeat.cancel() }
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth permission revoked", e)
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
            if (clientSocket === socket) {
                remoteControls.disconnect()
                sharedWriter = null
                clientSocket = null
                updateState(ConnectionInfo(isListening = serverSocket != null))
                updateNotification("Watch disconnected — waiting…")
            }
            Log.d(TAG, "Watch disconnected")
        }
    }

    private fun disconnectCurrent() {
        remoteControls.disconnect()
        serverJob?.cancel()
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        clientSocket = null
        sharedWriter = null
        updateState(ConnectionInfo())
    }

    // ── Message handling ──────────────────────────────────────────────────────

    private suspend fun processMessage(json: String) {
        try {
            val msg = gson.fromJson(json, SyncMessage::class.java)
            if (!SyncValidation.validIncomingHealthPayload(msg.type, msg.payload, msg.timestamp)) {
                if (msg.type == MessageType.HEART_RATE || msg.type == MessageType.HEART_RATE_STATUS) {
                    _heartRateFeedback.value = HeartRateFeedback("invalid_reading", System.currentTimeMillis())
                }
                Log.w(TAG, "Rejected invalid ${msg.type} tracking payload")
                return
            }
            when (msg.type) {
                MessageType.SENSOR_INTERVALS -> {
                    val intervals = SensorIntervalPolicy.parse(msg.payload) ?: return
                    preferences.applySensorIntervals(intervals)
                    val current = preferences.sensorIntervals()
                    // Send a newer phone edit back when the watch connected with an older snapshot.
                    if (intervals.revision < current.revision) {
                        sendRaw(SyncMessage(MessageType.SENSOR_INTERVALS, payload = gson.toJson(current)))
                    }
                }
                MessageType.BATCHED_SYNC -> {
                    val batch = JsonParser.parseString(msg.payload).asJsonObject
                    val workoutRows = batch.getAsJsonArray("workouts") ?: JsonArray()
                    val stepRows = batch.getAsJsonArray("daily_steps") ?: JsonArray()
                    Log.d(TAG, "Received BATCHED_SYNC from watch: ${workoutRows.size()} workouts, ${stepRows.size()} step buckets")

                    val processedSessionIds = mutableListOf<String>()
                    val processedStepTimestamps = mutableListOf<Long>()

                    for (row in workoutRows) {
                        try {
                            val raw = row.toString()
                            if (!SyncValidation.validIncomingWorkoutSession(raw)) continue
                            val workout = gson.fromJson(raw, WorkoutSessionPayload::class.java)
                            // Use saveWorkoutSession() to preserve route_json (GPS coordinates)
                            // saveWorkout(WorkoutPayload) would lose the GPS route
                            repository.saveWorkoutSession(workout)
                            processedSessionIds.add(workout.session_id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving batch workout: ${e.message}")
                        }
                    }

                    val validBuckets = stepRows.mapNotNull { row ->
                        val raw = row.toString()
                        if (SyncValidation.validIncomingStepBucket(raw)) gson.fromJson(raw, DailyStepBucketPayload::class.java)
                        else null
                    }
                    processedStepTimestamps.addAll(repository.saveStepBuckets(validBuckets))

                    // Respond with SYNC_ACK so Watch marks these records as is_synced = 1
                    val ackPayload = SyncAckPayload(
                        session_ids = processedSessionIds,
                        step_timestamps = processedStepTimestamps
                    )
                    sendRaw(SyncMessage(MessageType.SYNC_ACK, payload = gson.toJson(ackPayload)))
                    Log.d(TAG, "Sent SYNC_ACK confirmation to Watch for ${processedSessionIds.size} workouts & ${processedStepTimestamps.size} step buckets.")
                }
                MessageType.HEART_RATE -> {
                    val reading = gson.fromJson(msg.payload, HeartRatePayload::class.java)
                    repository.saveHeartRate(reading, msg.timestamp)
                    if (SyncValidation.validHeartRate(reading.bpm, reading.accuracy, msg.timestamp)) {
                        _heartRateFeedback.value = HeartRateFeedback("reading", System.currentTimeMillis())
                    }
                }
                MessageType.HEART_RATE_STATUS -> {
                    // Status previews stay in memory; only HEART_RATE may enter the repository.
                    _heartRateFeedback.value = heartRateFeedbackFromJson(msg.payload, System.currentTimeMillis())
                }
                MessageType.STEPS        -> repository.saveSteps(gson.fromJson(msg.payload, StepsPayload::class.java), msg.timestamp)
                MessageType.SPO2         -> repository.saveSpO2(gson.fromJson(msg.payload, SpO2Payload::class.java), msg.timestamp)
                MessageType.SLEEP        -> repository.saveSleep(gson.fromJson(msg.payload, SleepPayload::class.java))
                MessageType.FALL_DETECTED -> postFallAlert(gson.fromJson(msg.payload, FallDetectedPayload::class.java))
                MessageType.WORKOUT_SESSION -> {
                    val payload = gson.fromJson(msg.payload, WorkoutPayload::class.java)
                    Log.d(TAG, "Received WORKOUT_SESSION from watch. distance=${payload.distanceMeters}m")
                    repository.saveWorkout(payload)
                }
                MessageType.REPLY_MESSAGE -> {
                    val reply = gson.fromJson(msg.payload, ReplyMessagePayload::class.java)
                    val key = reply.notificationKey.orEmpty()
                    val signature = "${reply.packageName}\u0000$key\u0000${reply.title}\u0000${reply.replyText}"
                    val previous = if (reply.requestId > 0) replyResults[reply.requestId] else null
                    val result = if (previous != null) {
                        if (previous.signature == signature) previous.result
                        else ReplyResultPayload(reply.requestId, key, false, "This reply request expired; try again")
                    } else {
                        HealthNotificationListenerService.sendReply(applicationContext,
                            reply.packageName.orEmpty(), reply.replyText.orEmpty(), reply.title.orEmpty(), key, reply.requestId)
                            .also { if (reply.requestId > 0) replyResults[reply.requestId] = CachedReply(signature, it) }
                    }
                    sendRaw(SyncMessage(MessageType.REPLY_RESULT, payload = gson.toJson(result)))
                }
                MessageType.PHONE_CONTROL -> remoteControls.handle(gson.fromJson(msg.payload, PhoneControlPayload::class.java))
                MessageType.CALL_ACTION -> {
                    val callAction = gson.fromJson(msg.payload, CallActionPayload::class.java)
                    Log.d(TAG, "Received CALL_ACTION from watch: ${callAction.action}")
                    handleCallAction(callAction)
                }
                // Phone Telecom callbacks are the authority for call state.
                MessageType.PING         -> sendRaw(SyncMessage(MessageType.ACK, payload = "{}"))
                else -> Unit
            }
        } catch (e: Exception) { Log.e(TAG, "Parse error: ${e.message}") }
    }

    private fun handleCallAction(payload: CallActionPayload) {
        val address = _connectionState.value.deviceAddress.orEmpty()
        WatchInCallService.handle(applicationContext, payload, address) { result ->
            sendRaw(SyncMessage(MessageType.CALL_CONTROL_STATE, payload = gson.toJson(result)))
        }
    }

    private fun sendRaw(msg: SyncMessage): Boolean {
        val writer = sharedWriter ?: return false
        return outbound.trySend(OutgoingFrame(writer, msg)).isSuccess
    }

    private fun postFallAlert(payload: FallDetectedPayload) {
        try {
            getSystemService(NotificationManager::class.java).notify(
                9999,
                NotificationCompat.Builder(this, FALL_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Possible fall detected")
                    .setContentText("Check on the watch wearer. Sensor detection: ${payload.severity}.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: Exception) { Log.e(TAG, "Fall alert error: ${e.message}") }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hasBtPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        else true

    private fun getBluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun updateState(state: ConnectionInfo) {
        val newConnection = state.isConnected && !_connectionState.value.isConnected
        _heartRateFeedback.update {
            if (newConnection) null else currentHeartRateFeedback(it, state.isConnected, System.currentTimeMillis())
        }
        _connectionState.tryEmit(state)
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Bluetooth Sync", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "HealthSync watch connection" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(FALL_CHANNEL_ID, "Possible fall alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("HealthSync")
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .build()

    private fun openAppIntent() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {}
    }
}

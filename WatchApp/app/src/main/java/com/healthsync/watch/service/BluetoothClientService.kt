package com.healthsync.watch.service

import android.app.*
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import com.healthsync.watch.data.*
import com.healthsync.watch.WatchApp
import com.healthsync.watch.notification.NotificationDisplayActivity
import com.healthsync.watch.notification.NotificationInboxStore
import com.healthsync.watch.notification.WatchNotificationAlerts
import com.healthsync.watch.notification.parseNotificationPayload
import com.healthsync.watch.sensor.ForegroundPolicy
import com.healthsync.watch.ui.CallAlertActivity
import com.healthsync.watch.ui.InCallActivity
import com.healthsync.watch.ui.WatchFaceActivity
import com.healthsync.watch.ui.shell.PhoneControlsBridge
import com.healthsync.watch.ui.calls.WatchCallBridge
import com.healthsync.watch.ui.calls.WatchCallAlerts
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class BluetoothClientService : Service() {

    companion object {
        private const val TAG         = "BTClient"
        private const val CHANNEL_ID  = "bt_client_channel"
        private const val NOTIF_ID    = 2001
        private const val ALERT_CHANNEL_ID = "watch_alerts"
        private const val CALL_ALERT_ID = 2101
        private const val MESSAGE_ALERT_ID = 2102
        private const val RETRY_DELAY = 8_000L
        val SERVICE_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")

        @Volatile var instance: BluetoothClientService? = null
            private set

        @Volatile var sharedWriter: PrintWriter? = null
        @Volatile var isConnected: Boolean = false

        fun broadcastStatus(context: Context, connected: Boolean) {
            PhoneControlsBridge.connectionChanged(context, connected)
            WatchCallBridge.connectionChanged(context, connected)
            WatchNotificationAlerts.connectionChanged(context, connected)
            context.sendBroadcast(
                Intent(WatchFaceActivity.ACTION_BT_STATUS).setPackage(context.packageName).apply {
                    putExtra(WatchFaceActivity.EXTRA_CONNECTED, connected)
                }
            )
        }

        /** True means queued on the active connection; only SYNC_ACK confirms persistence. */
        fun sendRawMsg(msg: SyncMessage, gson: Gson = Gson()): Boolean {
            val svc = instance ?: return false
            val writer = sharedWriter ?: return false
            val connection = svc.socket ?: return false
            if (!isConnected) return false
            return svc.outbound.trySend(OutgoingFrame(writer, connection, gson.toJson(msg))).isSuccess
        }

        /** Share current saved intervals without changing their last-edit revision. */
        fun syncSensorIntervals(context: Context): Boolean {
            val snapshot = WatchPreferences(context.applicationContext).sensorIntervals()
            return sendRawMsg(SyncMessage(MessageType.SENSOR_INTERVALS, payload = Gson().toJson(snapshot)))
        }

        fun syncBatchedPayloads(context: Context) {
            val svc = instance ?: return
            svc.serviceScope.launch {
                svc.syncMutex.withLock {
                    if (!isConnected) return@withLock
                    val dbHelper = WatchDatabaseHelper.getInstance(context.applicationContext)
                    for (workout in dbHelper.getUnsyncedWorkouts(limit = 100)) {
                        val batch = BatchedSyncPayload(workouts = listOf(workout))
                        if (!sendRawMsg(SyncMessage(MessageType.BATCHED_SYNC, payload = svc.gson.toJson(batch)))) break
                    }
                    for (steps in dbHelper.getUnsyncedStepBuckets(limit = 2000).chunked(200)) {
                        val batch = BatchedSyncPayload(daily_steps = steps)
                        if (!sendRawMsg(SyncMessage(MessageType.BATCHED_SYNC, payload = svc.gson.toJson(batch)))) break
                    }
                }
            }
        }
    }

    private val gson  = Gson()
    internal val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: BluetoothSocket? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private data class OutgoingFrame(val writer: PrintWriter, val socket: BluetoothSocket, val json: String)
    private val outbound = Channel<OutgoingFrame>(128)
    private val syncMutex = Mutex()
    private val preferences by lazy { WatchPreferences(this) }
    private val lastReceivedAt = AtomicLong()
    private var foregroundStarted = false
    private var connectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        PhoneControlsBridge.initialize(this)
        try {
            createChannel()
            foregroundStarted = ForegroundPolicy.startConnectedDevice(this, NOTIF_ID, buildNotif("Starting…"))
            if (!foregroundStarted) { stopSelf(); return }
            instance = this
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HealthSync:BTClientWake").apply { setReferenceCounted(false) }
            serviceScope.launch {
                for (frame in outbound) {
                    if (sharedWriter !== frame.writer || frame.json.length > 4 * 1024 * 1024) continue
                    try {
                        wakeLock?.acquire(10_000)
                        frame.writer.println(frame.json)
                        if (frame.writer.checkError()) frame.socket.close()
                    } catch (e: Exception) { Log.w(TAG, "Send failed", e) }
                    finally { releaseWakeLock() }
                }
            }
            ensureConnectionJob()
        } catch (e: Exception) {
            foregroundStarted = false
            Log.w(TAG, "Bluetooth service could not initialize", e)
            stopSelf()
        }
    }

    private fun ensureConnectionJob() {
        // Returning Home may recover a failed child job; an active reconnect keeps its backoff.
        if (connectionJob?.isActive == true) return
        connectionJob = serviceScope.launch { maintainConnection() }.also { job ->
            job.invokeOnCompletion { cause ->
                if (cause != null && cause !is CancellationException) Log.w(TAG, "Bluetooth connection loop stopped", cause)
            }
        }
    }

    private suspend fun maintainConnection() {
        var retryCount = 0
        var candidateIndex = 0
        while (currentCoroutineContext().isActive) {
            val adapter = getBluetoothAdapter()
            val bluetoothReady = try { hasBtPermission() && adapter?.isEnabled == true } catch (_: SecurityException) { false }
            if (!bluetoothReady || adapter == null) {
                updateNotif("Enable Bluetooth and grant its permission")
                delay(RETRY_DELAY)
                continue
            }

            // Find paired phone — try multiple strategies:
            // 1. Device with phone major class (0x0200)
            // 2. Any device that is NOT a wearable/audio (class 0x0700 or 0x0400)
            // 3. First bonded device as last resort
            val phone: BluetoothDevice? = try {
                val bonded = adapter.bondedDevices?.toList() ?: emptyList()
                val candidates = bonded.sortedWith(compareByDescending<BluetoothDevice> { it.address == preferences.lastPhoneAddress }
                    .thenByDescending { it.bluetoothClass?.majorDeviceClass == 0x0200 }
                    .thenBy { it.address })
                candidates.getOrNull(if (candidates.isEmpty()) 0 else candidateIndex % candidates.size)
            } catch (e: SecurityException) { null }
              catch (e: Exception) { null }

            if (phone == null) {
                updateNotif("Pair your phone in Bluetooth settings")
                delay(RETRY_DELAY); continue
            }

            try {
                updateNotif("Connecting to ${phone.name ?: "phone"}…")
                try { adapter.cancelDiscovery() } catch (_: SecurityException) {}
                val s = phone.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket = s
                val connectTimeout = serviceScope.launch { delay(20_000); try { s.close() } catch (_: Exception) {} }
                try { withContext(Dispatchers.IO) { s.connect() } } finally { connectTimeout.cancel() }
                sharedWriter = PrintWriter(OutputStreamWriter(s.outputStream, Charsets.UTF_8), true)
                isConnected = true
                preferences.lastPhoneAddress = phone.address
                lastReceivedAt.set(SystemClock.elapsedRealtime())
                retryCount = 0 // reset on success
                candidateIndex = 0
                broadcastStatus(this@BluetoothClientService, true)
                updateNotif("✓ Connected: ${phone.name}")
                Log.d(TAG, "Connected to phone: ${phone.name}")

                // PING to confirm channel works
                sendRawMsg(SyncMessage(MessageType.PING, payload = "{}"), gson)

                // Send Watch Device Info (version, build, model) to Phone Companion
                try {
                    val pInfo = packageManager.getPackageInfo(packageName, 0)
                    val vCode = if (Build.VERSION.SDK_INT >= 28) pInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pInfo.versionCode
                    val devInfo = DeviceInfoPayload(
                        versionName = pInfo.versionName ?: "2.4.3",
                        versionCode = vCode,
                        model = "Kolabee U8 Ultra",
                        batteryPercent = try {
                            val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                        } catch (_: Exception) { -1 }
                    )
                    sendRawMsg(SyncMessage(MessageType.DEVICE_INFO, payload = gson.toJson(devInfo)), gson)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send DEVICE_INFO: ${e.message}")
                }

                syncSensorIntervals(applicationContext)

                // Replay the genuine last sample with its collection timestamp and
                // explain permission/sensor failures to the companion after reconnect.
                SensorCollectorService.syncHeartRateToPhone(applicationContext)

                // Trigger batched sync for any unsynced SQLite records
                syncBatchedPayloads(applicationContext)

                val heartbeat = serviceScope.launch {
                    var ticks = 0
                    while (isActive) {
                        delay(20_000)
                        if (SystemClock.elapsedRealtime() - lastReceivedAt.get() > 75_000) {
                            try { s.close() } catch (e: Exception) { Log.w(TAG, "Heartbeat disconnect failed", e) }
                            break
                        }
                        sendRawMsg(SyncMessage(MessageType.PING, payload = "{}"), gson)
                        if (++ticks % 3 == 0) syncBatchedPayloads(applicationContext)
                    }
                }
                try { readLoop(s) } finally { heartbeat.cancel() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                updateNotif("Allow Nearby devices to connect")
                retryCount++
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                retryCount++
                candidateIndex++
            } finally {
                if (instance === this@BluetoothClientService) {
                    isConnected = false
                    sharedWriter = null
                }
                try { socket?.close() } catch (_: Exception) {}
                socket = null
                if (instance === this@BluetoothClientService) {
                    broadcastStatus(this@BluetoothClientService, false)
                    updateNotif("Disconnected — retrying…")
                }
                // Exponential backoff: 8s, 16s, 32s max
                val backoff = RETRY_DELAY * (1L shl (retryCount - 1).coerceIn(0, 2))
                if (currentCoroutineContext().isActive) delay(backoff)
            }
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun hasBtPermission() = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private suspend fun readLoop(socket: BluetoothSocket) {
        try {
            val reader = JsonLineReader(BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8)))
            while (currentCoroutineContext().isActive && socket.isConnected) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                lastReceivedAt.set(SystemClock.elapsedRealtime())
                processMessage(line)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read error: ${e.message}")
        }
    }

    private fun processMessage(json: String) {
        try {
            val msg = gson.fromJson(json, SyncMessage::class.java)
            when (msg.type) {
                MessageType.SYNC_ACK -> {
                    val ackPayload = msg.parsePayload<SyncAckPayload>(gson)
                    val dbHelper = WatchDatabaseHelper.getInstance(applicationContext)
                    dbHelper.markWorkoutsSynced(ackPayload.session_ids)
                    dbHelper.markStepBucketsSynced(ackPayload.step_timestamps)
                    Log.d(TAG, "Received SYNC_ACK from phone! Marked ${ackPayload.session_ids.size} workouts & ${ackPayload.step_timestamps.size} step buckets as is_synced = 1")
                }
                MessageType.NOTIFICATION -> showNotificationAlert(parseNotificationPayload(msg.payload, gson))
                MessageType.NOTIFICATION_REMOVED -> WatchNotificationAlerts.removed(this, msg.parsePayload<NotificationRemovedPayload>(gson).notificationKey)
                MessageType.NOTIFICATION_SNAPSHOT -> WatchNotificationAlerts.snapshot(this, msg.parsePayload<NotificationSnapshotPayload>(gson).activeKeys.orEmpty())
                MessageType.REPLY_RESULT -> WatchNotificationAlerts.replyResult(this, msg.parsePayload<ReplyResultPayload>(gson))
                MessageType.CALL_EVENT     -> WatchCallAlerts.receive(this, msg.parsePayload<CallPayload>(gson))
                MessageType.CALL_ANSWERED  -> {
                    val legacy = msg.parsePayload<ActiveCallPayload>(gson)
                    WatchCallAlerts.receive(this, CallPayload(CallState.ANSWERED, legacy.number.orEmpty(), legacy.callerName.orEmpty(), statusText = "Update the phone app for call controls"))
                }
                MessageType.CALL_CONTROL_STATE -> WatchCallBridge.receiveResult(msg.parsePayload<CallControlStatePayload>(gson))
                MessageType.PHONE_CONTROL_STATE -> PhoneControlsBridge.receive(msg.parsePayload<PhoneControlStatePayload>(gson))
                MessageType.SETTINGS_UPDATE -> {
                    val settings = msg.parsePayload<WatchSettingsPayload>(gson)
                    val prefs = com.healthsync.watch.data.WatchPreferences(applicationContext)
                    // Old/replayed phone settings must not undo a newer watch interval choice.
                    SensorIntervalPolicy.parseSettings(msg.payload)?.let(prefs::applySensorIntervals)
                    if (settings.stepGoal > 0) prefs.stepGoal = settings.stepGoal
                    if (settings.userWeightKg in 30.0..250.0) prefs.userWeightKg = settings.userWeightKg
                    if (settings.userHeightCm in 100..230) prefs.userHeightCm = settings.userHeightCm
                    SensorCollectorService.notifySettingsUpdated(applicationContext)
                    syncSensorIntervals(applicationContext)
                    broadcastStatus(this, isConnected)
                    val activeIntervals = prefs.sensorIntervals()
                    Log.d(TAG, "Settings received — active HR:${activeIntervals.hrIntervalMs}ms SpO2:${activeIntervals.spo2IntervalMs}ms revision:${activeIntervals.revision}")
                }
                MessageType.SENSOR_INTERVALS -> {
                    val intervals = SensorIntervalPolicy.parse(msg.payload) ?: return
                    if (preferences.applySensorIntervals(intervals)) SensorCollectorService.notifySettingsUpdated(applicationContext)
                    syncSensorIntervals(applicationContext)
                }
                MessageType.FORCE_MEASURE_HR -> {
                    Log.d(TAG, "Received FORCE_MEASURE_HR from phone")
                    SensorCollectorService.forceMeasureHr(applicationContext)
                }
                MessageType.FORCE_MEASURE_SPO2 -> {
                    Log.d(TAG, "Received FORCE_MEASURE_SPO2 from phone")
                    SensorCollectorService.forceMeasureSpO2(applicationContext)
                }
                MessageType.OTA_START -> {
                    val payload = msg.parsePayload<OtaStartPayload>(gson)
                    com.healthsync.watch.update.WatchUpdateManager.onOtaStart(applicationContext, payload)
                }
                MessageType.OTA_CHUNK -> {
                    val payload = msg.parsePayload<OtaChunkPayload>(gson)
                    com.healthsync.watch.update.WatchUpdateManager.onOtaChunk(applicationContext, payload)
                }
                MessageType.OTA_COMPLETE -> {
                    val payload = msg.parsePayload<OtaCompletePayload>(gson)
                    com.healthsync.watch.update.WatchUpdateManager.onOtaComplete(applicationContext, payload)
                }
                MessageType.WATCH_FACE_INSTALL -> {
                    try {
                        val payload = msg.parsePayload<WatchFaceInstallPayload>(gson)
                        val pkg = gson.fromJson(payload.jsonContent, com.healthsync.watch.data.watchface.HswfPackage::class.java)
                        val saved = com.healthsync.watch.data.watchface.DynamicFaceStore.getInstance(applicationContext).saveFace(pkg)
                        if (saved && payload.setActive) {
                            com.healthsync.watch.data.WatchPreferences(applicationContext).watchFaceStyle = "dynamic_${pkg.id}"
                            sendBroadcast(Intent("com.healthsync.watch.ACTION_FACE_CHANGED").setPackage(packageName))
                        }
                        sendRawMsg(SyncMessage(
                            type = MessageType.WATCH_FACE_ACK,
                            payload = gson.toJson(WatchFaceAckPayload(id = payload.id, success = saved, message = if (saved) "Installed" else "Save failed"))
                        ), gson)
                        Log.d(TAG, "Dynamic watch face received and saved: ${payload.name} (${payload.id})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error installing watch face: ${e.message}", e)
                    }
                }
                MessageType.ACK  -> Log.d(TAG, "ACK received")
                MessageType.PING -> sendRawMsg(SyncMessage(MessageType.ACK, payload = "{}"), gson)
                else -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    private fun showNotificationAlert(p: NotificationPayload) {
        WatchNotificationAlerts.receive(applicationContext, p)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            isConnected = false
            sharedWriter = null
            PhoneControlsBridge.connectionChanged(this, false)
            WatchNotificationAlerts.connectionChanged(this, false)
            WatchCallBridge.connectionChanged(this, false)
        }
        outbound.close()
        serviceScope.cancel()
        try { socket?.close() } catch (_: Exception) {}
        releaseWakeLock()
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        // onDestroy can release the same non-reference-counted lock as the writer.
        try { if (wakeLock?.isHeld == true) wakeLock?.release() }
        catch (e: RuntimeException) { Log.w(TAG, "Wake lock already released", e) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) { stopSelf(); return START_NOT_STICKY }
        ensureConnectionJob()
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        WatchNotificationAlerts.createChannel(this)
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CHANNEL_ID, "Watch BT Client", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Phone calls and messages", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun buildNotif(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("HealthSync Watch")
        .setContentText(text)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, WatchFaceActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun updateNotif(text: String) {
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(text)) }
        catch (_: Exception) {}
    }
}


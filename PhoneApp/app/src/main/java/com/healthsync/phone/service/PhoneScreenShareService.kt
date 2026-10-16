package com.healthsync.phone.service

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.healthsync.phone.PhoneScreenShareActivity
import com.healthsync.phone.R
import com.healthsync.phone.data.model.PhoneScreenPayload
import com.healthsync.phone.data.model.PhoneScreenProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID

data class PhoneScreenShareState(
    val active: Boolean = false,
    val pending: Boolean = false,
    val message: String = "Share Google Maps from this phone to your watch.",
    val acknowledgedFrame: Long = 0L
)

/** A user-approved, memory-only screen cast. Every start requires a new Android consent result. */
class PhoneScreenShareService : Service() {
    companion object {
        private const val ACTION_START = "com.healthsync.phone.START_SCREEN_SHARE"
        private const val ACTION_STOP = "com.healthsync.phone.STOP_SCREEN_SHARE"
        private const val EXTRA_CONSENT = "screen_share_consent"
        private const val EXTRA_CONSENT_ID = "screen_share_consent_id"
        private const val CHANNEL_ID = "phone_screen_share"
        private const val NOTIFICATION_ID = 1004
        @Volatile private var instance: PhoneScreenShareService? = null
        private val mutableState = MutableStateFlow(PhoneScreenShareState())
        private val consentPolicy = PhoneScreenConsentPolicy()
        private val sessionPolicy = PhoneScreenSessionPolicy()
        val state: StateFlow<PhoneScreenShareState> = mutableState.asStateFlow()

        fun isSessionActive(sessionId: String): Boolean {
            val connection = BluetoothSyncService.connectionState.value
            return sessionPolicy.isActive(sessionId, connection.isConnected, connection.deviceAddress)
        }

        @Synchronized fun beginConsent(): String? {
            if (state.value.active || state.value.pending) return null
            val id = UUID.randomUUID().toString()
            consentPolicy.begin(id, BluetoothSyncService.connectionState.value.deviceAddress, sessionPolicy.snapshotGeneration())
            mutableState.value = PhoneScreenShareState(pending = true, message = "Choose Google Maps in Android's screen sharing prompt.")
            return id
        }

        @Synchronized fun consentFailed(message: String, expectedConsentId: String? = null) {
            if (expectedConsentId != null && !consentPolicy.isPending(expectedConsentId)) return
            consentPolicy.invalidate()
            if (!state.value.active) mutableState.value = PhoneScreenShareState(message = message)
        }

        fun startWithConsent(context: Context, resultCode: Int, result: Intent, consentId: String) {
            require(resultCode == Activity.RESULT_OK)
            check(consentPolicy.isPending(consentId))
            // This Intent lives only in this one non-sticky service start; it is never persisted or reused.
            ContextCompat.startForegroundService(context, Intent(context, PhoneScreenShareService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_CONSENT, result).putExtra(EXTRA_CONSENT_ID, consentId))
        }

        fun stopSharing(context: Context) {
            sessionPolicy.invalidate()
            consentPolicy.invalidate()
            val current = instance
            if (current != null) current.worker.post { current.finishCapture("Screen sharing stopped.") }
            else {
                context.stopService(Intent(context, PhoneScreenShareService::class.java))
                consentFailed("Screen sharing stopped.")
            }
        }

        fun connectionChanged(context: Context, connected: Boolean) {
            if (!connected) {
                sessionPolicy.invalidate()
                consentPolicy.invalidate()
                val current = instance
                if (current != null) current.worker.post { current.finishCapture("Bluetooth disconnected. Share again after reconnecting.") }
                else if (state.value.pending) consentFailed("Bluetooth disconnected. Connect your watch before sharing.")
            }
        }

        fun handleWatchMessage(payload: PhoneScreenPayload) {
            if (!PhoneScreenProtocol.validControl(payload)) return
            if (payload.kind == "stop" && sessionPolicy.deactivateOwned(payload.sessionId)) consentPolicy.invalidate()
            val current = instance
            if (current != null) current.worker.post { current.receiveWatchMessage(payload) }
            else if (payload.kind == "request") {
                BluetoothSyncService.sendPhoneScreen(PhoneScreenPayload(
                    kind = "stop", sessionId = payload.sessionId.takeIf { PhoneScreenProtocol.validSession(it) } ?: UUID.randomUUID().toString(),
                    message = "No phone screen is being shared. Start sharing on your phone."
                ))
            }
        }
    }

    private lateinit var captureThread: HandlerThread
    private lateinit var worker: Handler
    private val control = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureSize: PhoneCaptureSize? = null
    private var flow: PhoneScreenFlowControl? = null
    @Volatile private var ownedSessionId: String? = null
    private val lifetimeLock = Any()
    private var destroyed = false
    private var latestEncodedImage: EncodedImage? = null
    private var capturedContentVisible = true
    private var receiverRegistered = false

    private fun newProjectionCallback(sessionId: String) = object : MediaProjection.Callback() {
        override fun onStop() {
            // Android's Stop/chip callback must revoke sending even if JPEG encoding blocks the worker.
            sessionPolicy.deactivateOwned(sessionId)
            worker.post {
                if (flow?.sessionId == sessionId) finishCapture("Android stopped screen sharing. Share again to restart.")
            }
        }
        override fun onCapturedContentResize(width: Int, height: Int) {
            worker.post {
                if (Build.VERSION.SDK_INT >= 34 && width > 0 && height > 0 && flow?.sessionId == sessionId && isSessionActive(sessionId)) resizeCapture(width, height)
            }
        }
        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            worker.post {
                if (Build.VERSION.SDK_INT < 34 || flow?.sessionId != sessionId || !isSessionActive(sessionId) || capturedContentVisible == isVisible) return@post
                capturedContentVisible = isVisible
                latestEncodedImage = null
                mutableState.value = state.value.copy(
                    message = if (isVisible) "Google Maps is visible. Waiting for a fresh image…" else "Shared app is not visible; open Google Maps to resume.",
                    acknowledgedFrame = 0L
                )
                if (isVisible) {
                    // A new surface cannot contain a queued image from before the selected app was hidden.
                    captureSize?.let { resizeCapture(it.width, it.height, forceNewSurface = true) }
                } else {
                    try {
                        virtualDisplay?.surface = null
                        val old = imageReader
                        imageReader = null
                        old?.setOnImageAvailableListener(null, null)
                        old?.close()
                    } catch (_: RuntimeException) {
                        finishCapture("The shared app became hidden and capture stopped. Share again to restart.")
                    }
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            sessionPolicy.invalidate()
            consentPolicy.invalidate()
            worker.post { finishCapture("Phone screen locked or turned off. Unlock and share again.") }
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            val current = flow ?: return
            when {
                !BluetoothSyncService.connectionState.value.isConnected -> finishCapture("Bluetooth disconnected. Share again after reconnecting.")
                !screenAvailable() -> finishCapture("Phone screen locked or turned off. Unlock and share again.")
                current.timedOut(SystemClock.elapsedRealtime()) -> finishCapture("Watch did not confirm the image in time. Check the connection and share again.")
                else -> {
                    imageReader?.let { consumeLatestImage(it) }
                    if (flow != null) worker.postDelayed(this, 500L)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        captureThread = HandlerThread("PhoneScreenCapture").apply { start() }
        worker = Handler(captureThread.looper)
        instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Phone screen sharing", NotificationManager.IMPORTANCE_LOW)
        )
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sessionPolicy.invalidate()
            consentPolicy.invalidate()
            worker.post { finishCapture("Screen sharing stopped.") }
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || state.value.active) {
            if (!state.value.active) stopSelf(startId)
            return START_NOT_STICKY
        }
        val consent = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra(EXTRA_CONSENT) as? Intent)
        val consentId = intent.getStringExtra(EXTRA_CONSENT_ID)
        intent.removeExtra(EXTRA_CONSENT)
        if (consent == null || consentId == null || !consentPolicy.isPending(consentId)) {
            consentFailed("Screen sharing was not approved. Tap Share Google Maps to try again.")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            val stopIntent = PendingIntent.getService(this, 0, Intent(this, PhoneScreenShareService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val openIntent = PendingIntent.getActivity(this, 0, Intent(this, PhoneScreenShareActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Phone screen shared to watch")
                .setContentText("Your approved screen is being sent over Bluetooth. Tap Stop to end sharing.")
                .setContentIntent(openIntent).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_media_pause, "Stop sharing", stopIntent).build()
            // Android 14 requires foreground mediaProjection *after consent* and before getMediaProjection.
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(NOTIFICATION_ID, notification)
            worker.post { startCapture(consent, consentId) }
        } catch (_: RuntimeException) {
            consentFailed("Android could not start screen sharing. Keep HealthSync open and try again.")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startCapture(consent: Intent, consentId: String) {
        if (flow != null) return
        val connection = BluetoothSyncService.connectionState.value
        // Use the epoch recorded when the user began this consent attempt, never a newer reconnect epoch.
        val approvedGeneration = consentPolicy.generationFor(consentId)
        if (!state.value.pending || approvedGeneration == null || !consentPolicy.consume(consentId, connection.deviceAddress)) {
            finishCapture("Screen sharing was canceled or the connection changed. Approve sharing again.")
            return
        }
        if (!connection.isConnected || !connection.supportsPhoneScreenShare) {
            finishCapture("Connect a watch with the latest HealthSync app before sharing.")
            return
        }
        if (!screenAvailable()) {
            finishCapture("Unlock your phone and keep its screen on before sharing.")
            return
        }
        try {
            val sessionId = UUID.randomUUID().toString()
            val activated = synchronized(lifetimeLock) {
                if (destroyed || instance !== this || !sessionPolicy.activate(sessionId, connection.deviceAddress, approvedGeneration)) false
                else { ownedSessionId = sessionId; true }
            }
            if (!activated) {
                finishCapture("Screen sharing was canceled or the connection changed. Approve sharing again.")
                return
            }
            flow = PhoneScreenFlowControl(sessionId, SystemClock.elapsedRealtime())
            mutableState.value = PhoneScreenShareState(active = true, message = "Waiting for the watch to confirm the first image…")
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(Activity.RESULT_OK, consent)
            val approvedProjection = projection ?: error("Projection unavailable")
            val callback = newProjectionCallback(sessionId)
            projectionCallback = callback
            approvedProjection.registerCallback(callback, control)
            val dimensions = displayDimensions()
            val size = PhoneScreenCapturePolicy.boundedSize(dimensions.width, dimensions.height)
            imageReader = newReader(size)
            captureSize = size
            // Exactly one virtual display for this fresh consent; rotation uses resize, never recreation.
            virtualDisplay = approvedProjection.createVirtualDisplay(
                "HealthSync watch screen", size.width, size.height, resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, worker
            )
            if (!isSessionActive(sessionId) || !BluetoothSyncService.sendPhoneScreen(PhoneScreenPayload(kind = "start", sessionId = sessionId))) {
                finishCapture("Could not send to the watch. Reconnect and share again.")
                return
            }
            worker.postDelayed(watchdog, 1_000L)
        } catch (_: RuntimeException) {
            finishCapture("Android could not capture this screen. Approve a new screen sharing session to try again.")
        }
    }

    private fun receiveWatchMessage(payload: PhoneScreenPayload) {
        val current = flow
        when (payload.kind) {
            "request" -> BluetoothSyncService.sendPhoneScreen(if (current == null)
                PhoneScreenPayload(kind = "stop", sessionId = payload.sessionId.takeIf { PhoneScreenProtocol.validSession(it) } ?: UUID.randomUUID().toString(), message = "No phone screen is being shared. Start sharing on your phone.")
                else PhoneScreenPayload(kind = "start", sessionId = current.sessionId))
            "ack" -> if (current?.acknowledge(payload.sessionId, payload.sequence, SystemClock.elapsedRealtime()) == true && payload.sequence > 0L && capturedContentVisible) {
                mutableState.value = state.value.copy(message = "Sharing live phone screen. Watch confirmed image ${payload.sequence}.", acknowledgedFrame = payload.sequence)
            }
            "stop" -> if (current?.sessionId == payload.sessionId) finishCapture("Screen sharing stopped by the watch.", sendPeer = false)
        }
    }

    private fun newReader(size: PhoneCaptureSize): ImageReader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2).also { reader ->
        reader.setOnImageAvailableListener({ source -> consumeLatestImage(source) }, worker)
    }

    private fun consumeLatestImage(reader: ImageReader) {
        val current = flow ?: return
        if (!isSessionActive(current.sessionId)) return
        if (!capturedContentVisible) {
            // Drain hidden-app buffers without encoding or sending; the watch ages out its old image.
            runCatching { reader.acquireLatestImage()?.close() }
            return
        }
        if (!current.canCapture(SystemClock.elapsedRealtime())) return
        val image = try { reader.acquireLatestImage() } catch (_: IllegalStateException) { null }
        try {
            if (!screenAvailable()) { finishCapture("Phone screen locked or turned off. Unlock and share again."); return }
            if (image != null) {
                latestEncodedImage = encodeImage(image) ?: run {
                    finishCapture("The screen image could not fit the Bluetooth transfer. Try again with Google Maps visible.")
                    return
                }
            }
            // A static app may produce no new buffers. Reconfirm its last actual captured image,
            // rather than discarding the initial image before ACK or falsely reporting link staleness.
            val encoded = latestEncodedImage ?: return
            // Stop/disconnect may have happened on another thread during JPEG encoding.
            if (!isSessionActive(current.sessionId)) return
            val sequence = current.reserveFrame(SystemClock.elapsedRealtime()) ?: return
            val payload = PhoneScreenPayload("frame", current.sessionId, sequence, encoded.width, encoded.height, Base64.encodeToString(encoded.bytes, Base64.NO_WRAP))
            if (!BluetoothSyncService.sendPhoneScreen(payload)) finishCapture("Bluetooth is busy or disconnected. Share again when the connection is ready.")
        } catch (_: RuntimeException) {
            finishCapture("Screen capture failed. Share Google Maps again to restart.")
        } finally {
            image?.close()
        }
    }

    private data class EncodedImage(val width: Int, val height: Int, val bytes: ByteArray)

    private fun encodeImage(image: Image): EncodedImage? {
        if (image.width !in 1..PhoneScreenProtocol.MAX_EDGE || image.height !in 1..PhoneScreenProtocol.MAX_EDGE ||
            image.width.toLong() * image.height > PhoneScreenProtocol.MAX_PIXELS) return null
        val plane = image.planes.firstOrNull() ?: return null
        if (plane.pixelStride != 4 || plane.rowStride < image.width * 4) return null
        // Copy only visible pixels; ImageReader row padding must never expand our bitmap dimensions.
        val pixels = ByteArray(image.width * image.height * 4)
        val buffer = plane.buffer.duplicate()
        for (row in 0 until image.height) {
            buffer.position(row * plane.rowStride)
            buffer.get(pixels, row * image.width * 4, image.width * 4)
        }
        var bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
            repeat(6) {
                for (quality in intArrayOf(72, 52, 35)) {
                    val output = ByteArrayOutputStream()
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
                    if (output.size() <= PhoneScreenProtocol.MAX_IMAGE_BYTES) return EncodedImage(bitmap.width, bitmap.height, output.toByteArray())
                }
                if (minOf(bitmap.width, bitmap.height) < 80) return null
                val smaller = Bitmap.createScaledBitmap(bitmap, maxOf(1, bitmap.width * 3 / 4), maxOf(1, bitmap.height * 3 / 4), true)
                if (smaller !== bitmap) bitmap.recycle()
                bitmap = smaller
            }
            return null
        } finally { bitmap.recycle() }
    }

    private fun resizeCapture(width: Int, height: Int, forceNewSurface: Boolean = false) {
        val display = virtualDisplay ?: return
        val size = PhoneScreenCapturePolicy.boundedSize(width, height)
        if (!capturedContentVisible) {
            // Keep the display detached while the selected app is hidden. Visibility creates a fresh surface.
            captureSize = size
            latestEncodedImage = null
            return
        }
        if (size == captureSize && !forceNewSurface) return
        var replacement: ImageReader? = null
        try {
            replacement = newReader(size)
            display.surface = null
            display.resize(size.width, size.height, resources.configuration.densityDpi)
            display.surface = replacement.surface
            val old = imageReader
            imageReader = replacement
            captureSize = size
            latestEncodedImage = null
            old?.setOnImageAvailableListener(null, null)
            old?.close()
        } catch (_: RuntimeException) {
            if (imageReader !== replacement) replacement?.close()
            finishCapture("Screen size changed and capture stopped. Share again to restart.")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Older Android has no captured-content callback. Full-screen projection follows display rotation.
        if (Build.VERSION.SDK_INT < 34) worker.post {
            if (flow != null) displayDimensions().let { resizeCapture(it.width, it.height) }
        }
    }

    private fun displayDimensions(): PhoneCaptureSize {
        val manager = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) return manager.maximumWindowMetrics.bounds.let { PhoneCaptureSize(maxOf(1, it.width()), maxOf(1, it.height())) }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        manager.defaultDisplay.getRealMetrics(metrics)
        return PhoneCaptureSize(maxOf(1, metrics.widthPixels), maxOf(1, metrics.heightPixels))
    }

    private fun screenAvailable(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun finishCapture(message: String, sendPeer: Boolean = true, stopService: Boolean = true) {
        val session = flow?.sessionId
        sessionPolicy.deactivateOwned(session ?: ownedSessionId)
        flow = null
        worker.removeCallbacks(watchdog)
        // Release every producer/consumer and unregister before stopping projection to avoid callback recursion.
        imageReader?.setOnImageAvailableListener(null, null)
        runCatching { virtualDisplay?.surface = null }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        captureSize = null
        latestEncodedImage = null
        capturedContentVisible = true
        val oldProjection = projection
        projection = null
        val oldCallback = projectionCallback
        projectionCallback = null
        if (oldCallback != null) runCatching { oldProjection?.unregisterCallback(oldCallback) }
        runCatching { oldProjection?.stop() }
        if (session != null && sendPeer) BluetoothSyncService.sendPhoneScreen(PhoneScreenPayload(kind = "stop", sessionId = session, message = message.take(160)))
        if (instance === this) mutableState.value = PhoneScreenShareState(message = message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopService) stopSelf()
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        receiverRegistered = false
        synchronized(lifetimeLock) {
            destroyed = true
            sessionPolicy.deactivateOwned(ownedSessionId)
        }
        if (instance === this) {
            if (state.value.active) mutableState.value = PhoneScreenShareState(message = "Phone screen sharing ended.")
            instance = null
        }
        worker.post {
            // Preserve the useful reason from a normal stop; unexpected service death must clear the watch.
            if (flow != null || projection != null) finishCapture("Phone screen sharing ended.", stopService = false)
            captureThread.quitSafely()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

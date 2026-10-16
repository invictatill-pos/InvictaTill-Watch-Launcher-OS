package com.healthsync.watch.ui.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.healthsync.watch.data.MessageType
import com.healthsync.watch.data.PhoneScreenPayload
import com.healthsync.watch.data.PhoneScreenProtocol
import com.healthsync.watch.data.SyncMessage
import com.healthsync.watch.service.BluetoothClientService
import java.util.concurrent.Executors

/** Pixel data exists only in process memory. No broadcasts, disk cache, logs or background launch. */
object PhoneScreenBridge {
    data class State(
        val connected: Boolean,
        val sessionId: String?,
        val bitmap: Bitmap?,
        val receivedAt: Long?,
        val message: String
    ) {
        fun isFresh(now: Long = SystemClock.elapsedRealtime()) = sessionId != null && connected &&
            bitmap != null && receivedAt?.let { now >= it && now - it < PhoneScreenProtocol.STALE_AFTER_MS } == true
    }

    private const val CHANNEL = "phone_screen_view"
    private const val NOTIFICATION_ID = 2110
    private val lock = Any()
    private val policy = PhoneScreenReceiverPolicy()
    private val main = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor { task -> Thread(task, "phone-screen-decode") }
    private val gson = Gson()
    private val observers = linkedSetOf<() -> Unit>()
    private var latestBitmap: Bitmap? = null
    private var decoding = false
    // One bounded compressed frame may wait behind a decode; never queue unbounded executor tasks.
    private var pending: PhoneScreenPayload? = null

    fun state(): State = synchronized(lock) {
        State(policy.connected, policy.sessionId, latestBitmap, policy.frameReceivedAt, policy.message)
    }

    /** Observe on the main thread; the activity removes its observer while it is not visible. */
    fun observe(observer: () -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        observers.add(observer)
        observer()
    }

    fun removeObserver(observer: () -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        observers.remove(observer)
    }

    fun connectionChanged(context: Context, connected: Boolean) {
        synchronized(lock) {
            policy.connectionChanged(connected)
            if (!connected) { latestBitmap = null; pending = null }
        }
        changed(context.applicationContext)
    }

    fun receive(context: Context, payload: PhoneScreenPayload) {
        val app = context.applicationContext
        when (payload.kind) {
            "start" -> {
                val accepted = synchronized(lock) {
                    val previous = policy.sessionId
                    policy.start(payload).also { accepted ->
                        if (accepted && previous != policy.sessionId) { latestBitmap = null; pending = null }
                    }
                }
                if (accepted) {
                    send(PhoneScreenPayload(kind = "ack", sessionId = payload.sessionId, sequence = 0))
                    changed(app)
                }
            }
            "stop" -> {
                val accepted = synchronized(lock) {
                    policy.stop(payload).also { if (it) { latestBitmap = null; pending = null } }
                }
                if (accepted) changed(app)
            }
            "frame" -> queueFrame(app, payload)
        }
    }

    fun requestCurrentSession(): Boolean {
        val session = synchronized(lock) { policy.sessionId.orEmpty() }
        return send(PhoneScreenPayload(kind = "request", sessionId = session))
    }

    /** Always clear locally, including when the phone has already disconnected. */
    fun stop(context: Context): Boolean {
        val session = synchronized(lock) {
            policy.stopLocally().also { latestBitmap = null; pending = null }
        }
        changed(context.applicationContext)
        return session != null && send(PhoneScreenPayload(kind = "stop", sessionId = session,
            message = "Sharing stopped from the watch."))
    }

    private fun queueFrame(context: Context, payload: PhoneScreenPayload) {
        synchronized(lock) {
            if (policy.beginFrame(payload) == null) return
            if (decoding) {
                val waiting = pending
                if (waiting == null || waiting.sessionId != payload.sessionId || payload.sequence > waiting.sequence) pending = payload
                return
            }
            decoding = true
        }
        decoder.execute {
            var next: PhoneScreenPayload? = payload
            while (next != null) {
                decode(context, next)
                next = synchronized(lock) {
                    val waiting = pending
                    pending = null
                    if (waiting == null) decoding = false
                    waiting
                }
            }
        }
    }

    private fun decode(context: Context, payload: PhoneScreenPayload) {
        val ticket = synchronized(lock) { policy.beginFrame(payload) } ?: return
        val bitmap = try {
            // Base64 overhead was bounded by validFrame before even this compressed allocation.
            val bytes = Base64.decode(payload.imageBase64, Base64.NO_WRAP)
            if (!validPhoneJpegBytes(bytes)) return
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (!validPhoneFrameBounds(payload.width, payload.height, bounds.outWidth, bounds.outHeight, bounds.outMimeType)) return
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inScaled = false
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return
        } catch (_: RuntimeException) {
            return
        } catch (_: OutOfMemoryError) {
            return
        }
        if (!validPhoneFrameBounds(payload.width, payload.height, bitmap.width, bitmap.height, "image/jpeg")) {
            bitmap.recycle()
            return
        }
        val accepted = synchronized(lock) {
            policy.commitFrame(ticket, SystemClock.elapsedRealtime()).also { if (it) latestBitmap = bitmap }
        }
        if (!accepted) {
            // This bitmap has never been exposed to a drawing view.
            bitmap.recycle()
            return
        }
        send(PhoneScreenPayload(kind = "ack", sessionId = ticket.sessionId, sequence = ticket.sequence))
        changed(context)
    }

    private fun send(payload: PhoneScreenPayload): Boolean = BluetoothClientService.sendRawMsg(
        SyncMessage(MessageType.PHONE_SCREEN, payload = gson.toJson(payload)))

    private fun changed(context: Context) {
        main.post {
            // Read current state at delivery so queued callbacks cannot resurrect a stopped image.
            observers.toList().forEach { it() }
            updateNotification(context)
        }
    }

    private fun updateNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val current = state()
        if (current.sessionId == null || !current.connected) { manager.cancel(NOTIFICATION_ID); return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Live phone view", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Open a user-approved phone screen session. Does not wake the screen."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                enableVibration(false)
                setSound(null, null)
            })
        }
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, PhoneMapActivity.intentFor(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mapmode).setContentTitle("Phone screen session").build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mapmode)
            .setContentTitle("Live phone map")
            .setContentText("Tap to see the phone screen")
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public).build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

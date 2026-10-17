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
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.healthsync.watch.data.MessageType
import com.healthsync.watch.data.NavigationPayload
import com.healthsync.watch.data.NavigationProtocol
import com.healthsync.watch.data.SyncMessage
import com.healthsync.watch.service.BluetoothClientService

/** Source instructions and a tiny optional source icon stay in process memory only. */
object NavigationBridge {
    data class State(val connected: Boolean, val status: String, val payload: NavigationPayload?,
        val icon: Bitmap?, val receivedAt: Long?) {
        fun isFresh(now: Long = SystemClock.elapsedRealtime()): Boolean = connected && status == "ACTIVE" &&
            payload != null && receivedAt?.let { now >= it && now - it < NavigationProtocol.STALE_AFTER_MS } == true
    }

    const val CHANNEL_ID = "watch_navigation"
    private const val NOTIFICATION_ID = 2111
    private val lock = Any()
    private val policy = NavigationReceiverPolicy()
    private val gson = Gson()
    private val main = Handler(Looper.getMainLooper())
    private val observers = linkedSetOf<() -> Unit>()
    private var icon: Bitmap? = null
    private var encodedIcon = ""
    private var expiry: Runnable? = null

    fun state(): State = synchronized(lock) {
        val current = policy.snapshot(SystemClock.elapsedRealtime())
        State(current.connected, current.status.name, current.payload,
            if (current.status == NavigationReceiverPolicy.Status.ACTIVE) icon else null, current.receivedAt)
    }

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
            if (!connected) { icon = null; encodedIcon = "" }
        }
        changed(context.applicationContext)
    }

    fun receive(context: Context, payload: NavigationPayload) {
        val receipt = synchronized(lock) {
            policy.receive(payload, SystemClock.elapsedRealtime()).also { result ->
                if (result.accepted) {
                    if (payload.status != "ACTIVE") { icon = null; encodedIcon = "" }
                    else if (payload.iconBase64 != encodedIcon) {
                        // The protocol gate bounded the base64 before decode. At most 96 x 96 pixels.
                        encodedIcon = payload.iconBase64
                        icon = decodeIcon(encodedIcon)
                    }
                }
            }
        }
        if (receipt.accepted) changed(context.applicationContext, payload.takeIf { receipt.maneuverChanged })
    }

    fun requestCurrentState(): Boolean = BluetoothClientService.sendRawMsg(
        SyncMessage(MessageType.NAVIGATION, payload = gson.toJson(NavigationPayload())))

    private fun decodeIcon(encoded: String): Bitmap? {
        if (encoded.isEmpty() || encoded.length > NavigationProtocol.MAX_ICON_BASE64) return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (!validNavigationPngBytes(bytes)) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (!validNavigationIconBounds(bounds.outWidth, bounds.outHeight, bounds.outMimeType)) return null
            val options = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            if (result.width != bounds.outWidth || result.height != bounds.outHeight ||
                !validNavigationIconBounds(result.width, result.height, "image/png")) {
                result.recycle(); null
            } else result
        } catch (_: RuntimeException) { null } catch (_: OutOfMemoryError) { null }
    }

    private fun changed(context: Context, cue: NavigationPayload? = null) {
        main.post {
            expiry?.let(main::removeCallbacks)
            expiry = null
            val current = state()
            if (current.isFresh()) {
                val remaining = NavigationProtocol.STALE_AFTER_MS -
                    (SystemClock.elapsedRealtime() - (current.receivedAt ?: 0L))
                expiry = Runnable { changed(context) }.also { main.postDelayed(it, remaining.coerceAtLeast(1L)) }
            } else if (current.status == "STALE") synchronized(lock) { icon = null; encodedIcon = "" }
            observers.toList().forEach { it() }
            updateNotification(context, current)
            // A queued event cannot alert for a maneuver that has already stopped or been replaced.
            if (cue != null && current.isFresh() && current.payload?.streamId == cue.streamId &&
                current.payload.sequence == cue.sequence) vibrate(context)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Navigation", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet route shortcut. Vibration controls brief cues when the next turn changes."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 80, 100)
            })
    }

    private fun updateNotification(context: Context, current: State) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!current.isFresh()) { manager.cancel(NOTIFICATION_ID); return }
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, NavigationActivity.intentFor(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions).setContentTitle("Navigation active").build()
        val p = current.payload ?: return
        val text = listOf(p.distance, p.instruction).filter { it.isNotBlank() }.joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions).setContentTitle("Google Maps navigation")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public).setShowWhen(false).setTimeoutAfter(NavigationProtocol.STALE_AFTER_MS).build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun vibrate(context: Context) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL ||
                !NotificationManagerCompat.from(context).areNotificationsEnabled()) return@runCatching
            val audio = context.getSystemService(AudioManager::class.java)
            if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT ||
                Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return@runCatching
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = manager.getNotificationChannel(CHANNEL_ID)
                if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE || !channel.shouldVibrate()) return@runCatching
            }
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator?.hasVibrator() != true) return@runCatching
            val pattern = longArrayOf(0, 100, 80, 100)
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else { @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1) }
        }
    }
}

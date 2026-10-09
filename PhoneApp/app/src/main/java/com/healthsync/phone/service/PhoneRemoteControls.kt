package com.healthsync.phone.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.healthsync.phone.MainActivity
import com.healthsync.phone.data.model.PhoneControlPayload
import com.healthsync.phone.data.model.PhoneControlStatePayload
import com.healthsync.phone.data.model.validatedPhoneControlAction

/** Phone-side controls use ordinary media sessions and the user's current sound settings. */
internal class PhoneRemoteControls(
    private val context: Context,
    private val emit: (PhoneControlStatePayload) -> Unit
) {
    companion object {
        private const val CHANNEL = "find_phone_alert"
        private const val NOTIFICATION_ID = 8505
        private const val ACTION_STOP = "com.healthsync.phone.STOP_FIND_PHONE"
    }
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var finderWakeLock: PowerManager.WakeLock? = null
    private var finding = false
    @Volatile private var destroyed = false
    private var registered = false
    private val stop = Runnable { stopFinding(); emitState(0L, null) }
    private val repeat = object : Runnable {
        override fun run() {
            if (!finding) return
            val tone = ringtone
            if (tone == null) { stopFinding(); emitState(0L, "Phone alert stopped"); return }
            try { if (!tone.isPlaying) tone.play() }
            catch (_: Exception) { stopFinding(); emitState(0L, "Phone could not play the alert"); return }
            handler.postDelayed(this, 1_000L)
        }
    }
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) { stopFinding(); emitState(0L, null) }
        }
    }

    init {
        runCatching {
            ContextCompat.registerReceiver(context, stopReceiver, IntentFilter(ACTION_STOP), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
    }

    fun handle(command: PhoneControlPayload) {
        handler.post {
            if (destroyed) return@post
            val action = validatedPhoneControlAction(command.action)
            if (command.requestId <= 0L || action == null) {
                emitState(command.requestId, "Unsupported phone command")
                return@post
            }
            val error = when (action) {
                "STATE" -> null
                "FIND_START" -> startFinding()
                "FIND_STOP" -> { stopFinding(); null }
                else -> controlMedia(action)
            }
            // Media apps update PlaybackState asynchronously; ask again on the next panel poll too.
            if (action in setOf("PLAY", "PAUSE", "PLAY_PAUSE", "NEXT", "PREVIOUS") && error == null)
                handler.postDelayed({ if (!destroyed) emitState(command.requestId, null) }, 300L)
            else emitState(command.requestId, error)
        }
    }

    private fun mediaSession(): Pair<MediaController?, String?> {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return null to "Media controls are unavailable on this phone"
        val sessions = try {
            manager.getActiveSessions(ComponentName(context, HealthNotificationListenerService::class.java))
        } catch (_: SecurityException) {
            return null to "Allow notification access in the phone app"
        } catch (_: Exception) {
            return null to "Phone media sessions could not load"
        }
        val selected = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: sessions.firstOrNull { it.playbackState != null }
        return selected to if (selected == null) "Open a music app on your phone" else null
    }

    private fun controlMedia(action: String): String? {
        val (controller, unavailable) = mediaSession()
        if (controller == null) return unavailable
        return try {
            val state = controller.playbackState
            val target = if (action == "PLAY_PAUSE") {
                if (state?.state == PlaybackState.STATE_PLAYING) "PAUSE" else "PLAY"
            } else action
            val supported = when (target) {
                "PLAY" -> PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE
                "PAUSE" -> PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
                "NEXT" -> PlaybackState.ACTION_SKIP_TO_NEXT
                "PREVIOUS" -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
                else -> 0L
            }
            if (state == null || state.actions and supported == 0L) return "The music app does not support this control"
            // Some apps expose only the toggle action, without separate play/pause actions.
            val explicitAction = if (target == "PLAY") PlaybackState.ACTION_PLAY else PlaybackState.ACTION_PAUSE
            if (target in setOf("PLAY", "PAUSE") && state.actions and explicitAction == 0L) {
                val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val accepted = controller.dispatchMediaButtonEvent(down)
                controller.dispatchMediaButtonEvent(up)
                return if (accepted) null else "The music app did not accept this control"
            }
            when (target) {
                "PLAY" -> controller.transportControls.play()
                "PAUSE" -> controller.transportControls.pause()
                "NEXT" -> controller.transportControls.skipToNext()
                "PREVIOUS" -> controller.transportControls.skipToPrevious()
            }
            null
        } catch (_: Exception) { "The music app could not receive the command" }
    }

    private fun emitState(requestId: Long, error: String?) {
        if (destroyed) return
        val (controller, mediaError) = mediaSession()
        val metadata = runCatching { controller?.metadata }.getOrNull()
        val playback = runCatching { controller?.playbackState }.getOrNull()
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100L / scale).toInt().coerceIn(0, 100) else -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        emit(PhoneControlStatePayload(
            requestId = requestId,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().take(120),
            artist = (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty().take(120),
            playing = playback?.state == PlaybackState.STATE_PLAYING,
            available = controller != null && playback != null,
            batteryPercent = percent,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            findingPhone = finding,
            error = (error ?: mediaError)?.take(180)
        ))
    }

    private fun startFinding(): String? {
        stopFinding()
        if (!registered) return "Phone alert controls are unavailable"
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "Allow notifications in the phone app to ring it"
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio?.getStreamVolume(AudioManager.STREAM_ALARM) == 0) return "Phone alarm volume is muted"
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return "No phone alert sound is configured"
        return try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Find phone", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Stops an alert requested from your connected watch"
                setSound(null, null)
            })
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                return "Enable the Find phone notification channel on your phone"
            val stopIntent = PendingIntent.getBroadcast(context, NOTIFICATION_ID,
                Intent(ACTION_STOP).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val openIntent = PendingIntent.getActivity(context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Your watch is finding this phone")
                .setContentText("Alert stops automatically after 15 seconds")
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop sound", stopIntent)
                .setOngoing(true).setSilent(true).build())
            val tone = RingtoneManager.getRingtone(context, uri)
                ?: run { manager.cancel(NOTIFICATION_ID); return "Phone alert sound is unavailable" }
            tone.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 28) tone.isLooping = true
            ringtone = tone
            tone.play()
            if (!tone.isPlaying) { stopFinding(); return "Phone could not play the alert" }
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: run { stopFinding(); return "Phone alert timing is unavailable" }
            finderWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HealthSync:FindPhone")
                .apply { setReferenceCounted(false) }
            // Handler delays use uptime. Keep this short user-triggered alert awake until its stop fires.
            finderWakeLock?.acquire(17_000L)
            finding = true
            handler.postDelayed(stop, 15_000L)
            handler.postDelayed(repeat, 1_000L)
            null
        } catch (_: Exception) { stopFinding(); "Phone could not start the alert" }
    }

    private fun stopFinding() {
        handler.removeCallbacks(stop); handler.removeCallbacks(repeat)
        runCatching { ringtone?.stop() }
        ringtone = null; finding = false
        runCatching { finderWakeLock?.let { if (it.isHeld) it.release() } }
        finderWakeLock = null
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
    }

    fun disconnect() { handler.post { stopFinding() } }
    fun destroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        stopFinding()
        if (registered) { runCatching { context.unregisterReceiver(stopReceiver) }; registered = false }
    }
}

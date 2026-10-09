package com.healthsync.watch.service

import android.app.Service
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.healthsync.watch.timer.TimerPhase
import com.healthsync.watch.timer.WatchTimerScheduler
import com.healthsync.watch.timer.WatchTimerStore
import java.util.Locale

/** No countdown wake lock: the wake-up alarm delivers expiration while the display sleeps. */
class WatchTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val state = WatchTimerStore.read(this@WatchTimerService)
            if (state.phase != TimerPhase.RUNNING) { stopSelf(); return }
            val remaining = state.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(this@WatchTimerService))
            if (remaining <= 0) { WatchTimerScheduler.expire(this@WatchTimerService); stopSelf(); return }
            if (Build.VERSION.SDK_INT < 24) {
                runCatching {
                    (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)
                        ?.notify(WatchTimerScheduler.LIVE_NOTIFICATION_ID, buildNotification(remaining))
                }
            }
            // Countdown chronometer ticks in the notification UI without service polling each second.
            handler.postDelayed(this, remaining.coerceAtMost(30_000L))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WatchTimerScheduler.createChannels(this)
        val state = WatchTimerStore.read(this)
        val remaining = state.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(this))
        val notification = buildNotification(remaining)
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(WatchTimerScheduler.LIVE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(WatchTimerScheduler.LIVE_NOTIFICATION_ID, notification)
        } catch (_: RuntimeException) {
            // The persisted alarm remains scheduled if firmware refuses a foreground notification.
            stopSelf(); return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        if (state.phase != TimerPhase.RUNNING) { stopSelf(); return START_NOT_STICKY }
        handler.post(tick)
        return START_STICKY
    }

    private fun buildNotification(remaining: Long): Notification {
        val approximate = !WatchTimerStore.exact(this)
        val detail = if (Build.VERSION.SDK_INT >= 24) "Countdown running" else "${remainingText(remaining)} remaining"
        val builder = NotificationCompat.Builder(this, WatchTimerScheduler.LIVE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Watch timer")
            .setContentText(if (approximate) "$detail · approximate alert" else detail)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(WatchTimerScheduler.openIntent(this))
            .addAction(android.R.drawable.ic_media_pause, "Stop", WatchTimerScheduler.stopIntent(this))
        if (Build.VERSION.SDK_INT >= 24) {
            builder.setWhen(System.currentTimeMillis() + remaining)
                .setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false).setUsesChronometer(false)
        }
        return builder.build()
    }

    private fun remainingText(remaining: Long): String {
        val seconds = (remaining.coerceAtLeast(0) + 999L) / 1_000L
        return if (seconds >= 3_600L) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60L, seconds % 60L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopForeground(true)
        super.onDestroy()
    }
}

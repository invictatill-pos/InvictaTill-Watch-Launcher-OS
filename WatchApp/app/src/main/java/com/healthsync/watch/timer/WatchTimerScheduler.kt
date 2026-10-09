package com.healthsync.watch.timer

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.healthsync.watch.receiver.WatchTimerReceiver
import com.healthsync.watch.service.WatchTimerService
import com.healthsync.watch.ui.shell.WatchUtilitiesActivity

/** Exact alarms where granted; the fallback deliberately reports its approximate delivery. */
object WatchTimerScheduler {
    const val ACTION_EXPIRE = "com.healthsync.watch.TIMER_EXPIRE"
    const val ACTION_STOP = "com.healthsync.watch.TIMER_STOP"
    const val LIVE_NOTIFICATION_ID = 6101
    const val ALERT_NOTIFICATION_ID = 6102
    const val LIVE_CHANNEL = "watch_timer_running"
    const val ALERT_CHANNEL = "watch_timer_alert"

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return Build.VERSION.SDK_INT < 31 || runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun start(context: Context, durationMs: Long): Boolean {
        stop(context)
        val state = TimerCheckpoint.start(durationMs, SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(context))
        return run(context, state)
    }

    fun resume(context: Context): Boolean {
        val state = WatchTimerStore.read(context)
        if (state.phase != TimerPhase.PAUSED || state.remainingAtAnchorMs <= 0) return false
        return run(context, state.resume(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(context)))
    }

    private fun run(context: Context, state: TimerCheckpoint): Boolean {
        WatchTimerStore.write(context, state)
        val scheduled = schedule(context)
        if (!scheduled) {
            WatchTimerStore.write(context, state.pause(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(context)))
            return false
        }
        return try {
            ContextCompat.startForegroundService(context, Intent(context, WatchTimerService::class.java))
            true
        } catch (_: RuntimeException) {
            cancelAlarm(context)
            WatchTimerStore.write(context, state.pause(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(context)))
            false
        }
    }

    fun pause(context: Context) {
        val state = WatchTimerStore.read(context)
        if (state.phase != TimerPhase.RUNNING) return
        if (state.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(context)) == 0L) {
            expire(context)
            return
        }
        WatchTimerStore.write(context, state.pause(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(context)))
        cancelAlarm(context)
        context.stopService(Intent(context, WatchTimerService::class.java))
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(LIVE_NOTIFICATION_ID)
    }

    fun stop(context: Context) {
        WatchTimerStore.write(context, TimerCheckpoint())
        cancelAlarm(context)
        context.stopService(Intent(context, WatchTimerService::class.java))
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.apply {
            cancel(LIVE_NOTIFICATION_ID); cancel(ALERT_NOTIFICATION_ID)
        }
    }

    fun cancelAlarm(context: Context) {
        runCatching { (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(alarmIntent(context)) }
    }

    /** Also used by the boot receiver; no foreground service is launched from boot. */
    fun schedule(context: Context): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val state = WatchTimerStore.read(context)
        if (state.phase != TimerPhase.RUNNING) return false
        val remaining = state.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(context))
        val trigger = SystemClock.elapsedRealtime() + remaining.coerceAtLeast(100L)
        val exact = canScheduleExact(context)
        if (exact) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, alarmIntent(context))
                WatchTimerStore.setExact(context, true)
                return true
            } catch (_: RuntimeException) { /* Access can be revoked between check and scheduling. */ }
        }
        return runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, alarmIntent(context))
            WatchTimerStore.setExact(context, false)
            true
        }.getOrDefault(false)
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context, 6100,
        Intent(context, WatchTimerReceiver::class.java).setAction(ACTION_EXPIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun openIntent(context: Context) = PendingIntent.getActivity(context, 6101,
        Intent(context, WatchUtilitiesActivity::class.java).putExtra(WatchUtilitiesActivity.EXTRA_TOOL, "timer")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun stopIntent(context: Context) = PendingIntent.getBroadcast(context, 6102,
        Intent(context, WatchTimerReceiver::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.createNotificationChannel(NotificationChannel(LIVE_CHANNEL, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Countdown remaining time and stop control"
            setSound(null, null); enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alert when a watch timer ends"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
        })
    }

    fun expire(context: Context) {
        if (!WatchTimerStore.finishIfDue(context, SystemClock.elapsedRealtime(), System.currentTimeMillis())) return
        cancelAlarm(context)
        createChannels(context)
        val alert = NotificationCompat.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Timer finished")
            .setContentText("Your watch timer is done. Tap Stop to dismiss.")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), android.media.AudioManager.STREAM_ALARM)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500)).setOngoing(true).setAutoCancel(false)
            .setContentIntent(openIntent(context)).addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent(context)).build()
        alert.flags = alert.flags or Notification.FLAG_INSISTENT
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(ALERT_NOTIFICATION_ID, alert) }
        }
        context.stopService(Intent(context, WatchTimerService::class.java))
    }
}

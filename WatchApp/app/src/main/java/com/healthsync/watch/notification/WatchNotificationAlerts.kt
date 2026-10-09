package com.healthsync.watch.notification

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.healthsync.watch.WatchApp
import com.healthsync.watch.data.NotificationPayload
import com.healthsync.watch.data.ReplyResultPayload
import com.healthsync.watch.data.WatchPreferences
import com.healthsync.watch.ui.calls.WatchCallBridge

/** Incoming messages always reach the inbox, even when alert permission or popups are off. */
object WatchNotificationAlerts {
    const val CHANNEL_ID = "watch_messages_v2"
    private const val ALERT_ID = 2102
    private const val TAG = "WatchMessageAlerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Phone messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Messages from your paired phone. Popups and screen wake are in Watch notifications."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 120, 70, 120)
            })
    }

    fun receive(context: Context, payload: NotificationPayload) {
        runCatching {
            val p = payload.copy(packageName = payload.packageName.orEmpty(), notificationKey = payload.notificationKey.orEmpty(),
                appLabel = payload.appLabel.orEmpty().take(200), title = payload.title.orEmpty().take(1000), text = payload.text.orEmpty().take(8000),
                ticker = payload.ticker.orEmpty(), category = payload.category.orEmpty(), channelName = payload.channelName.orEmpty(), conversationTitle = payload.conversationTitle.orEmpty())
            val (id, contentChanged) = NotificationInboxStore.getInstance(context).recordDelivery(p)
            if (!p.isActive) context.getSystemService(NotificationManager::class.java).cancel(alertTag(p.notificationKey), ALERT_ID)
            changed(context)
            presentSaved(context, p, id, contentChanged, NotificationInboxStore.SOURCE_PHONE)
        }.onFailure { Log.w(TAG, "Message could not be saved", it) }
    }

    fun removed(context: Context, key: String) {
        if (key.isBlank()) return
        runCatching { NotificationInboxStore.getInstance(context).deactivatePhoneKey(key) }
        context.getSystemService(NotificationManager::class.java).cancel(alertTag(key), ALERT_ID)
        changed(context)
    }

    fun snapshot(context: Context, keys: List<String>) {
        runCatching { NotificationInboxStore.getInstance(context).reconcilePhoneActiveKeys(keys) }
        // App filters can change while connected; their previously posted system copies must
        // disappear together with reply availability, without emitting another alert.
        runCatching {
            val active = keys.take(1000).filter { it.isNotBlank() && it.length <= 2048 }.toSet()
            val manager = context.getSystemService(NotificationManager::class.java)
            val prefix = "${NotificationInboxStore.SOURCE_PHONE}-message:"
            manager.activeNotifications.filter { it.id == ALERT_ID && it.tag?.startsWith(prefix) == true }
                .filter { it.tag.orEmpty().removePrefix(prefix) !in active }.forEach { manager.cancel(it.tag, it.id) }
        }.onFailure { Log.w(TAG, "Some inactive system copies could not be removed", it) }
        changed(context)
    }

    fun replyResult(context: Context, result: ReplyResultPayload) {
        runCatching { NotificationInboxStore.getInstance(context).completeReply(result.requestId,
            result.notificationKey.orEmpty(), result.success, result.error.orEmpty()) }
        changed(context)
    }

    fun connectionChanged(context: Context, connected: Boolean) {
        // A restored card cannot reply until the phone has advertised its current active targets.
        if (!connected) runCatching { NotificationInboxStore.getInstance(context).deactivatePhoneReplies() }
        changed(context)
    }

    fun presentSaved(context: Context, payload: NotificationPayload, inboxId: Long, changed: Boolean,
        source: String, nativeAlertStillVisible: Boolean = false) {
        if (!shouldInterruptForNotification(payload, changed, WatchCallBridge.isCallActive(), source)) return
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = WatchPreferences(context)
        val power = context.getSystemService(PowerManager::class.java)
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 || (manager.getNotificationChannel(CHANNEL_ID)?.importance ?: 0) >= NotificationManager.IMPORTANCE_HIGH) &&
            manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        val alertIntent = NotificationDisplayActivity.intentFor(context, payload, inboxId, source = source)
            .putExtra(NotificationDisplayActivity.EXTRA_WAKE_SCREEN, prefs.watchNotificationWakeEnabled)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        // Android 10+ restricts background launches. A foreground service or notification access
        // alone is not sufficient permission; retain the system heads-up fallback on those watches.
        val popup = canAutomaticallyShowNotification(Build.VERSION.SDK_INT, WatchApp.isForeground,
            power.isInteractive, locked, prefs.watchNotificationPopupEnabled, allowed)
        if (allowed && prefs.watchNotificationWakeEnabled && !power.isInteractive && Build.VERSION.SDK_INT <= 32) wakeBriefly(power)
        if (popup && runCatching { context.startActivity(alertIntent); true }.getOrDefault(false)) {
            manager.cancel(alertTag(payload.notificationKey.orEmpty(), source), ALERT_ID)
            // Native Android alerts already vibrated. Mirrored phone alerts vibrate once here.
            if (source == NotificationInboxStore.SOURCE_PHONE) vibrate(context)
            return
        }
        if (source == NotificationInboxStore.SOURCE_WATCH && nativeAlertStillVisible) return
        val pending = PendingIntent.getActivity(context, inboxId.hashCode(), alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("New phone notification")
            .setContentText("Unlock your watch to read it").build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(payload.title.ifBlank { payload.appLabel })
            .setSubText((payload.appLabel + if (payload.conversationTitle.isNotBlank()) " · ${payload.conversationTitle}" else "").take(200))
            .setContentText(payload.text.take(500))
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.text.take(5000)))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(false)
            .setSilent(source == NotificationInboxStore.SOURCE_WATCH)
            .setWhen(payload.time).setShowWhen(true).setTimeoutAfter(24 * 60 * 60 * 1000L)
            .build()
        runCatching { manager.notify(alertTag(payload.notificationKey.orEmpty(), source), ALERT_ID, notification) }
            .onFailure { Log.w(TAG, "Allow watch notifications to receive message alerts", it) }
    }

    @Suppress("DEPRECATION")
    private fun wakeBriefly(power: PowerManager) {
        // Legacy Android watches need this before starting their alert activity. The timeout is
        // mandatory, independent of the activity lifecycle, and never keeps the display awake.
        runCatching {
            power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "HealthSync:message-wake").apply { setReferenceCounted(false); acquire(3_000L) }
        }.onFailure { Log.w(TAG, "Firmware refused message screen wake", it) }
    }

    private fun vibrate(context: Context) {
        runCatching {
            val channel = if (Build.VERSION.SDK_INT >= 26)
                context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID) else null
            if (Build.VERSION.SDK_INT >= 26 && channel?.shouldVibrate() != true) return@runCatching
            val pattern = if (Build.VERSION.SDK_INT >= 26) channel?.vibrationPattern?.takeIf { it.isNotEmpty() } else null
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(pattern ?: longArrayOf(0, 120, 70, 120), -1))
            else { @Suppress("DEPRECATION") vibrator?.vibrate(longArrayOf(0, 120, 70, 120), -1) }
        }
    }

    private fun alertTag(key: String, source: String = NotificationInboxStore.SOURCE_PHONE) = "$source-message:$key"
    private fun changed(context: Context) = context.sendBroadcast(Intent(NotificationInboxStore.ACTION_CHANGED).setPackage(context.packageName))
}

package com.healthsync.watch.ui.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.healthsync.watch.WatchApp
import com.healthsync.watch.data.CallPayload
import com.healthsync.watch.data.CallState
import com.healthsync.watch.ui.CallAlertActivity
import com.healthsync.watch.ui.ActiveCallActivity
import com.healthsync.watch.ui.InCallActivity

/** Calls have their own channel and full-screen eligibility; message alerts cannot replace them. */
object WatchCallAlerts {
    const val CHANNEL_ID = "watch_calls_v2"
    private const val ALERT_ID = 2101
    fun cancel(context: Context) { context.getSystemService(NotificationManager::class.java)?.cancel(ALERT_ID) }
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Incoming phone calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Caller display and incoming call alerts from your connected phone"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            })
    }
    fun receive(context: Context, call: CallPayload) = WatchCallBridge.main {
        val previous = WatchCallBridge.snapshot.call
        if (!WatchCallPolicy.accepts(previous, call)) return@main
        val routeToWatch = WatchCallPolicy.shouldRouteAnsweredCallToWatch(previous, WatchCallBridge.snapshot.pendingAction, call)
        WatchCallBridge.receive(context, call)
        if (routeToWatch) WatchCallBridge.send("AUDIO_BLUETOOTH")
        if (call.state == CallState.ENDED || call.state == CallState.MISSED) {
            cancel(context)
            context.sendBroadcast(Intent(InCallActivity.ACTION_CALL_ENDED).setPackage(context.packageName))
            return@main
        }
        val incoming = call.state == CallState.INCOMING
        if (!incoming) context.sendBroadcast(Intent(InCallActivity.ACTION_CALL_ANSWERED).setPackage(context.packageName))
        val newPresentation = previous?.callId != call.callId || previous?.state != call.state
        val target = Intent(context, if (incoming) CallAlertActivity::class.java else ActiveCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(CallAlertActivity.EXTRA_CALLER_NAME, WatchCallPolicy.callerLabel(call))
            .putExtra(CallAlertActivity.EXTRA_NUMBER, call.number)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@main
        createChannel(context)
        val pending = PendingIntent.getActivity(context, if (incoming) ALERT_ID else ALERT_ID + 1, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(if (incoming) "Incoming phone call" else "Phone call active").setContentText("Open HealthSync for call controls").build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(WatchCallPolicy.callerLabel(call)).setContentText(if (incoming) "Incoming phone call" else "Phone call active")
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
        if (!incoming) builder.setSilent(true)
        if (incoming && (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent())) builder.setFullScreenIntent(pending, true)
        val alertsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE)
        val dndAllows = Build.VERSION.SDK_INT < 23 || manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        if (alertsAllowed) runCatching { manager.notify(ALERT_ID, builder.build()) }
        if (newPresentation && alertsAllowed && (WatchApp.isForeground || (Build.VERSION.SDK_INT < 29 && dndAllows))) {
            runCatching { context.startActivity(target) }
        }
    }
}

package com.healthsync.phone.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.NavigationPayload
import com.healthsync.phone.data.model.NavigationProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

data class PhoneNavigationState(
    val enabled: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val listenerConnected: Boolean = false,
    val watchConnected: Boolean = false,
    val watchSupported: Boolean = false,
    val status: String = "DISABLED",
    val message: String = "Turn on watch navigation to share Google Maps directions."
) {
    val isReady: Boolean get() = enabled && hasNotificationAccess && listenerConnected && watchConnected && watchSupported
}

/** Uses the system-owned notification listener; no screen capture or separate foreground service. */
object PhoneNavigationBridge {
    private const val TAG = "PhoneNavigation"
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val streamId = UUID.randomUUID().toString()
    private var sequence = 0L
    private var applicationContext: Context? = null
    private var observingConnection = false
    private var replacingOrdinaryNotifications = false
    private var iconKey = ""
    private var iconPostTime = 0L
    private var iconBase64 = ""
    private val _state = MutableStateFlow(PhoneNavigationState())
    val state: StateFlow<PhoneNavigationState> = _state.asStateFlow()
    private val heartbeat = Runnable { applicationContext?.let { refreshOnMain(it) } }

    fun configure(context: Context, enabled: Boolean) {
        PhonePreferences(context).navigationEnabled = enabled
        refresh(context)
        // Also remove any old navigation entry from the regular watch inbox when opting in.
        HealthNotificationListenerService.resyncNotifications(context)
    }

    /** Safe from Bluetooth's worker thread, including when the listener is not available. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        handler.post { initialize(app); refreshOnMain(app) }
    }

    fun onListenerConnected(context: Context) = refresh(context)
    fun onListenerDisconnected(context: Context) = refresh(context)

    fun onNotificationPosted(context: Context, notification: StatusBarNotification) {
        if (notification.packageName != NavigationNotificationParser.MAPS_PACKAGE) return
        val app = context.applicationContext
        handler.post {
            initialize(app)
            // Some Maps versions reuse their notification's timestamp while changing the icon.
            if (iconKey == notification.key) iconKey = ""
            refreshOnMain(app, posted = notification)
        }
    }

    fun onNotificationRemoved(context: Context, notification: StatusBarNotification) {
        if (notification.packageName != NavigationNotificationParser.MAPS_PACKAGE) return
        val app = context.applicationContext
        handler.post { initialize(app); refreshOnMain(app, removedKey = notification.key) }
    }

    /** Navigation opt-in is independent of ordinary app filters and notification mirroring. */
    internal fun replacesOrdinaryNotification(context: Context, notification: StatusBarNotification): Boolean =
        BluetoothSyncService.connectionState.value.let { connection ->
            NavigationNotificationParser.shouldReplaceOrdinary(PhonePreferences(context).navigationEnabled,
                connection.isConnected, connection.supportsNavigation)
        } && runCatching { parse(notification) != null }.getOrDefault(false)

    private fun initialize(context: Context) {
        applicationContext = context
        if (!observingConnection) {
            observingConnection = true
            scope.launch {
                BluetoothSyncService.connectionState.map { it.isConnected to it.supportsNavigation }
                    .distinctUntilChanged().collect {
                        applicationContext?.let { app ->
                            val replace = NavigationNotificationParser.shouldReplaceOrdinary(
                                PhonePreferences(app).navigationEnabled, it.first, it.second)
                            if (replace != replacingOrdinaryNotifications) {
                                replacingOrdinaryNotifications = replace
                                HealthNotificationListenerService.resyncNotifications(app)
                            }
                            refreshOnMain(app)
                        }
                    }
            }
        }
    }

    private fun refreshOnMain(context: Context, posted: StatusBarNotification? = null, removedKey: String? = null) {
        handler.removeCallbacks(heartbeat)
        val enabled = PhonePreferences(context).navigationEnabled
        val access = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        val listener = HealthNotificationListenerService.instance
        val connection = BluetoothSyncService.connectionState.value
        var sourceUnavailable = enabled && (!access || listener == null)
        val active = if (enabled && !sourceUnavailable) runCatching {
            // Removal callbacks can precede the activeNotifications snapshot updating; exclude that
            // key explicitly. Likewise use the posted object immediately for a changed direction.
            val notifications = listener!!.activeNotifications.orEmpty().filter { it.key != removedKey && it.key != posted?.key } + listOfNotNull(posted)
            notifications.asSequence().filter { it.packageName == NavigationNotificationParser.MAPS_PACKAGE }
                .sortedByDescending { it.postTime }.mapNotNull { sbn -> parse(sbn)?.let { sbn to it } }.firstOrNull()
        }.getOrElse { sourceUnavailable = true; null } else null
        val status = when { !enabled -> "DISABLED"; sourceUnavailable -> "UNAVAILABLE"; active != null -> "ACTIVE"; else -> "IDLE" }
        val sourceMessage = when (status) {
            "DISABLED" -> "Turn on watch navigation in the phone app."
            "UNAVAILABLE" -> if (!access) "Allow notification access in the phone app." else "Phone notification access is reconnecting."
            "IDLE" -> "Start navigation in Google Maps on your phone."
            else -> ""
        }
        val payload = if (active == null) NavigationPayload(status = status, streamId = streamId, sequence = ++sequence, message = sourceMessage)
        else active.second.let { directions -> NavigationPayload(status = "ACTIVE", streamId = streamId, sequence = ++sequence,
            sessionId = directions.sessionId, instruction = directions.instruction, distance = directions.distance,
            road = directions.road, summary = directions.summary, maneuver = directions.maneuver,
            iconBase64 = realNavigationIcon(context, active.first)) }
        if (active == null) { iconKey = ""; iconBase64 = "" }
        val message = when {
            !enabled -> "Turn on watch navigation to share Google Maps directions."
            !access -> "Allow notification access so HealthSync can read Google Maps directions."
            sourceUnavailable -> "Notification access is reconnecting. Check phone notification access if this continues."
            !connection.isConnected -> "Connect your watch to receive Google Maps directions."
            !connection.supportsNavigation -> "Update HealthSync on your watch to use turn-by-turn navigation."
            status == "ACTIVE" -> "Sharing live Google Maps directions with your watch."
            else -> sourceMessage
        }
        _state.value = PhoneNavigationState(enabled, access, listener != null && !sourceUnavailable,
            connection.isConnected, connection.supportsNavigation, status, message)
        BluetoothSyncService.sendNavigationToWatch(payload)
        if (enabled && access && listener != null && connection.isConnected && connection.supportsNavigation)
            handler.postDelayed(heartbeat, NavigationProtocol.HEARTBEAT_MS)
    }

    private fun parse(sbn: StatusBarNotification): NavigationNotificationParser.Directions? {
        val notification = sbn.notification
        val extras = notification.extras
        return NavigationNotificationParser.parse(NavigationNotificationParser.Source(
            packageName = sbn.packageName, key = sbn.key, ongoing = sbn.isOngoing, category = notification.category,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            progress = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) || extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0,
            actionTitles = notification.actions.orEmpty().take(8).map { it.title?.toString().orEmpty() }))
    }

    private fun realNavigationIcon(context: Context, sbn: StatusBarNotification): String {
        if (iconKey == sbn.key && iconPostTime == sbn.postTime) return iconBase64
        iconKey = sbn.key
        iconPostTime = sbn.postTime
        iconBase64 = runCatching {
            // Only use the actual supplied large icon. The Maps application icon is not a maneuver.
            val drawable = sbn.notification.getLargeIcon()?.loadDrawable(context) ?: return@runCatching ""
            val side = NavigationProtocol.MAX_ICON_SIDE
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            try {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val scale = side.toFloat() / maxOf(width, height)
                val renderedWidth = (width * scale).toInt().coerceIn(1, side)
                val renderedHeight = (height * scale).toInt().coerceIn(1, side)
                val left = (side - renderedWidth) / 2
                val top = (side - renderedHeight) / 2
                drawable.setBounds(left, top, left + renderedWidth, top + renderedHeight)
                drawable.draw(Canvas(bitmap))
                val output = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) || output.size() > NavigationProtocol.MAX_ICON_BYTES) ""
                else Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            } finally { bitmap.recycle() }
        }.onFailure { Log.d(TAG, "Navigation icon unavailable") }.getOrDefault("")
        return iconBase64
    }
}

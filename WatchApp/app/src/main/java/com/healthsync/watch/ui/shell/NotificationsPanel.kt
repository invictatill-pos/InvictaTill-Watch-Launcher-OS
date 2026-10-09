package com.healthsync.watch.ui.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.healthsync.watch.notification.NotificationDisplayActivity
import com.healthsync.watch.notification.NotificationInboxEntry
import com.healthsync.watch.notification.NotificationInboxStore
import com.healthsync.watch.notification.LocalNotificationListenerService
import com.healthsync.watch.notification.canReplyToInboxEntry
import com.healthsync.watch.notification.notificationReplyStatus
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.WatchFaceActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.roundToInt

/** Notification shade inside Home. Contents remain available while the phone is disconnected. */
class NotificationsPanel(
    private val activity: AppCompatActivity,
    private val onClose: () -> Unit,
    private val onOpenSettings: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var registered = false
    private var resumed = false
    private var unreadOnly = false
    private var entries = emptyList<NotificationInboxEntry>()
    private val accent = 0xFF8CE7C8.toInt()
    private val muted = 0xFF9CAFA7.toInt()
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val diameter = minOf(activity.resources.displayMetrics.widthPixels, activity.resources.displayMetrics.heightPixels)
        val side = (diameter * .14f).roundToInt()
        val end = (diameter * .17f).roundToInt().coerceAtLeast(dp(40))
        setPadding(side, end, side, end)
    }
    private val scroll = RoundScrollView(activity).apply {
        setBackgroundColor(Color.BLACK)
        isFillViewport = true
        clipToPadding = false
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    val view: View = scroll
    private val status: TextView
    private val all: TextView
    private val unread: TextView
    private val cards = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val markRead: TextView
    private val clearAll: TextView
    private var clearing = false
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { reload() }
    }

    init {
        text(content, "Notifications", 23f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        status = text(content, "Loading your inbox…", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(16))
        }
        val filters = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        all = filter(filters, "All") { unreadOnly = false; render() }
        unread = filter(filters, "Unread") { unreadOnly = true; render() }
        content.addView(filters, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        clearAll = action(content, "Clear all") { clearAllNotifications() }.apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(12)
        }
        content.addView(cards, LinearLayout.LayoutParams(-1, -2))
        markRead = action(content, "Mark all read") {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { NotificationInboxStore.getInstance(activity).markAllRead() }
                }
                if (result.isSuccess) broadcastChange() else toast("Could not mark notifications read")
            }
        }
        action(content, "Phone & notification settings", onOpenSettings)
        action(content, "Back to clock", onClose).apply {
            background = shape(Color.TRANSPARENT)
            setTextColor(muted)
        }
        text(content, "Clear all removes phone copies and closes clearable watch alerts. Ongoing watch alerts stay.", 10f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        updateFilters()
    }

    fun onResume() {
        if (resumed) return
        resumed = true
        if (!registered) {
            runCatching {
                ContextCompat.registerReceiver(activity, changes, IntentFilter().apply {
                    addAction(NotificationInboxStore.ACTION_CHANGED)
                    addAction(WatchFaceActivity.ACTION_BT_STATUS)
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
                registered = true
            }
        }
        reload()
    }

    fun onPause() {
        resumed = false
        if (registered) { runCatching { activity.unregisterReceiver(changes) }; registered = false }
        loadJob?.cancel()
    }

    fun destroy() { onPause(); scope.cancel() }

    private fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { NotificationInboxStore.getInstance(activity).read() }
            }
            result.onSuccess { entries = it; render() }.onFailure {
                cards.removeAllViews()
                emptyCard("Inbox unavailable", "Tap to try loading again") { reload() }
                status.text = connectionLabel()
            }
        }
    }

    private fun render() {
        updateFilters()
        val visible = entries.filter { !unreadOnly || !it.isRead }
        status.text = "${entries.count { !it.isRead }} unread · ${connectionLabel()}"
        cards.removeAllViews()
        markRead.visibility = if (entries.any { !it.isRead }) View.VISIBLE else View.GONE
        clearAll.visibility = if (entries.isNotEmpty()) View.VISIBLE else View.GONE
        clearAll.isEnabled = !clearing
        clearAll.text = if (clearing) "Clearing…" else "Clear all"
        if (visible.isEmpty()) {
            emptyCard(if (unreadOnly) "You're all caught up" else "No notifications yet",
                if (BluetoothClientService.isConnected || LocalNotificationListenerService.isConnected()) "Phone and watch alerts appear here"
                else "Connect your phone or enable watch notification access", onOpenSettings)
        } else visible.forEach { addCard(it) }
    }

    private fun addCard(entry: NotificationInboxEntry) {
        val payload = entry.payload
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(14))
            background = shape(if (entry.isRead) 0xFF151C19.toInt() else 0xFF1A2922.toInt())
            isClickable = true
            isFocusable = true
            contentDescription = "${sourceLabel(entry)} notification. ${payload.appLabel}, ${payload.title}, ${payload.text}. ${if (entry.isRead) "Read" else "Unread"}. Tap to open"
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                runCatching {
                    activity.startActivity(Intent(activity, NotificationDisplayActivity::class.java)
                        .putExtra(NotificationDisplayActivity.EXTRA_INBOX_ID, entry.id)
                        .putExtra(NotificationDisplayActivity.EXTRA_FROM_INBOX, true))
                }.onFailure { toast("Notification could not open") }
            }
        }
        val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        text(top, "${if (entry.isRead) "" else "● "}${payload.appLabel.ifBlank { "App" }}", 11f, accent, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val eventTime = if (entry.source == NotificationInboxStore.SOURCE_WATCH) payload.time else entry.receivedAt
        text(top, "${sourceLabel(entry)} · ${relativeTime(eventTime)}", 10f, muted).apply { setPadding(dp(5), 0, 0, 0) }
        val dismiss = TextView(activity).apply {
            text = "×"; textSize = 23f; gravity = Gravity.CENTER; setTextColor(muted)
            background = shape(0xFF28352E.toInt())
            contentDescription = "Dismiss ${sourceLabel(entry)} ${payload.appLabel} notification"
            isFocusable = true
            setOnClickListener { dismiss(entry) }
        }
        top.addView(dismiss, LinearLayout.LayoutParams(dp(40), dp(40)).apply { leftMargin = dp(8) })
        card.addView(top)
        text(card, payload.title.ifBlank { "Notification" }, 17f, Color.WHITE, !entry.isRead).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, dp(5))
        }
        if (payload.text.isNotBlank()) text(card, payload.text, 13f, 0xFFD5E2DB.toInt()).apply {
            maxLines = 3; ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        val replyStatus = notificationReplyStatus(entry)
        if (replyStatus.isNotBlank()) text(card, replyStatus, 10f, muted).apply { setPadding(0, dp(7), 0, 0) }
        else if (canReplyToInboxEntry(entry)) text(card, "Tap to read & reply", 10f, accent).apply { setPadding(0, dp(7), 0, 0) }
        cards.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun dismiss(entry: NotificationInboxEntry) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val store = NotificationInboxStore.getInstance(activity)
                    val current = store.get(entry.id)
                    if (current == null || current.payload != entry.payload || current.source != entry.source) return@runCatching false
                    if (entry.source == NotificationInboxStore.SOURCE_WATCH) LocalNotificationListenerService.dismiss(entry)
                    store.delete(entry)
                }
            }
            if (result.getOrDefault(false)) {
                entries = entries.filterNot { it.id == entry.id }
                render()
                broadcastChange()
            } else { toast("Notification changed. Refreshing inbox"); reload() }
        }
    }

    private fun clearAllNotifications() {
        if (clearing) return
        clearing = true
        render()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LocalNotificationListenerService.clearAll(activity.applicationContext) }
            }
            clearing = false
            result.onSuccess {
                if (it.keptActiveWatch > 0) toast("${it.keptActiveWatch} ongoing watch alert${if (it.keptActiveWatch == 1) " stays" else "s stay"}")
                broadcastChange()
            }.onFailure { toast("Could not clear notifications"); reload() }
        }
    }

    private fun emptyCard(title: String, detail: String, click: () -> Unit) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
            background = shape(0xFF151C19.toInt())
            setOnClickListener { click() }
            isFocusable = true
        }
        text(card, "○", 35f, accent).gravity = Gravity.CENTER
        text(card, title, 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(6)) }
        text(card, detail, 12f, muted).gravity = Gravity.CENTER
        cards.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun updateFilters() {
        all.background = shape(if (!unreadOnly) accent else 0xFF18221D.toInt())
        all.setTextColor(if (!unreadOnly) Color.BLACK else muted)
        unread.background = shape(if (unreadOnly) accent else 0xFF18221D.toInt())
        unread.setTextColor(if (unreadOnly) Color.BLACK else muted)
        unread.text = "Unread${entries.count { !it.isRead }.takeIf { it > 0 }?.let { " $it" } ?: ""}"
        all.isSelected = !unreadOnly; unread.isSelected = unreadOnly
    }

    private fun filter(parent: LinearLayout, title: String, click: () -> Unit) = TextView(activity).apply {
        text = title; textSize = 12f; gravity = Gravity.CENTER; minHeight = dp(44)
        setTypeface(null, Typeface.BOLD); isFocusable = true
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); click() }
        parent.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
    }

    private fun action(parent: LinearLayout, title: String, click: () -> Unit) = TextView(activity).apply {
        text = title; textSize = 13f; setTextColor(accent); gravity = Gravity.CENTER
        minHeight = dp(48); setPadding(dp(9), dp(12), dp(9), dp(12))
        background = shape(0xFF18221D.toInt()); isFocusable = true
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); click() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun text(parent: LinearLayout, title: String, size: Float, color: Int, bold: Boolean = false) = TextView(activity).apply {
        text = title; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) setTypeface(null, Typeface.BOLD)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    private fun sourceLabel(entry: NotificationInboxEntry) = if (entry.source == NotificationInboxStore.SOURCE_WATCH) "Watch" else "Phone"
    private fun connectionLabel() = (if (BluetoothClientService.isConnected) "Phone connected" else "Phone offline") +
        if (LocalNotificationListenerService.isConnected()) " · Watch alerts on" else " · Watch alerts off"
    private fun relativeTime(time: Long): String {
        val elapsed = (System.currentTimeMillis() - time).coerceAtLeast(0L)
        return when {
            elapsed < 60_000L -> "Now"
            elapsed < 3_600_000L -> "${elapsed / 60_000L}m"
            elapsed < 86_400_000L -> DateFormat.getTimeFormat(activity).format(Date(time))
            else -> DateFormat.getDateFormat(activity).format(Date(time))
        }
    }
    private fun broadcastChange() {
        activity.sendBroadcast(Intent(NotificationInboxStore.ACTION_CHANGED).setPackage(activity.packageName))
        if (resumed) reload()
    }
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat() }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
}

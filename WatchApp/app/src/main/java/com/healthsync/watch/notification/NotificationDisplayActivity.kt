package com.healthsync.watch.notification

import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.healthsync.watch.R
import com.healthsync.watch.data.MessageType
import com.healthsync.watch.data.NotificationPayload
import com.healthsync.watch.data.ReplyMessagePayload
import com.healthsync.watch.data.SyncMessage
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import com.healthsync.watch.ui.WatchFaceActivity
import com.healthsync.watch.ui.calls.WatchCallBridge
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class NotificationDisplayActivity : LauncherPanelActivity() {
    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_LABEL = "app_label"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TIME = "time"
        const val EXTRA_CAN_REPLY = "can_reply"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"
        const val EXTRA_CONVERSATION_TITLE = "conversation_title"
        const val EXTRA_INBOX_ID = "inbox_id"
        const val EXTRA_FROM_INBOX = "from_inbox"
        const val EXTRA_SOURCE = "notification_source"
        const val EXTRA_WAKE_SCREEN = "wake_screen"
        private val requestSequence = AtomicLong(System.currentTimeMillis())

        fun intentFor(context: Context, p: NotificationPayload, inboxId: Long = -1L, fromInbox: Boolean = false, source: String = "phone") =
            Intent(context, NotificationDisplayActivity::class.java)
                .putExtra(EXTRA_PACKAGE_NAME, p.packageName).putExtra(EXTRA_APP_LABEL, p.appLabel)
                .putExtra(EXTRA_TITLE, p.title).putExtra(EXTRA_TEXT, p.text).putExtra(EXTRA_TIME, p.time)
                .putExtra(EXTRA_CAN_REPLY, p.canReply).putExtra(EXTRA_NOTIFICATION_KEY, p.notificationKey)
                .putExtra(EXTRA_CONVERSATION_TITLE, p.conversationTitle)
                .putExtra(EXTRA_INBOX_ID, inboxId).putExtra(EXTRA_FROM_INBOX, fromInbox)
                .putExtra(EXTRA_SOURCE, source)
    }
    private val handler = Handler(Looper.getMainLooper())
    private var notificationPackage = ""
    private var notificationTitle = ""
    private var notificationKey = ""
    private var replyRequested = false
    private var canReply = false
    private var fromInbox = false
    private var source = NotificationInboxStore.SOURCE_PHONE
    private var displayedEntry: NotificationInboxEntry? = null
    private var displayJob: Job? = null
    private var displayRevision = 0L
    private lateinit var replyContainer: View
    private lateinit var backToInbox: Button
    private lateinit var nativeActions: LinearLayout
    private var receiverRegistered = false
    private val close = Runnable { finish() }
    private val replyTimeout = Runnable { refreshReplyState() }
    private val inboxChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (WatchCallBridge.isCallActive() && !fromInbox) { finish(); return }
            refreshReplyState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWake(intent)
        setContentView(R.layout.activity_notification_display)
        val diameter = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        (findViewById<com.healthsync.watch.ui.RoundScrollView>(R.id.notificationScroll).getChildAt(0) as? android.view.ViewGroup)
            ?.setPadding((diameter * 0.14f).toInt(), (diameter * 0.16f).toInt().coerceAtLeast(dp(36)),
                (diameter * 0.14f).toInt(), (diameter * 0.16f).toInt().coerceAtLeast(dp(36)))
        replyContainer = findViewById(R.id.replyContainer)
        val input = findViewById<EditText>(R.id.etReplyInput)
        input.filters = arrayOf(InputFilter.LengthFilter(500))
        findViewById<Button>(R.id.btnReply).setOnClickListener {
            if (source == NotificationInboxStore.SOURCE_WATCH) {
                val entry = displayedEntry
                val opened = entry != null && if (LocalNotificationListenerService.canOpen(entry))
                    LocalNotificationListenerService.open(entry) else LocalNotificationListenerService.openApp(this, entry)
                if (opened) finish()
                else {
                    Toast.makeText(this, "The source action is no longer available", Toast.LENGTH_SHORT).show()
                    updateWatchActions()
                }
                return@setOnClickListener
            }
            handler.removeCallbacksAndMessages(null)
            replyContainer.visibility = if (replyContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (replyContainer.visibility == View.GONE) scheduleClose()
        }
        listOf(R.id.chipOk to "OK", R.id.chipYes to "Yes", R.id.chipNo to "No",
            R.id.chipOnMyWay to "On my way!", R.id.chipCallLater to "Call you later", R.id.chipThanks to "Thanks!")
            .forEach { (id, text) -> findViewById<Button>(id).setOnClickListener { requestReply(text) } }
        findViewById<Button>(R.id.btnSendCustomReply).setOnClickListener { requestReply(input.text.toString()) }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) {
                requestReply(input.text.toString())
                true
            } else false
        }
        findViewById<View>(R.id.btnDismiss).setOnClickListener { dismissDisplayed() }
        nativeActions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val detailContent = findViewById<com.healthsync.watch.ui.RoundScrollView>(R.id.notificationScroll).getChildAt(0) as LinearLayout
        detailContent.addView(nativeActions, detailContent.indexOfChild(findViewById<View>(R.id.btnDismiss)), LinearLayout.LayoutParams(-1, -2))
        backToInbox = Button(this).apply {
            text = "Back to notifications"; isAllCaps = false; textSize = 12f
            setTextColor(muted); backgroundTintList = null; background = buttonBackground()
            minHeight = dp(48); setOnClickListener { finish() }
        }
        ((findViewById<com.healthsync.watch.ui.RoundScrollView>(R.id.notificationScroll).getChildAt(0)) as LinearLayout)
            .addView(backToInbox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        display(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureWake(intent)
        display(intent)
    }

    private fun configureWake(incoming: Intent) {
        if (Build.VERSION.SDK_INT >= 27) setTurnScreenOn(incoming.getBooleanExtra(EXTRA_WAKE_SCREEN, false))
        else {
            @Suppress("DEPRECATION")
            val flag = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (incoming.getBooleanExtra(EXTRA_WAKE_SCREEN, false)) window.addFlags(flag) else window.clearFlags(flag)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, inboxChanges, IntentFilter().apply {
            addAction(NotificationInboxStore.ACTION_CHANGED); addAction(WatchFaceActivity.ACTION_BT_STATUS)
            addAction(WatchCallBridge.ACTION_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        if (WatchCallBridge.isCallActive() && !fromInbox) { finish(); return }
        refreshReplyState()
    }

    override fun onStop() {
        if (receiverRegistered) { unregisterReceiver(inboxChanges); receiverRegistered = false }
        super.onStop()
    }

    private fun display(incoming: Intent) {
        displayRevision++
        displayJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        fromInbox = incoming.getBooleanExtra(EXTRA_FROM_INBOX, false)
        val id = incoming.getLongExtra(EXTRA_INBOX_ID, -1L)
        if (id < 0L) { displayedEntry = null; render(incoming); return }
        displayJob = lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    NotificationInboxStore.getInstance(applicationContext).open(id)
                }.getOrNull()
            }
            if (saved == null && fromInbox) {
                Toast.makeText(this@NotificationDisplayActivity, "Notification is no longer saved", Toast.LENGTH_SHORT).show()
                finish(); return@launch
            }
            displayedEntry = saved
            if (saved != null) {
                sendBroadcast(Intent(NotificationInboxStore.ACTION_CHANGED).setPackage(packageName))
                render(intentFor(this@NotificationDisplayActivity, saved.payload, saved.id, fromInbox, saved.source))
            } else render(incoming)
        }
    }

    private fun render(incoming: Intent) {
        handler.removeCallbacksAndMessages(null)
        notificationPackage = incoming.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        notificationTitle = incoming.getStringExtra(EXTRA_TITLE) ?: ""
        notificationKey = incoming.getStringExtra(EXTRA_NOTIFICATION_KEY) ?: ""
        source = displayedEntry?.source ?: incoming.getStringExtra(EXTRA_SOURCE) ?: NotificationInboxStore.SOURCE_PHONE
        replyRequested = displayedEntry?.replyRequested == true
        canReply = source == NotificationInboxStore.SOURCE_PHONE && incoming.getBooleanExtra(EXTRA_CAN_REPLY, false) && notificationPackage.isNotBlank() && notificationKey.isNotBlank()
        if (displayedEntry != null && !canReplyToInboxEntry(displayedEntry!!)) canReply = false
        val label = incoming.getStringExtra(EXTRA_APP_LABEL)?.ifBlank { "App" } ?: "App"
        findViewById<TextView>(R.id.tvAppLabel).text = "$label · ${if (source == NotificationInboxStore.SOURCE_WATCH) "Watch" else "Phone"}"
        findViewById<TextView>(R.id.tvNotifTitle).text = notificationTitle
        findViewById<TextView>(R.id.tvConversationTitle).apply {
            text = incoming.getStringExtra(EXTRA_CONVERSATION_TITLE).orEmpty()
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.tvNotifText).text = incoming.getStringExtra(EXTRA_TEXT) ?: ""
        findViewById<TextView>(R.id.tvNotifTime).text = DateFormat.getTimeFormat(this)
            .format(Date(incoming.getLongExtra(EXTRA_TIME, System.currentTimeMillis())))
        findViewById<TextView>(R.id.tvAppInitial).text = label.take(1).uppercase(Locale.getDefault())
        findViewById<Button>(R.id.btnReply).apply {
            if (source == NotificationInboxStore.SOURCE_WATCH) {
                visibility = View.VISIBLE
            } else {
                visibility = if (canReply || replyRequested) View.VISIBLE else View.GONE
                isEnabled = canReply && !replyRequested
                text = replyButtonLabel(displayedEntry)
            }
        }
        updateWatchActions()
        findViewById<Button>(R.id.btnDismiss).text = when {
            source == NotificationInboxStore.SOURCE_WATCH && displayedEntry?.let(LocalNotificationListenerService::canDismiss) == true -> "DISMISS WATCH ALERT"
            source == NotificationInboxStore.SOURCE_WATCH -> "REMOVE SAVED COPY"
            fromInbox -> "REMOVE WATCH COPY"
            else -> "CLOSE ALERT"
        }
        backToInbox.visibility = if (fromInbox) View.VISIBLE else View.GONE
        replyContainer.visibility = View.GONE
        findViewById<EditText>(R.id.etReplyInput).text.clear()
        (findViewById<View>(android.R.id.content) as? android.view.ViewGroup)?.let { content ->
            content.findViewById<com.healthsync.watch.ui.RoundScrollView>(R.id.notificationScroll)?.scrollTo(0, 0)
        }
        // Delivery owns vibration; rendering, reconnects and inbox opens must remain silent.
        updateReplyStatus()
        scheduleClose()
    }

    private fun updateWatchActions() {
        nativeActions.removeAllViews()
        if (source != NotificationInboxStore.SOURCE_WATCH) return
        val entry = displayedEntry
        val canOpen = entry?.let(LocalNotificationListenerService::canOpen) == true
        val canOpenApp = entry?.let { LocalNotificationListenerService.canOpenApp(this, it) } == true
        findViewById<Button>(R.id.btnReply).apply {
            isEnabled = canOpen || canOpenApp
            text = when { canOpen -> "OPEN NOTIFICATION"; canOpenApp -> "OPEN APP"; else -> "OPEN ACTION UNAVAILABLE" }
        }
        if (entry == null) return
        LocalNotificationListenerService.actions(entry).forEach { nativeAction ->
            action(nativeActions, nativeAction.title) {
                if (LocalNotificationListenerService.performAction(entry, nativeAction.index)) finish()
                else {
                    Toast.makeText(this, "Action unavailable. Unlock the watch or open the source app.", Toast.LENGTH_LONG).show()
                    updateWatchActions()
                }
            }
        }
    }

    private fun requestReply(text: String) {
        if (source != NotificationInboxStore.SOURCE_PHONE || replyRequested || !canReply) return
        val reply = text.trim().take(500)
        if (reply.isBlank()) {
            Toast.makeText(this, "Enter a reply first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!BluetoothClientService.isConnected) {
            Toast.makeText(this, "Phone disconnected. Reconnect to reply.", Toast.LENGTH_LONG).show()
            return
        }
        replyRequested = true
        findViewById<Button>(R.id.btnReply).isEnabled = false
        val displayed = displayedEntry
        val targetPackage = notificationPackage
        val targetTitle = notificationTitle
        val targetKey = notificationKey
        val revision = displayRevision
        val requestId = requestSequence.incrementAndGet()
        handler.removeCallbacks(close)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val store = NotificationInboxStore.getInstance(applicationContext)
                    if (displayed != null) {
                        val current = store.get(displayed.id)
                        if (!isCurrentInboxReplyTarget(displayed, current)) return@runCatching "updated"
                        if (!store.beginReply(displayed, requestId, System.currentTimeMillis())) return@runCatching "updated"
                    } else return@runCatching "updated"
                    val gson = Gson()
                    val payload = ReplyMessagePayload(packageName = targetPackage, replyText = reply,
                        title = targetTitle, notificationKey = targetKey, requestId = requestId)
                    if (!BluetoothClientService.sendRawMsg(SyncMessage(MessageType.REPLY_MESSAGE, payload = gson.toJson(payload)))) {
                        store.completeReply(requestId, targetKey, false, "Phone disconnected. Reconnect to reply.")
                        return@runCatching "disconnected"
                    }
                    "pending"
                }.getOrDefault("unavailable")
            }
            if (displayRevision != revision) return@launch
            if (result != "pending") {
                replyRequested = false
                if (result == "updated") canReply = false
                findViewById<Button>(R.id.btnReply).isEnabled = canReply
                Toast.makeText(this@NotificationDisplayActivity, if (result == "updated") "Notification changed. Reopen the inbox to reply."
                    else "Reply could not be requested. Reconnect and try again.", Toast.LENGTH_LONG).show()
                return@launch
            }
            replyContainer.visibility = View.GONE
            findViewById<Button>(R.id.btnReply).apply { this.text = "SENDING…"; isEnabled = false }
            Toast.makeText(this@NotificationDisplayActivity, "Sending reply…", Toast.LENGTH_SHORT).show()
            sendBroadcast(Intent(NotificationInboxStore.ACTION_CHANGED).setPackage(packageName))
            handler.removeCallbacks(replyTimeout)
            handler.postDelayed(replyTimeout, REPLY_TIMEOUT_MS + 250L)
            refreshReplyState()
        }
    }

    private fun replyButtonLabel(entry: NotificationInboxEntry?): String = when (entry?.replyState) {
        "pending" -> "SENDING…"
        "sent" -> "REPLY SENT"
        "unknown" -> "CHECK PHONE"
        "failed" -> "RETRY REPLY"
        else -> if (entry?.replyRequested == true) "REPLY REQUESTED" else "REPLY"
    }

    private fun updateReplyStatus() {
        val entry = displayedEntry ?: return
        val status = notificationReplyStatus(entry)
        findViewById<TextView>(R.id.tvNotifTime).text = DateFormat.getTimeFormat(this).format(Date(entry.payload.time)) +
            if (status.isNotBlank()) " · $status" else if (!entry.payload.isActive && source == NotificationInboxStore.SOURCE_PHONE) " · History · reply unavailable" else ""
        handler.removeCallbacks(replyTimeout)
        if (entry.replyState == "pending") handler.postDelayed(replyTimeout,
            (entry.replyRequestedAt + REPLY_TIMEOUT_MS - System.currentTimeMillis()).coerceAtLeast(0L) + 250L)
    }

    private fun refreshReplyState() {
        val displayed = displayedEntry ?: return
        val revision = displayRevision
        lifecycleScope.launch {
            val current = withContext(Dispatchers.IO) { runCatching {
                NotificationInboxStore.getInstance(applicationContext).get(displayed.id)
            }.getOrNull() }
            if (revision != displayRevision) return@launch
            if (current == null) { canReply = false; findViewById<Button>(R.id.btnReply).isEnabled = false; return@launch }
            if (!sameNotificationContent(displayed.payload, current.payload) || displayed.payload.time != current.payload.time) {
                canReply = false
                findViewById<Button>(R.id.btnReply).apply { isEnabled = false; text = "NOTIFICATION UPDATED" }
                replyContainer.visibility = View.GONE
                return@launch
            }
            val previousState = displayedEntry?.replyState
            displayedEntry = current
            replyRequested = current.replyRequested
            canReply = canReplyToInboxEntry(current) && BluetoothClientService.isConnected
            if (source == NotificationInboxStore.SOURCE_WATCH) { updateWatchActions(); return@launch }
            findViewById<Button>(R.id.btnReply).apply {
                isEnabled = canReply; text = replyButtonLabel(current)
                visibility = if (current.payload.canReply || current.replyRequested || current.replyState.isNotBlank()) View.VISIBLE else View.GONE
            }
            if (!canReply) replyContainer.visibility = View.GONE
            updateReplyStatus()
            if (current.replyState == "sent" && previousState != "sent") {
                Toast.makeText(this@NotificationDisplayActivity, "Reply sent to app", Toast.LENGTH_SHORT).show()
                if (!fromInbox) handler.postDelayed(close, 2_000L)
            } else scheduleClose()
        }
    }

    private fun dismissDisplayed() {
        val displayed = displayedEntry
        if (displayed == null || (!fromInbox && source == NotificationInboxStore.SOURCE_PHONE)) { finish(); return }
        val revision = displayRevision
        lifecycleScope.launch {
            val removed = withContext(Dispatchers.IO) {
                runCatching {
                    val store = NotificationInboxStore.getInstance(applicationContext)
                    val current = store.get(displayed.id)
                    if (current == null || current.payload != displayed.payload || current.source != displayed.source) return@runCatching false
                    if (displayed.source == NotificationInboxStore.SOURCE_WATCH) LocalNotificationListenerService.dismiss(displayed)
                    store.delete(displayed)
                }.getOrDefault(false)
            }
            if (revision != displayRevision) return@launch
            if (removed) {
                sendBroadcast(Intent(NotificationInboxStore.ACTION_CHANGED).setPackage(packageName))
                finish()
            } else {
                Toast.makeText(this@NotificationDisplayActivity, "Notification changed. Reopen the inbox.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun scheduleClose() {
        handler.removeCallbacks(close)
        if (!fromInbox && replyContainer.visibility != View.VISIBLE && displayedEntry?.replyState != "pending") handler.postDelayed(close, 20_000L)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && !replyRequested) scheduleClose()
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}


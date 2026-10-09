package com.healthsync.watch.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.ui.WatchFaceActivity
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.roundToInt

/** A bounded local inbox survives closed alerts, restarts, and Bluetooth disconnects. */
class NotificationsInboxActivity : LauncherPanelActivity() {
    private lateinit var recycler: RecyclerView
    private val inboxAdapter = InboxAdapter()
    private var allEntries = emptyList<NotificationInboxEntry>()
    private var visibleEntries = emptyList<NotificationInboxEntry>()
    private var query = ""
    private var unreadOnly = false
    private var loading = true
    private var loadError: String? = null
    private var loadJob: Job? = null
    private var receiverRegistered = false
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { reload() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        query = savedInstanceState?.getString("inbox_search") ?: ""
        unreadOnly = savedInstanceState?.getBoolean("inbox_unread") ?: false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        recycler = RecyclerView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutManager = LinearLayoutManager(this@NotificationsInboxActivity)
            adapter = inboxAdapter
            clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER; itemAnimator = null
            val diameter = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            val side = (diameter * 0.14f).roundToInt()
            val end = (diameter * 0.16f).roundToInt().coerceAtLeast(dp(36))
            setPadding(side, end, side, end)
            isFocusable = true; isFocusableInTouchMode = true
            setOnGenericMotionListener { _, event ->
                if (Build.VERSION.SDK_INT >= 26 && event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    scrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(48)).roundToInt()); true
                } else false
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { scaleRows() }
            })
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scaleRows() }
        }
        setContentView(recycler)
        recycler.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            ContextCompat.registerReceiver(this, changes, IntentFilter().apply {
                addAction(NotificationInboxStore.ACTION_CHANGED); addAction(WatchFaceActivity.ACTION_BT_STATUS)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
    }
    override fun onResume() { super.onResume(); reload() }
    override fun onStop() {
        if (receiverRegistered) { runCatching { unregisterReceiver(changes) }; receiverRegistered = false }
        loadJob?.cancel()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("inbox_search", query); outState.putBoolean("inbox_unread", unreadOnly)
        super.onSaveInstanceState(outState)
    }

    private fun reload() {
        loadJob?.cancel(); loading = true; loadError = null
        inboxAdapter.refreshStatus()
        loadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { NotificationInboxStore.getInstance(applicationContext).read() } }
            loading = false
            result.onSuccess { allEntries = it }.onFailure { loadError = "Inbox could not load. Tap Refresh to retry." }
            applyFilter()
        }
    }
    private fun applyFilter() {
        val previous = visibleEntries.size.coerceAtLeast(1)
        val search = query.trim()
        visibleEntries = allEntries.filter { entry ->
            (!unreadOnly || !entry.isRead) && (search.isBlank() || listOf(entry.payload.appLabel, entry.payload.title,
                entry.payload.text, entry.payload.packageName, entry.payload.conversationTitle).any { it.contains(search, ignoreCase = true) })
        }
        // Preserve search focus while replacing only rows below the header.
        inboxAdapter.notifyItemRangeRemoved(1, previous)
        inboxAdapter.notifyItemRangeInserted(1, visibleEntries.size.coerceAtLeast(1))
        inboxAdapter.refreshStatus()
        recycler.post { scaleRows() }
    }
    private fun open(entry: NotificationInboxEntry) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(recycler.windowToken, 0)
        launchFirst(listOf(Intent(this, NotificationDisplayActivity::class.java)
            .putExtra(NotificationDisplayActivity.EXTRA_INBOX_ID, entry.id)
            .putExtra(NotificationDisplayActivity.EXTRA_FROM_INBOX, true)), "Notification could not open.")
    }
    private fun clearInbox() {
        AlertDialog.Builder(this).setTitle("Clear all notifications?")
            .setMessage("Removes phone copies and closes clearable watch alerts. Ongoing watch alerts stay.")
            .setNegativeButton("Cancel", null).setPositiveButton("Clear all") { _, _ ->
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { LocalNotificationListenerService.clearAll(applicationContext) } }
                    if (result.isFailure) { message("Inbox could not be cleared. Try again."); return@launch }
                    result.getOrNull()?.keptActiveWatch?.takeIf { it > 0 }?.let {
                        message("$it ongoing watch alert${if (it == 1) " stays" else "s stay"}.")
                    }
                    sendBroadcast(Intent(NotificationInboxStore.ACTION_CHANGED).setPackage(packageName)); reload()
                }
            }.show()
    }
    private fun scaleRows() {
        if (recycler.height <= 0) return
        for (index in 0 until recycler.childCount) {
            val child = recycler.getChildAt(index)
            val position = recycler.getChildAdapterPosition(child)
            val scale = if (position in 1..visibleEntries.size)
                1f - minOf(kotlin.math.abs(0.5f - (child.y + child.height / 2f) / recycler.height), 0.5f) * 0.14f else 1f
            child.scaleX = scale; child.scaleY = scale; child.alpha = scale
        }
    }
    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private inner class HeaderHolder(val content: LinearLayout) : RecyclerView.ViewHolder(content) {
        val status: TextView
        val filter: Button
        init {
            heading(content, "Notifications", "Saved on this watch")
            val search = EditText(this@NotificationsInboxActivity).apply {
                hint = "Search notifications"; contentDescription = "Search notifications by app, title, or message"
                textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(muted); isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                minHeight = dp(48); setPadding(dp(12), dp(10), dp(12), dp(10)); background = shape(panelColor)
                isSaveEnabled = false; setText(query); setSelection(text.length)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString() ?: ""; applyFilter() }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
                        clearFocus(); recycler.requestFocus(); true
                    } else false
                }
            }
            content.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            filter = action(content, "Show unread") { unreadOnly = !unreadOnly; applyFilter() }
            status = label(content, "Loading inbox…", 11f)
        }
    }
    private inner class EntryHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val app = rowText(10f, accent, 1)
        val title = rowText(14f, Color.WHITE, 2)
        val preview = rowText(12f, 0xFFB7CCC0.toInt(), 2)
        val status = rowText(10f, muted, 2)
        init {
            row.setPadding(dp(14), dp(12), dp(14), dp(12)); row.background = buttonBackground()
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        private fun rowText(size: Float, color: Int, lines: Int) = TextView(this@NotificationsInboxActivity).apply {
            textSize = size; setTextColor(color); maxLines = lines; ellipsize = android.text.TextUtils.TruncateAt.END; row.addView(this)
        }
        fun bind(entry: NotificationInboxEntry) {
            val p = entry.payload
            app.text = "${if (!entry.isRead) "● " else ""}${p.appLabel.ifBlank { "App" }} · ${if (entry.source == NotificationInboxStore.SOURCE_WATCH) "Watch" else "Phone"}"
            title.text = p.title.ifBlank { "Notification" }; title.setTypeface(null, if (entry.isRead) Typeface.NORMAL else Typeface.BOLD)
            preview.text = p.text
            status.text = DateFormat.getDateFormat(this@NotificationsInboxActivity).format(Date(p.time)) + " · " +
                DateFormat.getTimeFormat(this@NotificationsInboxActivity).format(Date(p.time)) +
                notificationReplyStatus(entry).let { if (it.isNotBlank()) " · $it" else if (!p.isActive) " · History" else "" }
            row.contentDescription = "${p.appLabel}, ${p.title}, ${p.text}. ${if (!entry.isRead) "Unread. " else ""}Tap to open"
            row.setOnClickListener { open(entry) }
        }
    }
    private inner class FooterHolder(content: LinearLayout) : RecyclerView.ViewHolder(content) {
        val clear: Button
        init {
            action(content, "Refresh") { reload() }
            clear = action(content, "Clear all") { clearInbox() }
            action(content, "Back to clock") { backToClock() }
        }
    }
    private inner class InboxAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var header: HeaderHolder? = null
        private var footer: FooterHolder? = null
        override fun getItemCount() = visibleEntries.size.coerceAtLeast(1) + 2
        override fun getItemViewType(position: Int) = when {
            position == 0 -> 0
            position == itemCount - 1 -> 3
            visibleEntries.isEmpty() -> 2
            else -> 1
        }
        fun refreshStatus() {
            header?.status?.text = when {
                loading -> "Loading inbox…"
                loadError != null -> "Unable to refresh inbox"
                else -> "${visibleEntries.size} shown · ${allEntries.count { !it.isRead }} unread · ${if (BluetoothClientService.isConnected) "Connected" else "Offline"}"
            }
            header?.filter?.text = if (unreadOnly) "Show all notifications" else "Show unread"
            footer?.clear?.isEnabled = allEntries.isNotEmpty()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
            0 -> HeaderHolder(column()).also { header = it }
            1 -> EntryHolder(column())
            2 -> object : RecyclerView.ViewHolder(column().apply { label(this, "", 13f) }) {}
            else -> FooterHolder(column()).also { footer = it }
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is HeaderHolder, is FooterHolder -> refreshStatus()
                is EntryHolder -> holder.bind(visibleEntries[position - 1])
                else -> ((holder.itemView as LinearLayout).getChildAt(0) as TextView).text = when {
                    loading -> "Loading saved notifications…"
                    loadError != null -> loadError
                    query.isNotBlank() || unreadOnly -> "No matching notifications. Change the search or show all."
                    else -> "Notifications from your paired phone appear here. Keeps the newest 100 for up to 7 days."
                }
            }
        }
    }
}

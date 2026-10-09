package com.healthsync.watch.ui.shell

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.healthsync.watch.ui.RoundScrollView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

/** Installed activity components are retained: aliases and multiple app entry points stay usable. */
class AppGridPanel(
    private val activity: AppCompatActivity,
    private val onClose: () -> Unit,
    private val onUtilities: () -> Unit,
    private val onSettings: () -> Unit
) {
    /** Core watch apps navigate inside Home rather than opening duplicate launcher activities. */
    var onNavigate: (String) -> Unit = {}
    private data class Entry(val key: String, val label: String, val component: ComponentName? = null, val icon: Int = 0)
    private enum class Tab { ALL, PINS, RECENT }
    private val accent = 0xFF8CE7C8.toInt()
    private val muted = 0xFFB0BCB8.toInt()
    private val prefs = activity.getSharedPreferences("watch_shell_apps", Context.MODE_PRIVATE)
    private val icons = LruCache<String, Drawable.ConstantState>(48)
    private val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
    val view: View get() = root
    private var tab = Tab.ALL
    private var allApps = emptyList<Entry>()
    private var shown = emptyList<Entry>()
    private var loading = true
    private var error = false
    private var resumed = false
    private var registered = false
    private var destroyed = false
    private var queryJob: Job? = null
    private var sheet: View? = null
    private val builtins = listOf(
        Entry("@health", "Health", icon = android.R.drawable.ic_menu_myplaces),
        Entry("@music", "Music", icon = android.R.drawable.ic_media_play),
        Entry("@notifications", "Notifications", icon = android.R.drawable.ic_dialog_email),
        Entry("@tools", "Tools", icon = android.R.drawable.ic_menu_recent_history),
        Entry("@settings", "Settings", icon = android.R.drawable.ic_menu_manage)
    )
    private val columns = run {
        val metrics = activity.resources.displayMetrics
        val safeWidthDp = metrics.widthPixels * .8f / metrics.density
        // Reserve room for labels after round-screen insets, including the user's text scale.
        val minimumCellDp = 88f * activity.resources.configuration.fontScale.coerceAtLeast(1f)
        if (safeWidthDp >= minimumCellDp * 3f) 3 else 2
    }
    private val adapter = AppAdapter()
    private val recycler = RecyclerView(activity).apply {
        layoutManager = GridLayoutManager(activity, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (position == 0 || position > shown.size) columns else 1
            }
        }
        adapter = this@AppGridPanel.adapter
        itemAnimator = null
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_NEVER
        val metrics = activity.resources.displayMetrics
        setPadding((metrics.widthPixels * .1f).roundToInt(), (metrics.heightPixels * .14f).roundToInt(),
            (metrics.widthPixels * .1f).roundToInt(), (metrics.heightPixels * .18f).roundToInt())
        isFocusable = true
        isFocusableInTouchMode = true
        setOnGenericMotionListener { _, event ->
            if (Build.VERSION.SDK_INT >= 26 && event.action == MotionEvent.ACTION_SCROLL &&
                event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                scrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(48)).roundToInt())
                true
            } else false
        }
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    if (getChildAdapterPosition(child) in 1..shown.size && height > 0) {
                        val offset = kotlin.math.abs((child.y + child.height / 2f) / height - .5f)
                        val scale = (1f - offset * .12f).coerceIn(.88f, 1f)
                        child.scaleX = scale; child.scaleY = scale
                    }
                }
            }
        })
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    init { root.addView(recycler, FrameLayout.LayoutParams(-1, -1)) }

    fun onResume() {
        if (destroyed) return
        resumed = true
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registered = runCatching {
                ContextCompat.registerReceiver(activity, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                true
            }.getOrDefault(false)
        }
        refresh()
        recycler.requestFocus()
    }

    fun onPause() {
        resumed = false
        queryJob?.cancel()
        if (registered) runCatching { activity.unregisterReceiver(packageReceiver) }
        registered = false
        closeSheet()
    }

    fun destroy() {
        onPause()
        destroyed = true
        recycler.adapter = null
        icons.evictAll()
    }

    /** Home gives an open app options sheet the first Back press. */
    fun handleBack(): Boolean {
        if (sheet == null) return false
        closeSheet()
        recycler.requestFocus()
        return true
    }

    private fun refresh() {
        if (!resumed || destroyed) return
        queryJob?.cancel()
        loading = true; error = false
        updateShown()
        queryJob = activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { queryApps() } }
            if (!resumed || destroyed) return@launch
            loading = false
            result.onSuccess { allApps = it; icons.evictAll() }.onFailure { error = true }
            updateShown()
        }
    }

    @Suppress("DEPRECATION")
    private fun queryApps(): List<Entry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = activity.packageManager.queryIntentActivities(intent, 0).mapNotNull {
            val info = it.activityInfo ?: return@mapNotNull null
            if (!info.enabled || !info.applicationInfo.enabled || !info.exported || info.packageName == activity.packageName)
                return@mapNotNull null
            val component = ComponentName(info.packageName, info.name)
            val label = runCatching { it.loadLabel(activity.packageManager).toString().trim() }.getOrDefault("").ifEmpty { info.packageName }
            Entry(component.flattenToString(), label, component)
        }.distinctBy { it.key }
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        return entries.sortedWith { a, b ->
            collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.key.compareTo(b.key)
        }
    }

    private fun pins() = prefs.getStringSet("pins", emptySet()).orEmpty().toSet()
    private fun recent() = prefs.getString("recent", "").orEmpty().split('|').filter { it.isNotEmpty() }
    private fun updateShown() {
        val available = builtins + allApps
        shown = when (tab) {
            Tab.ALL -> available
            Tab.PINS -> available.filter { it.key in pins() }
            Tab.RECENT -> recent().mapNotNull { key -> available.firstOrNull { it.key == key } }
        }
        adapter.notifyDataSetChanged()
    }

    private fun remember(entry: Entry) {
        prefs.edit().putString("recent", (listOf(entry.key) + recent().filter { it != entry.key }).take(12).joinToString("|")).apply()
    }

    private fun launch(entry: Entry) {
        when (entry.key) {
            "@health" -> { remember(entry); onNavigate("fitness") }
            "@music" -> { remember(entry); onNavigate("media") }
            "@notifications" -> { remember(entry); onNavigate("notifications") }
            "@tools" -> { remember(entry); onUtilities() }
            "@settings" -> { remember(entry); onSettings() }
            else -> try {
                activity.startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER); component = entry.component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                })
                remember(entry)
            } catch (_: RuntimeException) {
                Toast.makeText(activity, "${entry.label} cannot open. Refreshing apps.", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun closeSheet() { sheet?.let(root::removeView); sheet = null }
    private fun showSheet(entry: Entry) {
        closeSheet()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            val side = (activity.resources.displayMetrics.widthPixels * .16f).roundToInt()
            val end = (activity.resources.displayMetrics.heightPixels * .17f).roundToInt()
            setPadding(side, end, side, end)
        }
        val overlay = RoundScrollView(activity).apply {
            setBackgroundColor(Color.BLACK); isFillViewport = true
            addView(content, ViewGroup.LayoutParams(-1, -2))
        }
        content.addView(text(entry.label, 18f, Color.WHITE).apply { maxLines = 2 }, LinearLayout.LayoutParams(-1, -2))
        val isPinned = entry.key in pins()
        content.addView(chip(if (isPinned) "Unpin app" else "Pin app") {
            val updated = pins().toMutableSet()
            if (isPinned) updated.remove(entry.key) else updated.add(entry.key)
            prefs.edit().putStringSet("pins", updated).apply()
            closeSheet(); updateShown()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        entry.component?.let { component ->
            content.addView(chip("App info") {
                try {
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${component.packageName}")))
                    closeSheet()
                } catch (_: RuntimeException) {
                    Toast.makeText(activity, "App settings is unavailable", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        }
        content.addView(chip("Done") { closeSheet() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        sheet = overlay
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
    private fun rounded(color: Int, radius: Int = 24) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun touchBackground(color: Int = 0xFF18251F.toInt()) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(0xFF355747.toInt()))
        addState(intArrayOf(), rounded(color))
    }
    private fun text(value: String, size: Float, color: Int = muted) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        includeFontPadding = false; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(2), dp(7), dp(2), dp(7))
    }
    private fun chip(value: String, selected: Boolean = false, action: () -> Unit) = text(value, 12f, if (selected) Color.BLACK else Color.WHITE).apply {
        background = touchBackground(if (selected) accent else 0xFF18251F.toInt())
        minimumHeight = dp(44)
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); action() }
    }

    private inner class Header(content: LinearLayout) : RecyclerView.ViewHolder(content) {
        fun bind() {
            content.removeAllViews()
            val title = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            title.addView(chip("‹") { onClose() }.apply { textSize = 25f }, LinearLayout.LayoutParams(dp(44), dp(44)))
            title.addView(text("Apps", 22f, Color.WHITE), LinearLayout.LayoutParams(0, dp(44), 1f))
            title.addView(View(activity), LinearLayout.LayoutParams(dp(44), dp(44)))
            content.addView(title)
            val tabs = LinearLayout(activity)
            for ((value, label) in listOf(Tab.ALL to "All", Tab.PINS to "Pins", Tab.RECENT to "Recent")) {
                tabs.addView(chip(label, tab == value) {
                    tab = value; closeSheet(); updateShown(); recycler.scrollToPosition(0)
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), dp(4), dp(2), dp(10)) })
            }
            content.addView(tabs)
        }
        private val content = content
    }

    private inner class AppHolder(content: LinearLayout) : RecyclerView.ViewHolder(content) {
        private val icon = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            isDuplicateParentStateEnabled = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), rounded(0xFF355747.toInt(), 40))
                addState(intArrayOf(), rounded(0xFF17211E.toInt(), 40))
            }
            val inset = dp(10); setPadding(inset, inset, inset, inset)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val label = text("", 11f, Color.WHITE).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2), dp(2), dp(2), dp(2))
            minimumHeight = dp(36)
        }
        var iconJob: Job? = null
        private var key: String? = null
        init {
            content.gravity = Gravity.CENTER_HORIZONTAL
            content.setPadding(dp(3), dp(4), dp(3), dp(8))
            val size = dp(if (columns == 3) 48 else 56)
            content.addView(icon, LinearLayout.LayoutParams(size, size))
            content.addView(label, LinearLayout.LayoutParams(-1, -2))
        }
        fun bind(entry: Entry) {
            iconJob?.cancel(); key = entry.key
            label.text = if (entry.key in pins()) "★ ${entry.label}" else entry.label
            itemView.contentDescription = "Open ${entry.label}. Hold for pin and app options."
            itemView.setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); launch(entry) }
            itemView.setOnLongClickListener { it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); showSheet(entry); true }
            icon.clearColorFilter()
            if (entry.component == null) { icon.setImageResource(entry.icon); icon.setColorFilter(accent); return }
            val cached = icons.get(entry.key)
            if (cached != null) { icon.setImageDrawable(cached.newDrawable(activity.resources)); return }
            icon.setImageResource(android.R.drawable.sym_def_app_icon)
            iconJob = activity.lifecycleScope.launch {
                val drawable = withContext(Dispatchers.IO) {
                    runCatching { activity.packageManager.getActivityIcon(entry.component) }.getOrNull()
                }
                drawable?.constantState?.let { icons.put(entry.key, it) }
                if (!destroyed && key == entry.key && drawable != null) icon.setImageDrawable(drawable)
            }
        }
        fun recycle() { iconJob?.cancel(); key = null }
    }

    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = RecyclerView.LayoutParams(-1, -2)
    }
    private inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = shown.size + 2
        override fun getItemViewType(position: Int) = if (position == 0) 0 else if (position > shown.size) 2 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
            0 -> Header(column())
            1 -> AppHolder(column())
            else -> object : RecyclerView.ViewHolder(column()) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.scaleX = 1f; holder.itemView.scaleY = 1f
            when (holder) {
                is Header -> holder.bind()
                is AppHolder -> holder.bind(shown[position - 1])
                else -> (holder.itemView as LinearLayout).apply {
                    removeAllViews()
                    val status = when {
                        error -> "Apps could not refresh"
                        loading -> "Finding apps…"
                        tab == Tab.PINS && shown.isEmpty() -> "Hold an app to pin it here"
                        tab == Tab.RECENT && shown.isEmpty() -> "Apps you open appear here"
                        tab == Tab.ALL && allApps.isEmpty() -> "No other apps installed"
                        tab == Tab.ALL -> "${allApps.size} installed · Hold to pin"
                        else -> "Hold an app for options"
                    }
                    addView(text(status, 11f))
                    if (error) addView(chip("Refresh") { refresh() }, LinearLayout.LayoutParams(-1, dp(44)))
                }
            }
        }
        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is AppHolder) holder.recycle()
            super.onViewRecycled(holder)
        }
    }
}

package com.healthsync.watch.ui.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

/** A component-based drawer: launcher aliases and multiple entry points are preserved. */
class AppDrawerActivity : LauncherPanelActivity() {
    private data class AppEntry(val component: ComponentName, val label: String)

    private lateinit var recycler: RecyclerView
    private lateinit var drawerAdapter: DrawerAdapter
    private var allApps: List<AppEntry> = emptyList()
    private var visibleApps: List<AppEntry> = emptyList()
    private var query = ""
    private var loading = true
    private var loadError: String? = null
    private var loadJob: Job? = null
    private var receiverRegistered = false
    private val icons = LruCache<ComponentName, Drawable.ConstantState>(48)
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        query = savedInstanceState?.getString("app_search") ?: ""
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        drawerAdapter = DrawerAdapter()
        recycler = RecyclerView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutManager = LinearLayoutManager(this@AppDrawerActivity)
            adapter = drawerAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            val side = (resources.displayMetrics.widthPixels * 0.14f).roundToInt()
            val end = (resources.displayMetrics.heightPixels * 0.15f).roundToInt().coerceAtLeast(dp(36))
            setPadding(side, end, side, end)
            isFocusable = true
            isFocusableInTouchMode = true
            itemAnimator = null
            setOnGenericMotionListener { _, event ->
                if (Build.VERSION.SDK_INT >= 26 && event.action == MotionEvent.ACTION_SCROLL &&
                    event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    scrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(48)).roundToInt())
                    true
                } else false
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = scaleRows()
            })
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scaleRows() }
        }
        setContentView(recycler)
        recycler.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        refreshApps()
    }

    override fun onStop() {
        loadJob?.cancel()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(packageReceiver) }
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("app_search", query)
        super.onSaveInstanceState(outState)
    }

    private fun refreshApps() {
        loadJob?.cancel()
        loading = true
        loadError = null
        drawerAdapter.refreshStatus()
        if (visibleApps.isEmpty()) drawerAdapter.notifyItemChanged(1)
        loadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { queryApps() } }
            loading = false
            result.onSuccess {
                icons.evictAll()
                allApps = it
            }.onFailure {
                loadError = "Apps could not refresh. Tap Retry or open Android settings."
            }
            applyFilter()
        }
    }

    @Suppress("DEPRECATION")
    private fun queryApps(): List<AppEntry> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = packageManager.queryIntentActivities(launcher, 0).mapNotNull { resolved ->
            val info = resolved.activityInfo ?: return@mapNotNull null
            if (!info.enabled || !info.applicationInfo.enabled || !info.exported) return@mapNotNull null
            val label = runCatching { resolved.loadLabel(packageManager).toString().trim() }
                .getOrDefault("").ifEmpty { info.packageName }
            AppEntry(ComponentName(info.packageName, info.name), label)
        }.distinctBy { it.component }
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        return entries.sortedWith { left, right ->
            val labelOrder = collator.compare(left.label, right.label)
            if (labelOrder != 0) labelOrder else left.component.flattenToString().compareTo(right.component.flattenToString())
        }
    }

    private fun applyFilter() {
        val search = query.trim()
        val previous = visibleApps.size.coerceAtLeast(1)
        visibleApps = if (search.isEmpty()) allApps else allApps.filter {
            it.label.contains(search, ignoreCase = true) || it.component.packageName.contains(search, ignoreCase = true)
        }
        // Keep the search header alive while changing only the rows below it.
        drawerAdapter.notifyItemRangeRemoved(1, previous)
        drawerAdapter.notifyItemRangeInserted(1, visibleApps.size.coerceAtLeast(1))
        drawerAdapter.refreshStatus()
        recycler.post { scaleRows() }
    }

    private fun launchApp(entry: AppEntry) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(recycler.windowToken, 0)
        val launched = launchFirst(listOf(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = entry.component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }), "${entry.label} is no longer available or cannot open.")
        if (!launched) refreshApps()
    }

    private fun scaleRows() {
        if (!::recycler.isInitialized || recycler.height <= 0) return
        for (index in 0 until recycler.childCount) {
            val child = recycler.getChildAt(index)
            val position = recycler.getChildAdapterPosition(child)
            if (position in 1..visibleApps.size) {
                val relative = (child.y + child.height / 2f) / recycler.height
                val scale = 1f - minOf(kotlin.math.abs(0.5f - relative), 0.5f) * 0.14f
                child.scaleX = scale
                child.scaleY = scale
                child.alpha = scale
            } else {
                child.scaleX = 1f
                child.scaleY = 1f
                child.alpha = 1f
            }
        }
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private inner class HeaderHolder(val content: LinearLayout) : RecyclerView.ViewHolder(content) {
        val status: TextView
        init {
            heading(content, "Apps", "Installed on your watch")
            action(content, "Android settings") { openSystemSettings() }
            val search = EditText(this@AppDrawerActivity).apply {
                hint = "Search apps"
                contentDescription = "Search installed watch apps by name"
                textSize = 14f
                setTextColor(Color.WHITE)
                setHintTextColor(muted)
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                minHeight = dp(48)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = shape(panelColor)
                isSaveEnabled = false // The Activity owns query restoration, including during a reload.
                setText(query)
                setSelection(text.length)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        query = s?.toString() ?: ""
                        applyFilter()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                            ?.hideSoftInputFromWindow(windowToken, 0)
                        clearFocus()
                        recycler.requestFocus()
                        true
                    } else false
                }
            }
            content.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            status = label(content, "Loading apps…", 11f)
        }
    }

    private inner class AppHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val icon: ImageView
        val title: TextView
        val subtitle: TextView
        var iconJob: Job? = null
        var boundComponent: ComponentName? = null
        init {
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = dp(64)
            row.setPadding(dp(10), dp(10), dp(10), dp(10))
            row.background = buttonBackground()
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(7)
            }
            icon = ImageView(this@AppDrawerActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) })
            val text = LinearLayout(this@AppDrawerActivity).apply { orientation = LinearLayout.VERTICAL }
            row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            title = TextView(this@AppDrawerActivity).apply {
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            subtitle = TextView(this@AppDrawerActivity).apply {
                textSize = 10f
                setTextColor(muted)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            text.addView(title)
            text.addView(subtitle)
        }

        fun bind(entry: AppEntry) {
            iconJob?.cancel()
            boundComponent = entry.component
            title.text = entry.label
            subtitle.text = entry.component.packageName
            row.contentDescription = "Open ${entry.label}, ${entry.component.packageName}. Hold for app settings."
            row.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                launchApp(entry)
            }
            row.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                launchFirst(listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${entry.component.packageName}")), Intent(Settings.ACTION_SETTINGS)),
                    "App settings is unavailable on this watch.")
                true
            }
            val cached = icons.get(entry.component)
            if (cached != null) {
                icon.setImageDrawable(cached.newDrawable(resources))
                return
            }
            icon.setImageResource(android.R.drawable.sym_def_app_icon)
            iconJob = lifecycleScope.launch {
                val drawable = withContext(Dispatchers.IO) {
                    runCatching { packageManager.getActivityIcon(entry.component) }.getOrNull()
                }
                drawable?.constantState?.let { icons.put(entry.component, it) }
                if (boundComponent == entry.component && drawable != null) icon.setImageDrawable(drawable)
            }
        }
    }

    private inner class DrawerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var header: HeaderHolder? = null
        override fun getItemCount() = visibleApps.size.coerceAtLeast(1) + 2
        override fun getItemViewType(position: Int) = when {
            position == 0 -> 0
            position == itemCount - 1 -> 3
            visibleApps.isEmpty() -> 2
            else -> 1
        }

        fun refreshStatus() {
            header?.status?.text = when {
                loading -> "Loading apps…"
                loadError != null -> "Unable to refresh apps"
                query.isNotBlank() -> "${visibleApps.size} of ${allApps.size} apps"
                else -> "${allApps.size} apps · A–Z"
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
            0 -> HeaderHolder(column()).also { header = it }
            1 -> AppHolder(column())
            2 -> object : RecyclerView.ViewHolder(column().apply {
                label(this, "", 13f)
            }) {}
            else -> object : RecyclerView.ViewHolder(column().apply {
                action(this, "Retry / refresh") { refreshApps() }
                action(this, "Quick settings") {
                    if (launchFirst(listOf(Intent(this@AppDrawerActivity, QuickSettingsActivity::class.java)), "Quick settings could not open.")) {
                        finish() // A panel's Back to clock must return directly to the clock.
                    }
                }
                action(this, "Back to clock") { backToClock() }
            }) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is HeaderHolder -> refreshStatus()
                is AppHolder -> holder.bind(visibleApps[position - 1])
                else -> if (getItemViewType(position) == 2) {
                    val emptyText = (holder.itemView as LinearLayout).getChildAt(0) as TextView
                    emptyText.text = when {
                        loading -> "Finding launchable apps…"
                        loadError != null -> loadError
                        query.isNotBlank() -> "No matching apps. Clear the search to show all apps."
                        else -> "No launchable apps found. Android settings remains available."
                    }
                }
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is AppHolder) {
                holder.iconJob?.cancel()
                holder.boundComponent = null
            }
            super.onViewRecycled(holder)
        }
    }
}

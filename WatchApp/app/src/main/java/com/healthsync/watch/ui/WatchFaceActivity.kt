package com.healthsync.watch.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.VelocityTracker
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.healthsync.watch.R
import com.healthsync.watch.data.WatchPreferences
import com.healthsync.watch.notification.NotificationsInboxActivity
import com.healthsync.watch.notification.NotificationInboxStore
import com.healthsync.watch.ui.shell.*
import com.healthsync.watch.timer.TimerPhase
import com.healthsync.watch.timer.WatchTimerStore
import com.healthsync.watch.ui.launcher.AppDrawerActivity
import com.healthsync.watch.ui.launcher.QuickSettingsActivity
import com.healthsync.watch.ui.launcher.LauncherBrightness
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.service.WorkoutTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class WatchFaceActivity : AppCompatActivity() {
    companion object {
        const val ACTION_BT_STATUS = "com.healthsync.watch.BT_STATUS"
        const val EXTRA_CONNECTED = "connected"
        const val EXTRA_PANEL = "watch_shell_panel"
    }

    private lateinit var casioWatchFaceView: CasioWatchFaceView
    private lateinit var circularWatchFaceView: CircularWatchFaceView
    private lateinit var orbitFace: OrbitWatchFaceView
    private lateinit var shellDock: LinearLayout
    private lateinit var panelHost: FrameLayout
    private lateinit var notificationDock: WatchDockButton
    private val navigation = ShellNavigation()
    private data class Surface(val view: View, val resume: () -> Unit, val pause: () -> Unit,
        val destroy: () -> Unit, val back: () -> Boolean = { false }, val keepAwake: () -> Boolean = { false })
    private var surface: Surface? = null
    private var unread = 0
    private var unreadJob: Job? = null
    private var velocity: VelocityTracker? = null
    private var openingPanel = false
    private var closingPanel = false
    private var wasPanelAtDown = false
    private var animatingPanel = false

    private val preferences by lazy { WatchPreferences(this) }
    private val wristWake by lazy { WristWakeController(this) {
        if (isAmbient && surface == null && resumed && screenIsInteractive) {
            setAmbientDisplay(false)
            scheduleAmbient()
        }
    } }
    private val handler = Handler(Looper.getMainLooper())
    private var totalsJob: Job? = null
    private var displayedDay = -1
    private val numberFormat = NumberFormat.getIntegerInstance()

    private var isAmbient = false
    private var resumed = false
    private var windowFocused = false
    private var screenIsInteractive = true
    private var wakeGestureInProgress = false
    private var interactiveBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var interactiveSystemUiVisibility: Int? = null
    private var dailyCalories = 0
    private var dailyDistanceKm = 0f
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureStartTime = 0L
    private var gestureConsumed = false
    private var touchInProgress = false
    private var gestureDirection: LauncherGesturePolicy.Direction? = null
    private var gestureDown: MotionEvent? = null
    private val faceGestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(event: MotionEvent) {
                if (!gestureConsumed && !isAmbient && resumed && screenIsInteractive) {
                    gestureConsumed = true
                    gestureDown?.let { cancelChildGesture(it) }
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    openPanel("faces")
                }
            }
        })
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startHealthServices()
        updateLiveData()
    }

    private val clockTick = object : Runnable {
        override fun run() {
            if (!resumed || !screenIsInteractive || surface != null) return
            updateLiveData()
            handler.postDelayed(this, WatchDisplayPolicy.nextTickDelay(System.currentTimeMillis(), isAmbient))
        }
    }

    private val enterAmbient = Runnable {
        if (surface == null && !touchInProgress && WatchDisplayPolicy.shouldDim(preferences.alwaysOnDisplayEnabled, resumed && screenIsInteractive, windowFocused)) {
            setAmbientDisplay(true)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenIsInteractive = false
                    wristWake.stop()
                    touchInProgress = false
                    surface?.pause?.invoke()
                    handler.removeCallbacks(clockTick)
                    handler.removeCallbacks(enterAmbient)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    return
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenIsInteractive = true
                    setAmbientDisplay(false)
                    if (resumed) surface?.resume?.invoke()
                    applyDisplayPolicy()
                    handler.removeCallbacks(clockTick)
                    if (resumed) handler.post(clockTick)
                    scheduleAmbient()
                }
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                    refreshDailyTotals()
                    handler.removeCallbacks(clockTick)
                    if (resumed && screenIsInteractive) handler.post(clockTick)
                    return
                }
            }
            if (intent.action == "com.healthsync.watch.WORKOUT_SAVED") refreshDailyTotals()
            if (intent.action == NotificationInboxStore.ACTION_CHANGED) refreshUnread()
            // Sensor events must not turn the minute-only dim clock into a continuous redraw loop.
            if (!isAmbient && surface == null) updateLiveData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Older builds saved a headless Wear ambient fragment. Restoring it on a generic Android
        // watch can crash before this activity starts. This screen has no unsaved form state;
        // its face and AOD choices are restored from preferences instead.
        super.onCreate(null)
        interactiveBrightness = window.attributes.screenBrightness

        setContentView(R.layout.activity_watch_face)
        setWatchFullscreen()

        casioWatchFaceView = findViewById(R.id.casioWatchFaceView)
        circularWatchFaceView = findViewById(R.id.circularWatchFaceView)
        orbitFace = findViewById(R.id.orbitWatchFace)
        shellDock = findViewById(R.id.shellDock)
        panelHost = findViewById(R.id.shellPanelHost)
        preferences.upgradeToWatchShell()
        orbitFace.onFitness = { openPanel("fitness") }
        orbitFace.onNotifications = { openPanel("notifications") }
        orbitFace.onControls = { openPanel("controls") }
        orbitFace.onWorkout = { launchWorkout() }
        orbitFace.onTimer = { startActivity(Intent(this, WatchUtilitiesActivity::class.java)
            .putExtra(WatchUtilitiesActivity.EXTRA_TOOL, "timer")) }
        fun dock(glyph: String, label: String, panel: String): WatchDockButton = WatchDockButton(this, glyph, label).apply {
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); openPanel(panel) }
            shellDock.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins((3 * resources.displayMetrics.density).toInt(), 0, (3 * resources.displayMetrics.density).toInt(), 0)
            })
        }
        dock("apps", "Apps", "apps")
        notificationDock = dock("bell", "Notifications", "notifications")
        dock("settings", "Watch settings", "settings")
        val faceRoot = findViewById<View>(R.id.watchFaceRoot)
        faceRoot.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val diameter = min(faceRoot.width, faceRoot.height)
            if (diameter > 0) {
                val dockParams = shellDock.layoutParams as FrameLayout.LayoutParams
                dockParams.width = (diameter * .49f).roundToInt()
                dockParams.height = minOf((44f * resources.displayMetrics.density).roundToInt(), (diameter * .14f).roundToInt())
                dockParams.bottomMargin = (faceRoot.height - diameter) / 2 + (diameter * .095f).roundToInt()
                shellDock.layoutParams = dockParams
            }
        }

        // Configure Casio watchface interactive buttons
        casioWatchFaceView.onModeClick = {
            val next = if (preferences.watchFaceStyle == "classic") "orbit" else "classic"
            preferences.watchFaceStyle = next
            updateWatchFaceVisibility()
            Toast.makeText(this, if (next == "orbit") "Orbit Face" else "Casio Face", Toast.LENGTH_SHORT).show()
        }
        casioWatchFaceView.onStartWorkoutClick = { launchWorkout() }
        casioWatchFaceView.onHistoryClick = {
            startActivity(Intent(this, WatchHistoryActivity::class.java))
        }

        updateWatchFaceVisibility()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (surface != null) {
                    if (surface?.back?.invoke() != true) closePanel()
                } else if (isHome()) {
                    setAmbientDisplay(false)
                    scheduleAmbient()
                } else {
                    finish()
                }
            }
        })
        requestRequiredPermissions()
        val restored = savedInstanceState?.getStringArrayList("shell_history")
        if (restored != null) navigation.restore(restored)
        else intent.getStringExtra(EXTRA_PANEL)?.let { navigation.open(it) }
        navigation.current?.let { renderPanel(it, animate = false) }
    }

    @Suppress("DEPRECATION")
    private fun isHome(): Boolean = intent.hasCategory(Intent.CATEGORY_HOME) || runCatching {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }.getOrDefault(false)

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) setIntent(intent)
        if (::circularWatchFaceView.isInitialized) {
            clearPanels()
            setAmbientDisplay(false)
            intent?.getStringExtra(EXTRA_PANEL)?.let { openPanel(it) }
            scheduleAmbient()
        }
    }

    @Suppress("DEPRECATION")
    private fun setWatchFullscreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun applyDisplayPolicy() {
        if (resumed && windowFocused && screenIsInteractive &&
            (surface?.keepAwake?.invoke() == true || (surface == null && preferences.alwaysOnDisplayEnabled))) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun scheduleAmbient() {
        handler.removeCallbacks(enterAmbient)
        if (surface == null && !isAmbient && !touchInProgress &&
            WatchDisplayPolicy.shouldDim(preferences.alwaysOnDisplayEnabled, resumed && screenIsInteractive, windowFocused)) {
            handler.postDelayed(enterAmbient, preferences.aodIdleDelayMs)
        }
    }

    @Suppress("DEPRECATION") // Immersive flags are the supported fullscreen API on Android 8.
    private fun setAmbientDisplay(ambient: Boolean) {
        val decor = window.decorView
        if (ambient) {
            if (!isAmbient) interactiveSystemUiVisibility = decor.systemUiVisibility
            decor.systemUiVisibility = decor.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            interactiveSystemUiVisibility?.let { decor.systemUiVisibility = it }
            interactiveSystemUiVisibility = null
        }
        isAmbient = ambient
        if (ambient && resumed && screenIsInteractive && surface == null && preferences.wristWakeEnabled) wristWake.start()
        else wristWake.stop()
        circularWatchFaceView.setAmbientMode(ambient)
        orbitFace.setAmbient(ambient)
        casioWatchFaceView.setAmbientMode(ambient)
        window.attributes = window.attributes.apply {
            screenBrightness = if (ambient) preferences.aodBrightness else interactiveBrightness
        }
        updateWatchFaceVisibility()
        handler.removeCallbacks(clockTick)
        if (resumed && screenIsInteractive && surface == null) handler.post(clockTick)
    }

    private fun cancelChildGesture(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    private fun surfaceFor(panel: String): Surface = when (panel) {
        "apps" -> AppGridPanel(this, { closePanel() }, { openTools() }, { openPanel("settings") }).let {
            it.onNavigate = { route -> openPanel(route) }
            Surface(it.view, it::onResume, it::onPause, it::destroy, it::handleBack)
        }
        "controls" -> QuickControlsPanel(this, { closePanel() }, { openPanel("settings") }, { openTools() }).let {
            it.onDisplayChanged = { syncBrightness(); applyDisplayPolicy() }
            Surface(it.view, it::onResume, it::onPause, it::destroy, it::handleBack, { it.isFlashlightActive })
        }
        "notifications" -> NotificationsPanel(this, { closePanel() }, { openPanel("settings") }).let {
            Surface(it.view, it::onResume, it::onPause, it::destroy)
        }
        "fitness" -> FitnessPanel(this, { closePanel() }, { openPanel("settings") }).let {
            Surface(it.view, it::onResume, it::onPause, it::destroy)
        }
        "faces" -> FacesPanel(this, { closePanel() }, { clearPanels(); updateLiveData() }).let {
            Surface(it.view, it::onResume, it::onPause, it::destroy)
        }
        "media" -> MediaPanel(this, { closePanel() }, {
            startActivity(Intent(this, WatchOptionsActivity::class.java)
                .putExtra(WatchOptionsActivity.EXTRA_SCREEN, WatchOptionsActivity.SCREEN_PHONE))
        }).let { Surface(it.view, it::onResume, it::onPause, it::destroy) }
        else -> WatchSettingsPanel(this, { closePanel() }, { openPanel(it) }, { openTools() }).let {
            Surface(it.view, it::onResume, it::onPause, it::destroy)
        }
    }

    private fun openTools() { startActivity(Intent(this, WatchUtilitiesActivity::class.java)) }

    private fun syncBrightness() {
        LauncherBrightness.apply(window, this)
        interactiveBrightness = window.attributes.screenBrightness
    }

    private fun openPanel(panel: String) {
        if (panel !in ShellNavigation.PANELS) return
        navigation.open(panel)
        renderPanel(panel)
    }

    private fun renderPanel(panel: String, animate: Boolean = true) {
        handler.removeCallbacks(enterAmbient)
        handler.removeCallbacks(clockTick)
        setAmbientDisplay(false)
        panelHost.animate().cancel()
        surface?.pause?.invoke()
        surface?.destroy?.invoke()
        panelHost.removeAllViews()
        surface = surfaceFor(panel)
        panelHost.addView(surface!!.view, FrameLayout.LayoutParams(-1, -1))
        panelHost.visibility = View.VISIBLE
        panelHost.alpha = 1f
        panelHost.translationX = 0f
        panelHost.translationY = 0f
        if (resumed && screenIsInteractive) surface?.resume?.invoke()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (animate) {
            panelHost.alpha = 0f
            panelHost.translationY = 12f * resources.displayMetrics.density
            panelHost.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        }
        updateWatchFaceVisibility()
    }

    private fun closePanel() {
        if (surface == null || animatingPanel) return
        val prior = navigation.back()
        if (prior != null) renderPanel(prior) else clearPanels()
    }

    private fun clearPanels() {
        panelHost.animate().cancel()
        animatingPanel = false
        openingPanel = false
        closingPanel = false
        navigation.home()
        surface?.pause?.invoke()
        surface?.destroy?.invoke()
        surface = null
        panelHost.removeAllViews()
        panelHost.visibility = View.GONE
        panelHost.translationX = 0f
        panelHost.translationY = 0f
        panelHost.alpha = 1f
        syncBrightness()
        updateWatchFaceVisibility()
        applyDisplayPolicy()
        updateLiveData()
        handler.removeCallbacks(clockTick)
        if (resumed && screenIsInteractive) handler.post(clockTick)
        scheduleAmbient()
        refreshUnread()
    }

    private fun panelName(direction: LauncherGesturePolicy.Direction): String = when (direction) {
        LauncherGesturePolicy.Direction.UP -> "apps"
        LauncherGesturePolicy.Direction.DOWN -> "controls"
        LauncherGesturePolicy.Direction.LEFT -> "notifications"
        LauncherGesturePolicy.Direction.RIGHT -> "fitness"
    }

    private fun directedDistance(event: MotionEvent): Float = when (gestureDirection) {
        LauncherGesturePolicy.Direction.UP -> gestureStartY - event.y
        LauncherGesturePolicy.Direction.DOWN -> event.y - gestureStartY
        LauncherGesturePolicy.Direction.LEFT -> gestureStartX - event.x
        LauncherGesturePolicy.Direction.RIGHT -> event.x - gestureStartX
        else -> 0f
    }.coerceAtLeast(0f)

    private fun moveOpeningPanel(event: MotionEvent) {
        val distance = directedDistance(event)
        val w = panelHost.width.toFloat().coerceAtLeast(1f)
        val h = panelHost.height.toFloat().coerceAtLeast(1f)
        panelHost.translationX = when (gestureDirection) {
            LauncherGesturePolicy.Direction.LEFT -> (w - distance).coerceAtLeast(0f)
            LauncherGesturePolicy.Direction.RIGHT -> -(w - distance).coerceAtLeast(0f)
            else -> 0f
        }
        panelHost.translationY = when (gestureDirection) {
            LauncherGesturePolicy.Direction.UP -> (h - distance).coerceAtLeast(0f)
            LauncherGesturePolicy.Direction.DOWN -> -(h - distance).coerceAtLeast(0f)
            else -> 0f
        }
    }

    private fun finishOpeningPanel(event: MotionEvent, cancelled: Boolean) {
        velocity?.computeCurrentVelocity(1000)
        val horizontal = gestureDirection == LauncherGesturePolicy.Direction.LEFT || gestureDirection == LauncherGesturePolicy.Direction.RIGHT
        val size = if (horizontal) panelHost.width.toFloat() else panelHost.height.toFloat()
        val speed = when (gestureDirection) {
            LauncherGesturePolicy.Direction.UP -> -(velocity?.yVelocity ?: 0f)
            LauncherGesturePolicy.Direction.DOWN -> velocity?.yVelocity ?: 0f
            LauncherGesturePolicy.Direction.LEFT -> -(velocity?.xVelocity ?: 0f)
            else -> velocity?.xVelocity ?: 0f
        }
        val commit = !cancelled && (directedDistance(event) >= size * .24f || speed > 550f * resources.displayMetrics.density)
        openingPanel = false
        if (commit) {
            gestureDirection?.let { navigation.open(panelName(it)) }
            panelHost.animate().translationX(0f).translationY(0f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else clearPanels()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (animatingPanel) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isAmbient) {
            touchInProgress = true
            wakeGestureInProgress = true
            setAmbientDisplay(false)
        }
        if (wakeGestureInProgress) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                wakeGestureInProgress = false
                touchInProgress = false
                scheduleAmbient()
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchInProgress = true
            scheduleAmbient()
            gestureStartX = event.x
            gestureStartY = event.y
            gestureStartTime = event.eventTime
            gestureConsumed = false
            gestureDirection = null
            wasPanelAtDown = surface != null
            gestureDown?.recycle()
            gestureDown = MotionEvent.obtain(event)
            velocity?.recycle()
            velocity = VelocityTracker.obtain()
        }
        velocity?.addMovement(event)
        if (!wasPanelAtDown) faceGestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            gestureConsumed = true
            gestureDirection = null
            if (openingPanel) clearPanels()
            closingPanel = false
            panelHost.translationX = 0f
            cancelChildGesture(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1) {
            if (openingPanel) moveOpeningPanel(event)
            else if (closingPanel) panelHost.translationX = (event.x - gestureStartX).coerceAtLeast(0f)
            else if (!gestureConsumed) {
                val shortest = min(window.decorView.width, window.decorView.height).toFloat()
                val threshold = maxOf(28f * resources.displayMetrics.density, shortest * .11f)
                val direction = LauncherGesturePolicy.direction(event.x - gestureStartX,
                    event.y - gestureStartY, threshold, event.eventTime - gestureStartTime)
                if (wasPanelAtDown) {
                    if (direction == LauncherGesturePolicy.Direction.RIGHT && gestureStartX < shortest * .13f) {
                        gestureConsumed = true
                        closingPanel = true
                        cancelChildGesture(event)
                        panelHost.animate().cancel()
                        panelHost.translationY = 0f
                        panelHost.translationX = (event.x - gestureStartX).coerceAtLeast(0f)
                    }
                } else {
                    val vertical = direction == LauncherGesturePolicy.Direction.UP || direction == LauncherGesturePolicy.Direction.DOWN
                    val canNavigate = !vertical || preferences.watchFaceStyle != "modern" ||
                        gestureStartY < window.decorView.height * .2f || gestureStartY > window.decorView.height * .8f
                    if (direction != null && canNavigate) {
                        gestureConsumed = true
                        gestureDirection = direction
                        cancelChildGesture(event)
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        faceGestures.onTouchEvent(cancel)
                        cancel.recycle()
                        renderPanel(panelName(direction), animate = false)
                        openingPanel = true
                        moveOpeningPanel(event)
                    }
                }
            }
        }
        val ended = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        if (ended) {
            touchInProgress = false
            if (openingPanel) finishOpeningPanel(event, event.actionMasked == MotionEvent.ACTION_CANCEL)
            else if (closingPanel) {
                closingPanel = false
                velocity?.computeCurrentVelocity(1000)
                val commit = event.actionMasked == MotionEvent.ACTION_UP &&
                    (event.x - gestureStartX > panelHost.width * .25f || (velocity?.xVelocity ?: 0f) > 550f * resources.displayMetrics.density)
                if (commit && surface?.back?.invoke() == true) panelHost.animate().translationX(0f).setDuration(180).start()
                else if (commit) closePanel()
                else panelHost.animate().translationX(0f).setDuration(180).start()
            }
            scheduleAmbient()
            gestureDown?.recycle()
            gestureDown = null
            velocity?.recycle()
            velocity = null
            gestureDirection = null
        }
        if (gestureConsumed) return true
        return super.dispatchTouchEvent(event)
    }
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::casioWatchFaceView.isInitialized && !wakeGestureInProgress) {
            if (isAmbient) setAmbientDisplay(false)
            scheduleAmbient()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused = hasFocus
        if (!::casioWatchFaceView.isInitialized) return
        // Android's first immersive-mode hint temporarily takes focus without pausing this
        // activity. Keep the dim clock through that overlay; onPause restores the full UI
        // when the user actually leaves. Reassert the display policy when focus returns.
        if (hasFocus && resumed) {
            if (isAmbient) setAmbientDisplay(true) else setWatchFullscreen()
        }
        applyDisplayPolicy()
        scheduleAmbient()
    }

    private fun launchWorkout() {
        val active = WorkoutTrackingService.isActive
        val type = WorkoutTrackingService.activeActivityType
        val target = if (!active) WorkoutSelectionActivity::class.java
        else if (type.contains("strength", true) || type.contains("home", true)) StrengthWorkoutActivity::class.java
        else ActiveWorkoutActivity::class.java
        startActivity(Intent(this, target).putExtra(WorkoutTrackingService.EXTRA_ACTIVITY_TYPE, type))
    }

    private fun updateWatchFaceVisibility() {
        val style = preferences.watchFaceStyle
        val clockShortcuts = WatchFaceCatalog.showsClockShortcuts(style, preferences.clockShortcutsEnabled)
        orbitFace.setAodStyle(preferences.aodStyle)
        circularWatchFaceView.setAodStyle(if (preferences.aodStyle == "face")
            WatchFaceCatalog.ambientStyleForFace(style) else preferences.aodStyle)
        casioWatchFaceView.setAodStyle(preferences.aodStyle)
        circularWatchFaceView.visibility = View.GONE
        orbitFace.visibility = if ((!isAmbient && style == "orbit") || (isAmbient && style == "orbit")) View.VISIBLE else View.GONE
        casioWatchFaceView.visibility = if ((!isAmbient && style == "classic") || (isAmbient && style == "classic")) View.VISIBLE else View.GONE
        shellDock.visibility = if (clockShortcuts && !isAmbient && surface == null) View.VISIBLE else View.GONE
        val wallpaper = preferences.wallpaperBackdropEnabled && !isAmbient && surface == null && style != "classic"
        if (wallpaper) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(if (wallpaper) Color.TRANSPARENT else Color.BLACK))
        findViewById<View>(R.id.watchFaceRoot).setBackgroundColor(if (wallpaper) Color.TRANSPARENT else Color.BLACK)
        circularWatchFaceView.setWallpaperBackdrop(wallpaper)
        orbitFace.setWallpaper(wallpaper)
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct in 0..100) pct else -1
        } catch (_: Exception) { -1 }
    }

    private fun updateLiveData() {
        val nowCal = Calendar.getInstance()

        circularWatchFaceView.updateTime(nowCal)
        orbitFace.updateTime(nowCal)
        if (casioWatchFaceView.visibility == View.VISIBLE || isAmbient) casioWatchFaceView.updateTime(nowCal)

        val heart = SensorCollectorService.watchFaceHeartRate(nowCal.timeInMillis)
        val bpm = heart.bpm
        orbitFace.setHeartRateTime(heart.capturedAt)
        orbitFace.setHeartRateUnverified(heart.sensorReading)
        circularWatchFaceView.setHeartRateTime(heart.capturedAt)
        circularWatchFaceView.setHeartRateUnverified(heart.sensorReading)
        casioWatchFaceView.setHeartRateTime(heart.capturedAt)
        casioWatchFaceView.setHeartRateUnverified(heart.sensorReading)
        val steps = SensorCollectorService.latestSteps.coerceAtLeast(0)
        val stepGoal = preferences.stepGoal.coerceAtLeast(1)
        val connected = BluetoothClientService.isConnected
        val battery = getBatteryPercentage()
        orbitFace.setData(steps, stepGoal, bpm, battery, connected, unread, WorkoutTrackingService.isActive)
        val timer = WatchTimerStore.read(this)
        orbitFace.setTimer(when (timer.phase) {
            TimerPhase.RUNNING -> {
                val seconds = (timer.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), WatchTimerStore.bootCount(this)) + 999L) / 1000L
                if (seconds >= 3600L) "TIMER ${seconds / 3600L}h ${seconds % 3600L / 60L}m"
                else String.format(Locale.getDefault(), "TIMER %02d:%02d", seconds / 60L, seconds % 60L)
            }
            TimerPhase.PAUSED -> "TIMER PAUSED"
            TimerPhase.FINISHED -> "TIMER FINISHED"
            else -> null
        })
        notificationDock.badge = unread > 0
        notificationDock.contentDescription = "Notifications, $unread unread"
        circularWatchFaceView.setHealthData(bpm, steps, stepGoal, dailyCalories, dailyDistanceKm)
        circularWatchFaceView.setBluetoothConnected(connected)
        circularWatchFaceView.setBatteryLevel(battery)

        // Update Casio Watch Face (AOD & Interactive)
        casioWatchFaceView.setHealthData(bpm, steps, stepGoal, dailyCalories, dailyDistanceKm)
        casioWatchFaceView.setBluetoothConnected(connected)
        casioWatchFaceView.setBatteryLevel(battery)

        val today = nowCal.get(Calendar.DAY_OF_YEAR)
        if (today != displayedDay) {
            displayedDay = today
            refreshDailyTotals()
        }
    }

    private fun refreshDailyTotals() {
        totalsJob?.cancel()
        totalsJob = lifecycleScope.launch {
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sessions = withContext(Dispatchers.IO) {
                WorkoutHistoryStore.read(applicationContext).filter { it.start_time >= startOfDay }
            }
            val activeMins = (sessions.sumOf { it.duration_sec.toLong() } / 60)
            dailyDistanceKm = (sessions.sumOf { it.distance_m.toDouble() } / 1000).toFloat()
            dailyCalories = sessions.sumOf { it.calories }.toInt()

            val heart = SensorCollectorService.watchFaceHeartRate()
            casioWatchFaceView.setHeartRateTime(heart.capturedAt)
            casioWatchFaceView.setHeartRateUnverified(heart.sensorReading)
            circularWatchFaceView.setHeartRateTime(heart.capturedAt)
            circularWatchFaceView.setHeartRateUnverified(heart.sensorReading)
            casioWatchFaceView.setHealthData(heart.bpm,
                SensorCollectorService.latestSteps.coerceAtLeast(0),
                preferences.stepGoal.coerceAtLeast(1),
                dailyCalories, dailyDistanceKm)
            circularWatchFaceView.setHealthData(heart.bpm,
                SensorCollectorService.latestSteps.coerceAtLeast(0),
                preferences.stepGoal.coerceAtLeast(1), dailyCalories, dailyDistanceKm)
        }
    }

    private fun refreshUnread() {
        unreadJob?.cancel()
        unreadJob = lifecycleScope.launch {
            unread = withContext(Dispatchers.IO) {
                runCatching { NotificationInboxStore.getInstance(applicationContext).read().count { !it.isRead } }.getOrDefault(0)
            }
            if (surface == null) updateLiveData()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("shell_history", navigation.snapshot())
        super.onSaveInstanceState(outState)
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()
        fun addIfMissing(permission: String) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) needed += permission
        }
        addIfMissing(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) addIfMissing(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) addIfMissing(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) addIfMissing(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isEmpty()) startHealthServices() else permLauncher.launch(needed.toTypedArray())
    }

    private fun startHealthServices() {
        listOf(BluetoothClientService::class.java, SensorCollectorService::class.java).forEach { service ->
            runCatching { ContextCompat.startForegroundService(this, Intent(this, service)) }
                .onFailure { android.util.Log.w("WatchFace", "Could not start ${service.simpleName}", it) }
        }
        if (getSharedPreferences("watch_active_workout", MODE_PRIVATE).getBoolean("active", false)) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, WorkoutTrackingService::class.java)
                    .setAction(WorkoutTrackingService.ACTION_REQUEST_UPDATE))
            }.onFailure { android.util.Log.w("WatchFace", "Could not restore workout", it) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        screenIsInteractive = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        LauncherBrightness.apply(window, this)
        interactiveBrightness = window.attributes.screenBrightness
        wakeGestureInProgress = false
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction("com.healthsync.watch.HR_UPDATE")
            addAction("com.healthsync.watch.STEP_UPDATE")
            addAction("com.healthsync.watch.WORKOUT_SAVED")
            addAction(ACTION_BT_STATUS)
            addAction(WorkoutTrackingService.ACTION_UPDATE)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(NotificationInboxStore.ACTION_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        setAmbientDisplay(false)
        setWatchFullscreen()
        applyDisplayPolicy()
        scheduleAmbient()
        refreshDailyTotals()
        startHealthServices()
        refreshUnread()
        if (screenIsInteractive) surface?.resume?.invoke()
        if (surface != null) handler.removeCallbacks(clockTick)
    }

    override fun onPause() {
        resumed = false
        touchInProgress = false
        surface?.pause?.invoke()
        if (openingPanel) clearPanels()
        closingPanel = false
        panelHost.animate().cancel()
        panelHost.translationX = 0f
        panelHost.translationY = 0f
        gestureDown?.let {
            val cancel = MotionEvent.obtain(it).apply { action = MotionEvent.ACTION_CANCEL }
            faceGestures.onTouchEvent(cancel)
            cancel.recycle()
        }
        gestureDown?.recycle()
        gestureDown = null
        gestureDirection = null
        velocity?.recycle()
        velocity = null
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(enterAmbient)
        setAmbientDisplay(false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        casioWatchFaceView.stopIlluminator()
        runCatching { unregisterReceiver(receiver) }
        super.onPause()
    }

    override fun onDestroy() {
        wristWake.stop()
        handler.removeCallbacksAndMessages(null)
        gestureDown?.recycle()
        gestureDown = null
        surface?.destroy?.invoke()
        surface = null
        super.onDestroy()
    }
}

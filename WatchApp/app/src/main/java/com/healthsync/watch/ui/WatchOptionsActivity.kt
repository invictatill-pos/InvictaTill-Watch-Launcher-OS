package com.healthsync.watch.ui

import android.Manifest
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.healthsync.watch.R
import com.healthsync.watch.data.PowerSaveManager
import com.healthsync.watch.data.PowerSaveMode
import com.healthsync.watch.data.WatchPreferences
import com.healthsync.watch.data.InternetSharingPolicy
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.service.WatchInternetSharing
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.notification.NotificationsInboxActivity
import com.healthsync.watch.ui.launcher.AppDrawerActivity
import com.healthsync.watch.ui.launcher.QuickSettingsActivity
import java.util.Locale
import kotlin.math.roundToInt

/** All watch features have a visible route, including on standard Android 8 watches. */
class WatchOptionsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SCREEN = "watch_options_screen"
        const val SCREEN_MENU = "menu"
        const val SCREEN_HEALTH = "health"
        const val SCREEN_HR_DETAILS = "heart_rate_details"
        const val SCREEN_DISPLAY = "display"
        const val SCREEN_SENSORS = "sensors"
        const val SCREEN_PHONE = "phone"
        const val SCREEN_INTERNET = "internet"
        const val SCREEN_PROFILE = "profile"
        const val SCREEN_BEZEL = "bezel"
        const val SCREEN_NOTIFICATIONS = "notifications"
        const val SCREEN_BATTERY = "battery"
        private const val SCREEN_HR_INTERVAL = "hr_interval"
        private const val SCREEN_OXYGEN_INTERVAL = "oxygen_interval"
        private const val ACCENT = 0xFF8CE7C8.toInt()
        private const val MUTED = 0xFFA8B9B1.toInt()
    }

    private lateinit var preferences: WatchPreferences
    private lateinit var content: LinearLayout
    private lateinit var scroll: RoundScrollView
    private var screen = SCREEN_MENU
    private var liveUpdate: (() -> Unit)? = null
    private var pendingOperation: String? = null
    private data class ProfileDraft(val weight: String, val height: String, val goal: String)
    private var profileDraft: ProfileDraft? = null
    private var profileBaseline: ProfileDraft? = null
    private var profileInputs: List<EditText>? = null
    private var profileStatus: TextView? = null
    private data class NumericDraft(val field: Int, val value: String, val replaceInitial: Boolean)
    private var numericDraft: NumericDraft? = null
    private var numericDialog: Dialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val liveTick = object : Runnable {
        override fun run() {
            liveUpdate?.invoke()
            handler.postDelayed(this, 1_000L)
        }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val operation = pendingOperation
        pendingOperation = null
        if (operation != null) performOperation(operation)
        liveUpdate?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = WatchPreferences(this)
        setContentView(R.layout.activity_watch_options)
        setWatchFullscreen(window)
        content = findViewById(R.id.watchOptionsContent)
        scroll = findViewById(R.id.watchOptionsScroll)
        scroll.background = WatchVisualStyle.screenBackground()
        // Keep each control inside the central chord of a round display. The generous
        // end padding lets both first and last controls scroll through its widest point.
        val side = (resources.displayMetrics.widthPixels * 0.15f).roundToInt()
        val end = (resources.displayMetrics.widthPixels * 0.14f).roundToInt().coerceAtLeast(dp(36))
        content.setPadding(side, end, side, end)
        pendingOperation = savedInstanceState?.getString("pending_operation")
        if (savedInstanceState?.getBoolean("profile_has_draft") == true) {
            val weight = savedInstanceState.getString("profile_weight_draft")
            val height = savedInstanceState.getString("profile_height_draft")
            val goal = savedInstanceState.getString("profile_goal_draft")
            if (weight != null && height != null && goal != null) profileDraft = ProfileDraft(weight, height, goal)
        }
        showScreen(savedInstanceState?.getString("screen") ?: intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_MENU)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    SCREEN_MENU -> finish()
                    SCREEN_HR_INTERVAL, SCREEN_OXYGEN_INTERVAL -> showScreen(SCREEN_SENSORS)
                    SCREEN_HR_DETAILS -> showScreen(SCREEN_HEALTH)
                    SCREEN_INTERNET -> showScreen(SCREEN_PHONE)
                    else -> showScreen(SCREEN_MENU)
                }
            }
        })
        val numericField = savedInstanceState?.getInt("numeric_editor_field", -1) ?: -1
        val numericValue = savedInstanceState?.getString("numeric_editor_value")
        if (screen == SCREEN_PROFILE && isSmallWatch() && numericField in 0..2 && numericValue != null) {
            val replaceInitial = savedInstanceState?.getBoolean("numeric_editor_replace", true) ?: true
            numericDraft = NumericDraft(numericField, numericValue, replaceInitial)
            scroll.post {
                if (!isFinishing && !isDestroyed && screen == SCREEN_PROFILE) {
                    showNumericEditor(numericField, numericValue, replaceInitial)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            showScreen(intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_MENU)
        }
    }

    override fun onResume() {
        super.onResume()
        setWatchFullscreen(window)
        numericDialog?.window?.let { setWatchFullscreen(it) }
        handler.removeCallbacks(liveTick)
        handler.post(liveTick)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setWatchFullscreen(window)
    }

    @Suppress("DEPRECATION") // These immersive flags are the Android 8 fullscreen API.
    private fun setWatchFullscreen(targetWindow: Window) {
        targetWindow.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        targetWindow.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onPause() {
        handler.removeCallbacks(liveTick)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        numericDialog?.setOnDismissListener(null)
        numericDialog?.dismiss()
        numericDialog = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureProfileDraft()
        outState.putString("screen", screen)
        outState.putString("pending_operation", pendingOperation)
        outState.putBoolean("profile_has_draft", profileDraft != null)
        profileDraft?.let {
            outState.putString("profile_weight_draft", it.weight)
            outState.putString("profile_height_draft", it.height)
            outState.putString("profile_goal_draft", it.goal)
        }
        numericDraft?.let {
            outState.putInt("numeric_editor_field", it.field)
            outState.putString("numeric_editor_value", it.value)
            outState.putBoolean("numeric_editor_replace", it.replaceInitial)
        }
        super.onSaveInstanceState(outState)
    }

    private fun showScreen(requestedScreen: String) {
        numericDialog?.dismiss()
        numericDraft = null
        captureProfileDraft()
        if (screen == SCREEN_PROFILE) hideKeyboard()
        profileInputs = null
        profileBaseline = null
        profileStatus = null
        liveUpdate = null
        content.removeAllViews()
        screen = requestedScreen
        navigationHeader()
        // A fullscreen Android 8 window ignores ADJUST_RESIZE. PAN keeps the focused
        // number field above the keyboard without changing the circular viewport.
        window.setSoftInputMode((if (requestedScreen == SCREEN_PROFILE && !isSmallWatch())
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        when (requestedScreen) {
            SCREEN_HEALTH -> healthScreen()
            SCREEN_HR_DETAILS -> heartRateDetailsScreen()
            SCREEN_DISPLAY -> displayScreen()
            SCREEN_SENSORS -> sensorScreen()
            SCREEN_PHONE -> phoneScreen()
            SCREEN_INTERNET -> internetScreen()
            SCREEN_PROFILE -> profileScreen()
            SCREEN_BEZEL -> bezelScreen()
            SCREEN_NOTIFICATIONS -> notificationScreen()
            SCREEN_BATTERY -> batteryScreen()
            SCREEN_HR_INTERVAL -> intervalScreen(false)
            SCREEN_OXYGEN_INTERVAL -> intervalScreen(true)
            else -> { screen = SCREEN_MENU; menuScreen() }
        }
        liveUpdate?.invoke()
        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun menuScreen() {
        heading("Watch menu", "On the clock: ↑ Apps · ↓ Controls\n← Notifications · → Health")
        action("All apps") { openShell("apps") }
        action("Quick settings") { openShell("controls") }
        action("Phone notifications") { openShell("notifications") }
        action("Watch tools") { startActivity(Intent(this, com.healthsync.watch.ui.shell.WatchUtilitiesActivity::class.java)) }
        action("Phone controls") { openShell("media") }
        action("Navigation") { startActivity(com.healthsync.watch.ui.navigation.NavigationActivity.intentFor(this)) }
        action("Live phone map") { startActivity(com.healthsync.watch.ui.navigation.PhoneMapActivity.intentFor(this)) }
        action("Home setup & recovery") {
            startActivity(Intent(this, QuickSettingsActivity::class.java)
                .putExtra(QuickSettingsActivity.EXTRA_SCREEN, QuickSettingsActivity.SCREEN_HOME))
        }
        action("Android settings") {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .onFailure { label("Android settings could not open on this watch.") }
        }
        action("Exercises") { startActivity(Intent(this, WorkoutSelectionActivity::class.java)) }
        action("Workout history") { startActivity(Intent(this, WatchHistoryActivity::class.java)) }
        action("Health readings") { showScreen(SCREEN_HEALTH) }
        action("Display & AOD") { showScreen(SCREEN_DISPLAY) }
        action("Battery & Power Saver") { showScreen(SCREEN_BATTERY) }
        action("Software bezel") { showScreen(SCREEN_BEZEL) }
        action("Watch notifications") { showScreen(SCREEN_NOTIFICATIONS) }
        action("Sensor settings") { showScreen(SCREEN_SENSORS) }
        action("Phone connection") { showScreen(SCREEN_PHONE) }
        action("Profile & step goal") { showScreen(SCREEN_PROFILE) }
        action("Back to clock") {
            startActivity(Intent(this, WatchFaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }

    private fun displayScreen() {
        heading("Display & AOD", "Choose your watch face")
        val shortcuts = Switch(this).apply {
            text = "Clock shortcuts"
            textSize = 15f
            setTextColor(Color.WHITE)
            minHeight = dp(56)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panel(0xFF15221D.toInt())
            isChecked = preferences.clockShortcutsEnabled
            setOnCheckedChangeListener { _, enabled -> preferences.clockShortcutsEnabled = enabled }
        }
        content.addView(shortcuts, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        label("Hide the bottom clock buttons for a clean dial. Chrono Ultra and full dials always hide them. Swipe up for Apps, left for Notifications, or hold the clock for face settings. Settings is also in Apps.")
        val aod = Switch(this).apply {
            text = "Always-on display"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(56)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panel(0xFF15221D.toInt())
            isChecked = preferences.alwaysOnDisplayEnabled
            contentDescription = "Always-on display, ${if (isChecked) "on" else "off"}"
            setOnCheckedChangeListener { _, enabled ->
                preferences.alwaysOnDisplayEnabled = enabled
                contentDescription = "Always-on display, ${if (enabled) "on" else "off"}"
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        content.addView(aod, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(9)
        })
        label("When on, the foreground clock stays awake and dims after your chosen delay. Tap to brighten. The power button, lock screen and other apps still control the display. This uses more battery than screen-off.")
        label("AOD clock style", bold = true)
        WatchFaceCatalog.ambientEntries.forEach { entry ->
            val selected = preferences.aodStyle == entry.id
            action(entry.title + if (selected) "  ✓" else "", selected) {
                preferences.aodStyle = entry.id
                showScreen(SCREEN_DISPLAY)
            }.contentDescription = "${entry.title} AOD style${if (selected) ", selected" else ""}"
        }
        label("Dim after", bold = true)
        listOf(15_000L, 30_000L, 60_000L).forEach { delay ->
            val selected = preferences.aodIdleDelayMs == delay
            action("${delay / 1_000} seconds" + if (selected) "  ✓" else "", selected) {
                preferences.aodIdleDelayMs = delay
                showScreen(SCREEN_DISPLAY)
            }
        }
        label("AOD brightness", bold = true)
        listOf(.01f, .03f, .06f, .10f).forEach { brightness ->
            val selected = kotlin.math.abs(preferences.aodBrightness - brightness) < .001f
            action("${(brightness * 100).roundToInt()}%" + if (selected) "  ✓" else "", selected) {
                preferences.aodBrightness = brightness
                showScreen(SCREEN_DISPLAY)
            }
        }
        label("Dim faces use black, minute updates and moving pixels. Higher brightness increases power use.")
        val wristSensor = com.healthsync.watch.ui.shell.WristWakeController(this) {}
        val wrist = Switch(this).apply {
            text = "Raise wrist to brighten"
            textSize = 15f
            setTextColor(Color.WHITE)
            minHeight = dp(56)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panel(0xFF15221D.toInt())
            isEnabled = wristSensor.available
            isChecked = preferences.wristWakeEnabled
            setOnCheckedChangeListener { _, enabled -> preferences.wristWakeEnabled = enabled }
        }
        content.addView(wrist, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        label(if (wristSensor.available) "With AOD on, a wrist raise brightens the foreground dim clock. The sensor rests while the clock is bright. Screen-off wake remains controlled by the watch."
            else "A compatible motion sensor is unavailable on this watch.")
        label("Watch face", bold = true)
        WatchFaceCatalog.entries.forEach { entry ->
            val style = entry.id
            val title = entry.title
            val selected = preferences.watchFaceStyle == style
            action(if (selected) "$title  ✓" else title, selected) {
                preferences.watchFaceStyle = style
                showScreen(SCREEN_DISPLAY)
            }.contentDescription = "$title watch face${if (selected) ", selected" else ""}"
        }
        val wallpaper = Switch(this).apply {
            text = "System wallpaper"
            textSize = 15f
            setTextColor(Color.WHITE)
            minHeight = dp(56)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panel(0xFF15221D.toInt())
            isChecked = preferences.wallpaperBackdropEnabled
            setOnCheckedChangeListener { _, enabled -> preferences.wallpaperBackdropEnabled = enabled }
        }
        content.addView(wallpaper, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(9) })
        label("Show Android's selected wallpaper behind the clock. Classic keeps its own background. The dim clock always uses black. Animated wallpaper may use more battery.")
        action("Choose wallpaper") {
            runCatching { startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }
                .onFailure { label("No wallpaper picker is available. Try Android Settings → Display.") }
        }
        label("Long-press the clock to preview faces. For Dashboard, use Settings → Watch faces.")
        action("Software bezel") { showScreen(SCREEN_BEZEL) }
        backToMenu()
    }

    private fun notificationScreen() {
        heading("Watch notifications", "Phone and watch alerts in your launcher")
        val status = label("")
        action("Allow message notifications") {
            if (Build.VERSION.SDK_INT >= 33 && !allowed(Manifest.permission.POST_NOTIFICATIONS)) {
                pendingOperation = null
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            } else openNotificationChannel(com.healthsync.watch.notification.WatchNotificationAlerts.CHANNEL_ID)
        }
        listOf("Show new message popup" to preferences.watchNotificationPopupEnabled,
            "Wake screen for new alerts" to preferences.watchNotificationWakeEnabled).forEachIndexed { index, setting ->
            val toggle = Switch(this).apply {
                text = setting.first; textSize = 15f; setTextColor(Color.WHITE); minHeight = dp(56)
                setPadding(dp(12), dp(12), dp(12), dp(12)); background = panel(0xFF15221D.toInt())
                isChecked = setting.second
                setOnCheckedChangeListener { _, enabled ->
                    if (index == 0) preferences.watchNotificationPopupEnabled = enabled else preferences.watchNotificationWakeEnabled = enabled
                }
            }
            content.addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        label("New messages can wake compatible watches and open a popup. Your lock screen, Do Not Disturb, and Android alert settings are respected. Newer watches may show a banner; tap it to read. Reconnecting refreshes your inbox quietly.")
        action("Message sound & vibration") { openNotificationChannel(com.healthsync.watch.notification.WatchNotificationAlerts.CHANNEL_ID) }
        action("Incoming call alerts") { openNotificationChannel(com.healthsync.watch.ui.calls.WatchCallAlerts.CHANNEL_ID) }
        if (Build.VERSION.SDK_INT >= 34) action("Allow incoming call screen") {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }
                .onFailure { label("Incoming call screen settings are unavailable on this watch.") }
        }
        action("Allow watch notification access") {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .onFailure { label("Notification access settings are unavailable on this watch.") }
        }
        val takeover = Switch(this).apply {
            text = "Manage in launcher"
            textSize = 15f
            setTextColor(Color.WHITE)
            minHeight = dp(56)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panel(0xFF15221D.toInt())
            isChecked = preferences.watchNotificationTakeoverEnabled
            setOnCheckedChangeListener { _, enabled ->
                preferences.watchNotificationTakeoverEnabled = enabled
                com.healthsync.watch.notification.LocalNotificationListenerService.refreshTakeover(this@WatchOptionsActivity)
                liveUpdate?.invoke()
            }
        }
        content.addView(takeover, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        label("Moves supported clearable watch alerts into the launcher after saving them. Phone messages also appear here. Android keeps calls, ongoing, protected and action-sensitive prompts available.")
        label("Bluetooth transfer prompts are included when Android exposes them as notifications. Tap their available actions in the panel. Android permission dialogs and secure system screens still belong to Android.")
        label("Clear all removes saved phone copies and dismissible watch alerts. Active ongoing watch alerts remain. Clearing phone copies here leaves the original phone notification available.")
        action("Open notification panel") { openShell("notifications") }
        liveUpdate = {
            val allowed = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
            val messageAlerts = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
            status.text = (if (messageAlerts) "Message alerts on\n" else "Message alerts off · allow above\n") + when {
                !allowed -> "Watch access off · allow access above"
                preferences.watchNotificationTakeoverEnabled -> "Watch access on · launcher management enabled"
                else -> "Watch access on · copy mode"
            }
        }
        backToMenu()
    }

    private fun openNotificationChannel(channel: String) {
        com.healthsync.watch.notification.WatchNotificationAlerts.createChannel(this)
        com.healthsync.watch.ui.calls.WatchCallAlerts.createChannel(this)
        val settings = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channel)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(settings) }.onFailure { label("Notification settings could not open on this watch.") }
    }

    private fun bezelScreen() {
        heading("Software bezel", "A round window over other apps")
        val status = label("")
        val enabled = Switch(this).apply {
            text = "Round bezel over apps"
            textSize = 15f
            setTextColor(Color.WHITE)
            minHeight = dp(56)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panel(0xFF15221D.toInt())
            isChecked = preferences.bezelEnabled
            setOnCheckedChangeListener { _, checked ->
                preferences.bezelEnabled = checked
                if (checked && !Settings.canDrawOverlays(this@WatchOptionsActivity)) requestOverlayPermission()
                com.healthsync.watch.service.BezelOverlayService.reconcile(this@WatchOptionsActivity)
                liveUpdate?.invoke()
            }
        }
        content.addView(enabled, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        action("Allow display over other apps") { requestOverlayPermission() }
        label("The center stays transparent and touches pass through. The bezel hides the corners; it does not resize apps. Controls drawn outside the circle may be hidden. Turn it off here or with Turn off in the Software bezel service notification.")
        label(if (Build.VERSION.SDK_INT >= 31)
            "Android 12 and newer require a translucent mask so touches reach other apps. Protected screens can hide overlays. Android keeps a service indicator while this runs."
            else "The mask is black on this Android version. Android keeps a service notification while this runs. Protected screens can hide overlays.")
        label("Circle diameter", bold = true)
        listOf(100, 95, 90, 85).forEach { percent ->
            val selected = preferences.bezelDiameterPercent == percent
            action("$percent%" + if (selected) "  ✓" else "", selected) {
                preferences.bezelDiameterPercent = percent
                com.healthsync.watch.service.BezelOverlayService.reconcile(this)
                showScreen(SCREEN_BEZEL)
            }
        }
        liveUpdate = {
            val allowed = Settings.canDrawOverlays(this)
            status.text = when {
                !preferences.bezelEnabled -> "Bezel off"
                !allowed -> "Waiting for display-over-apps permission"
                com.healthsync.watch.service.BezelOverlayService.isRunning -> "Bezel active"
                else -> "Bezel unavailable · reopen or enable again"
            }
        }
        backToMenu()
    }

    private fun requestOverlayPermission() {
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
            .onFailure { label("Overlay permission settings are unavailable on this watch.") }
    }

    private fun openShell(panel: String) {
        startActivity(Intent(this, WatchFaceActivity::class.java).putExtra(WatchFaceActivity.EXTRA_PANEL, panel)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun healthScreen() {
        heading("Health readings", "Wear the watch snugly")
        val heart = label("Heart rate", 18f, bold = true)
        val heartStatus = label("")
        val heartButton = action("Measure heart rate") { requestOperation("measure_hr") }
        action("Heart rate details") { showScreen(SCREEN_HR_DETAILS) }
        action("Check watch heart rate data") { SensorCollectorService.checkHealthAppHeartRate(this) }
        val oxygen = label("Blood oxygen", 18f, bold = true)
        val oxygenStatus = label("")
        val oxygenButton = action("Measure oxygen") { requestOperation("measure_oxygen") }
        action("Check watch oxygen data") { SensorCollectorService.checkHealthAppOxygen(this) }
        action("Open watch health app") { showHealthApps(this) }
        val steps = label("Steps today", 18f, bold = true)
        val stepsStatus=label("",11f)
        val battery = label("Battery", 18f, bold = true)
        liveUpdate = {
            val hasBodyPermission = allowed(Manifest.permission.BODY_SENSORS)
            heart.text = "Heart rate\n${SensorCollectorService.heartRateValueText()}"
            heartStatus.text = SensorCollectorService.heartRateStatusText(this)
            // Older Android firmware can hide health sensors until permission is granted.
            // Keep this action available so an empty inventory cannot block permission or a retry.
            heartButton.text = if (hasBodyPermission) "Measure heart rate" else "Allow sensor access"
            val heartMeasuring = SensorCollectorService.hrStatus in setOf("measuring", "checking_health_apps")
            if (heartMeasuring) heartButton.text = if (SensorCollectorService.hrStatus == "checking_health_apps") "Checking watch apps…" else "Measuring…"
            heartButton.isEnabled = !heartMeasuring
            heartButton.contentDescription = heartButton.text
            val spo2 = SensorCollectorService.watchOxygenReading().percentage.takeIf { it > 0f }
            oxygen.text = spo2?.let { "Blood oxygen\n${String.format(Locale.getDefault(), "%.0f", it)}%" }
                ?: "Blood oxygen\n—%"
            oxygenStatus.text = SensorCollectorService.oxygenStatusText(this)
            val oxygenMeasuring = SensorCollectorService.spo2Status in setOf("measuring", "checking_health_apps", "unreliable_accuracy")
            oxygenButton.text = if (!hasBodyPermission) "Allow sensor access" else if (oxygenMeasuring) "Measuring oxygen…" else "Measure oxygen"
            oxygenButton.isEnabled = !oxygenMeasuring
            oxygenButton.contentDescription = oxygenButton.text
            steps.text = "Steps today\n" + if(SensorCollectorService.hasCapturedStepsToday()) java.text.NumberFormat.getIntegerInstance().format(SensorCollectorService.latestSteps.coerceAtLeast(0)) else "—"
            stepsStatus.text=SensorCollectorService.stepStatusText(this)
            val batteryLevel = batteryPercentage()
            battery.text = if (batteryLevel in 0..100) "Battery\n$batteryLevel%" else "Battery\nUnavailable"
        }
        action("App permissions") { openAppPermissions() }
        backToMenu()
        startHealthServices()
    }

    private fun heartRateDetailsScreen() {
        heading("Heart rate details", "Measure first, then copy the report")
        val sensorValue = label("", 18f, bold = true)
        val status = label("")
        action("Measure heart rate") { requestOperation("measure_hr") }
        action("Check watch app data") { SensorCollectorService.checkHealthAppHeartRate(this) }
        action("Open watch health app") { showHealthApps(this) }
        action("Copy heart rate report") {
            runCatching {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: error("Clipboard unavailable")
                clipboard.setPrimaryClip(ClipData.newPlainText("HealthSync heart rate report",
                    SensorCollectorService.heartRateDiagnosticReport(this)))
            }.onSuccess { Toast.makeText(this, "Heart rate report copied.", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this, "The clipboard is unavailable on this watch.", Toast.LENGTH_SHORT).show() }
        }
        action("App permissions") { openAppPermissions() }
        action("Back to health readings") { showScreen(SCREEN_HEALTH) }
        val report = label("", 12f).apply {
            gravity = Gravity.START
        }
        liveUpdate = {
            sensorValue.text = SensorCollectorService.heartRateValueText()
            status.text = SensorCollectorService.heartRateStatusText(this, measurementDetails = true)
            report.text = SensorCollectorService.heartRateDiagnosticReport(this)
        }
    }

    private fun sensorScreen() {
        heading("Sensor settings", "Automatic readings")
        val foregroundAccess=label("")
        var backgroundAccess:TextView?=null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backgroundAccess=label("")
            label("To capture while this app is in the background, open App permissions, select Sensors, then allow all the time if your watch offers it. This is a separate choice from foreground access.")
        }
        liveUpdate={
            foregroundAccess.text="Foreground sensor access: ${if (allowed(Manifest.permission.BODY_SENSORS)) "Allowed" else "Not allowed"}"
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
                backgroundAccess?.text="Background sensor access: ${if (allowed(Manifest.permission.BODY_SENSORS_BACKGROUND)) "Allowed" else "Not allowed"}"
        }
        action("Heart rate\n${intervalLabel(preferences.hrIntervalMs)}") { showScreen(SCREEN_HR_INTERVAL) }
        action("Blood oxygen\n${intervalLabel(preferences.spo2IntervalMs)}") { showScreen(SCREEN_OXYGEN_INTERVAL) }
        label("Heart rate allows up to 60 seconds for wrist contact and stops after a reliable sample. Oxygen lasts up to 30 seconds. Longer intervals use less battery. Off disables automatic readings; manual measurement remains available.")
        if (allowed(Manifest.permission.BODY_SENSORS) && !hasOxygenSensor()) {
            label("Automatic oxygen readings require a compatible sensor exposed by the watch.")
        }
        action("Sensor permissions") { requestOperation("sensor_permissions") }
        action("App permissions") { openAppPermissions() }
        label("If a permission was permanently denied, open App permissions to allow it again.")
        backToMenu()
    }

    private fun intervalScreen(oxygen: Boolean) {
        heading(if (oxygen) "Oxygen interval" else "Heart rate interval", "Choose how often to measure")
        val current = if (oxygen) preferences.spo2IntervalMs else preferences.hrIntervalMs
        listOf(
            WatchPreferences.INTERVAL_30_SEC, WatchPreferences.INTERVAL_1_MIN,
            WatchPreferences.INTERVAL_5_MIN, WatchPreferences.INTERVAL_10_MIN,
            WatchPreferences.INTERVAL_30_MIN, WatchPreferences.INTERVAL_1_HOUR,
            WatchPreferences.INTERVAL_OFF
        ).forEach { interval ->
            action(intervalLabel(interval) + if (interval == current) "  ✓" else "", interval == current) {
                if (oxygen) preferences.spo2IntervalMs = interval else preferences.hrIntervalMs = interval
                SensorCollectorService.notifySettingsUpdated(this)
                showScreen(SCREEN_SENSORS)
            }
        }
        action("Back to sensor settings") { showScreen(SCREEN_SENSORS) }
    }

    private fun phoneScreen() {
        heading("Phone connection", "Bluetooth sync")
        val connection = label("", 18f, bold = true)
        liveUpdate = { connection.text = if (BluetoothClientService.isConnected) "Phone connected" else "Phone offline" }
        label("Pair this watch and your phone in Bluetooth settings. Keep HealthSync open on your phone while reconnecting. Saved workouts sync when the connection returns.")
        action("Reconnect / Bluetooth") { requestOperation("reconnect") }
        action("Navigation") { startActivity(com.healthsync.watch.ui.navigation.NavigationActivity.intentFor(this)) }
        label("Enable Watch navigation in HealthSync on your phone and allow notification access. Start a Google Maps route on the phone, then open Navigation here for large turn directions and distance.")
        action("Live phone map") { startActivity(com.healthsync.watch.ui.navigation.PhoneMapActivity.intentFor(this)) }
        label("Live phone map is an optional phone screen viewer. Sharing the screen needs your approval each time.")
        action("Internet from phone") { showScreen(SCREEN_INTERNET) }
        label("For answer, decline, mute and end controls, open HealthSync Settings → Watch Calls on your phone and select Enable watch call controls. Grant Contacts to show saved caller names.")
        label("To talk through the watch, enable Calls for this watch in your phone's Bluetooth device settings. During a call, Use watch audio becomes available only when Android detects its call connection.")
        action("Call & message alerts") { showScreen(SCREEN_NOTIFICATIONS) }
        backToMenu()
    }

    private fun internetScreen() {
        heading("Internet from phone", "Use your existing Bluetooth pairing")
        val internet = label("", 17f, bold = true)
        val sync = label("", 12f)
        liveUpdate = {
            val status = WatchInternetSharing.readStatus(this)
            internet.text = InternetSharingPolicy.statusText(status)
            internet.setTextColor(if (InternetSharingPolicy.isBluetoothInternet(status)) ACCENT else Color.WHITE)
            sync.text = if (BluetoothClientService.isConnected) "HealthSync sync: connected" else "HealthSync sync: offline"
        }
        label("Share the phone's internet with watch apps. Internet sharing and HealthSync sync have separate status.")
        label("1. On your phone, open Settings → Hotspot & tethering (or Mobile Hotspot and Tethering), then turn on Bluetooth tethering.")
        label("2. On this watch, open Bluetooth settings, choose your paired phone, then enable Internet access if available.")
        action("Watch Bluetooth settings") { openInternetSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }
        label("3. Return here to check the watch's default connection. Internet via Bluetooth confirms Android has verified that connection. Wi-Fi or mobile data status means the watch is using that network.")
        label("If Internet access is missing, the watch firmware may not support Bluetooth internet. You can connect the watch to your phone's Wi-Fi hotspot instead.")
        action("Watch Wi-Fi settings") { openInternetSettings(Settings.ACTION_WIFI_SETTINGS) }
        label("Keep the devices nearby. The same pairing can be used for sync and internet when both devices support it.")
        action("Back to phone connection") { showScreen(SCREEN_PHONE) }
    }

    private fun openInternetSettings(settingsAction: String) {
        val opened = runCatching { startActivity(Intent(settingsAction)); true }.getOrDefault(false)
        if (!opened) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .onFailure { Toast.makeText(this, "Android settings are unavailable on this watch.", Toast.LENGTH_LONG).show() }
        }
    }

    private fun profileScreen() {
        heading("Profile & goal", "Improve activity estimates")
        val profilePreferences = getSharedPreferences("watch_sync_prefs", MODE_PRIVATE)
        val weightConfigured=profilePreferences.getBoolean("profile_weight_configured",false)
        val heightConfigured=profilePreferences.getBoolean("profile_height_configured",false)
        val profileAssumptions = label(if (!weightConfigured || !heightConfigured)
            "Unset profile entries use calculation defaults: 70 kg weight and 170 cm height. These are assumptions, not measurements."
            else "Weight and height are saved profile entries.", 11f)
        val savedProfile = ProfileDraft(if(weightConfigured) String.format(Locale.US, "%.1f", preferences.userWeightKg) else "",
            if(heightConfigured) preferences.userHeightCm.toString() else "", preferences.stepGoal.toString())
        val values = profileDraft ?: savedProfile
        profileBaseline = savedProfile
        label("Weight (kg)", bold = true)
        val weight = input(values.weight, decimal = true)
        weight.hint="Enter weight"
        weight.contentDescription = "Weight in kilograms, from 30 to 250"
        label("Height (cm)", bold = true)
        val height = input(values.height)
        height.hint="Enter height"
        height.contentDescription = "Height in centimetres, from 100 to 230"
        label("Daily step goal", bold = true)
        val goal = input(values.goal)
        goal.contentDescription = "Daily step goal, from 1 to 100000"
        profileInputs = listOf(weight, height, goal)
        weight.imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        height.imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        goal.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        weight.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_NEXT) { height.requestFocus(); true } else false
        }
        height.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_NEXT) { goal.requestFocus(); true } else false
        }
        goal.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { hideKeyboard(); true } else false
        }
        if (isSmallWatch()) {
            listOf(weight, height, goal).forEachIndexed { index, field ->
                field.showSoftInputOnFocus = false
                field.keyListener = null
                field.isCursorVisible = false
                field.isFocusable = true
                field.isFocusableInTouchMode = false
                field.contentDescription = "${field.contentDescription}. Tap to edit using the watch keypad"
                field.setOnClickListener { showNumericEditor(index) }
                field.setOnLongClickListener { showNumericEditor(index); true }
            }
        }
        val status = label("")
        profileStatus = status
        label(if (isSmallWatch()) "Tap a number to edit. Swipe the keypad for more keys. Apply changes the field; Save profile saves all values."
            else "Use Next between fields and Done to close the keyboard.")
        action("Save profile") {
            val weightValue = weight.text.toString().trim().replace(',', '.').toDoubleOrNull()
            val heightValue = height.text.toString().trim().toIntOrNull()
            val goalValue = goal.text.toString().trim().toIntOrNull()
            status.text = when {
                weightValue == null || !weightValue.isFinite() || weightValue !in 30.0..250.0 -> "Enter a weight from 30 to 250 kg."
                heightValue == null || heightValue !in 100..230 -> "Enter a height from 100 to 230 cm."
                goalValue == null || goalValue !in 1..100_000 -> "Enter a step goal from 1 to 100,000."
                else -> {
                    preferences.userWeightKg = weightValue
                    preferences.userHeightCm = heightValue
                    preferences.stepGoal = goalValue
                    profilePreferences.edit().putBoolean("profile_weight_configured",true).putBoolean("profile_height_configured",true).apply()
                    profileAssumptions.text = "Weight and height are saved profile entries."
                    profileBaseline = ProfileDraft(weight.text.toString(), height.text.toString(), goal.text.toString())
                    profileDraft = null
                    hideKeyboard()
                    "Profile saved"
                }
            }
            status.setTextColor(if (status.text == "Profile saved") ACCENT else 0xFFFFBC6A.toInt())
            status.announceForAccessibility(status.text)
        }
        action("Discard edits") {
            profileDraft = null
            profileInputs = null
            profileBaseline = null
            showScreen(SCREEN_PROFILE)
        }
        label("Calories use activity, duration and profile weight estimates. Height helps estimate distance when location is unavailable.",11f)
        label("Phone settings apply again when connected.")
        backToMenu()
    }

    /** Draft text is separate from preferences; only the validated Save action persists it. */
    private fun captureProfileDraft() {
        val fields = profileInputs ?: return
        if (fields.size != 3) return
        val values = ProfileDraft(fields[0].text.toString(), fields[1].text.toString(), fields[2].text.toString())
        profileDraft = values.takeIf { it != profileBaseline }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        currentFocus?.clearFocus()
        scroll.requestFocus()
    }

    private fun isSmallWatch(): Boolean {
        val metrics = resources.displayMetrics
        return minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density < 280f
    }

    /** Small watch keyboards can cover the whole display. This editor never starts an IME. */
    private fun showNumericEditor(fieldIndex: Int, restoredValue: String? = null, replaceInitial: Boolean = true) {
        val field = profileInputs?.getOrNull(fieldIndex) ?: return
        hideKeyboard()
        numericDialog?.dismiss()
        val decimal = fieldIndex == 0
        val title = listOf("Weight (kg)", "Height (cm)", "Step goal")[fieldIndex]
        numericDraft = NumericDraft(fieldIndex, restoredValue ?: field.text.toString(), replaceInitial)
        val dialog = object : Dialog(this) {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                super.onWindowFocusChanged(hasFocus)
                if (hasFocus) window?.let { setWatchFullscreen(it) }
            }
        }
        numericDialog = dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val titleView = TextView(this).apply {
            text = title
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(MUTED)
        }
        val valueView = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            isSingleLine = true
            setPadding(dp(3), 0, dp(3), 0)
            background = panel(0xFF15221D.toInt())
        }
        fun refreshValue() {
            val entry = numericDraft ?: return
            valueView.text = entry.value.ifEmpty { "—" }
            valueView.setTextColor(if (entry.replaceInitial) ACCENT else Color.WHITE)
            valueView.contentDescription = "$title, ${entry.value.ifEmpty { "empty" }}${if (entry.replaceInitial) ", digits replace this value" else ""}"
        }
        val keys = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val keysScroll = RoundScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            contentDescription = "Number keypad. Swipe for more keys"
            addView(keys, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun keyButton(text: String, size: Float = 18f, click: () -> Unit): Button = Button(this).apply {
            this.text = text
            textSize = size
            isAllCaps = false
            gravity = Gravity.CENTER
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(1), 0, dp(1), 0)
            setTextColor(Color.WHITE)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), panel(0xFF254138.toInt()))
                addState(intArrayOf(), panel(0xFF15221D.toInt()))
            }
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); click() }
        }
        listOf(listOf("1", "2", "3", "DEL"),
            listOf("4", "5", "6", if (decimal) "." else "CLR"),
            listOf("7", "8", "9", "0")).forEach { values ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            values.forEach { key ->
                val button = keyButton(key, if (key.length > 1) 10f else 18f) {
                    val entry = numericDraft ?: return@keyButton
                    val value = if (entry.replaceInitial) "" else entry.value
                    val next = when (key) {
                        "DEL" -> value.dropLast(1)
                        "CLR" -> ""
                        "." -> if (value.contains('.')) value else if (value.isEmpty()) "0." else "$value."
                        else -> if (value.length < 10) value + key else value
                    }
                    numericDraft = NumericDraft(fieldIndex, next, false)
                    refreshValue()
                }
                button.contentDescription = when (key) { "DEL" -> "Delete digit"; "CLR" -> "Clear number"; "." -> "Decimal point"; else -> key }
                row.addView(button, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    marginStart = dp(1); marginEnd = dp(1)
                })
            }
            keys.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            })
        }
        val apply = keyButton("Apply", 12f) {
            numericDraft?.let { field.setText(it.value); captureProfileDraft(); profileStatus?.text = "" }
            dialog.dismiss()
        }.apply { setTextColor(ACCENT); contentDescription = "Apply number to profile field" }
        val cancel = keyButton("Cancel", 12f) { dialog.dismiss() }.apply { contentDescription = "Cancel number edit" }
        footer.addView(apply, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(2) })
        footer.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(2) })
        listOf(titleView, valueView, keysScroll, footer).forEach { root.addView(it) }
        var stableBottomInset = 0
        // Floating dialog decor can consume the insets before content receives them.
        // Keep the footer above navigation even when those reported insets are zero.
        val navigationHeightId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navigationBottomReserve = if (navigationHeightId != 0)
            resources.getDimensionPixelSize(navigationHeightId) else dp(48)
        fun layoutKeypad() {
            // Reserve the navigation bar's stable area even when immersive flags hide it.
            // System UI can reveal it after restoration without moving these controls.
            val availableHeight = (root.height - maxOf(stableBottomInset, navigationBottomReserve)).coerceAtLeast(0)
            val diameter = minOf(root.width, availableHeight)
            if (diameter > 0) {
                val left = (root.width - diameter) / 2
                val top = (availableHeight - diameter) / 2
                fun place(view: View, widthFraction: Float, topFraction: Float, height: Int) {
                    val width = (diameter * widthFraction).roundToInt()
                    val x = left + (diameter - width) / 2
                    val y = top + (diameter * topFraction).roundToInt()
                    val current = view.layoutParams as? FrameLayout.LayoutParams
                    if (current == null || current.width != width || current.height != height || current.leftMargin != x || current.topMargin != y) {
                        view.layoutParams = FrameLayout.LayoutParams(width, height).apply { leftMargin = x; topMargin = y }
                    }
                }
                place(titleView, 0.70f, 0.12f, (diameter * 0.07f).roundToInt())
                place(valueView, 0.70f, 0.20f, (diameter * 0.13f).roundToInt())
                place(keysScroll, 0.76f, 0.36f, (diameter * 0.37f).roundToInt())
                place(footer, 0.48f, 0.78f, dp(34))
            }
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutKeypad() }
        root.setOnApplyWindowInsetsListener { _, insets ->
            stableBottomInset = insets.stableInsetBottom.coerceAtLeast(0)
            layoutKeypad()
            insets
        }
        dialog.setContentView(root)
        dialog.setOnDismissListener {
            if (numericDialog === dialog) { numericDialog = null; numericDraft = null }
        }
        refreshValue()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            setWatchFullscreen(this)
        }
        root.requestApplyInsets()
    }

    private fun requestOperation(operation: String) {
        if (pendingOperation != null) return
        val needed = mutableListOf<String>()
        fun add(permission: String) { if (!allowed(permission)) needed += permission }
        if (operation != "reconnect") {
            add(Manifest.permission.BODY_SENSORS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isEmpty()) performOperation(operation) else {
            pendingOperation = operation
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun performOperation(operation: String) {
        startHealthServices()
        when (operation) {
            "measure_hr" -> SensorCollectorService.forceMeasureHr(this)
            "measure_oxygen" -> SensorCollectorService.forceMeasureSpO2(this)
            "sensor_permissions" -> {
                SensorCollectorService.notifySettingsUpdated(this)
                showScreen(SCREEN_SENSORS)
            }
            "reconnect" -> runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .onFailure { label("Bluetooth settings could not open on this watch.") }
        }
    }

    private fun startHealthServices() {
        listOf(BluetoothClientService::class.java, SensorCollectorService::class.java).forEach { service ->
            runCatching { ContextCompat.startForegroundService(this, Intent(this, service)) }
                .onFailure { android.util.Log.w("WatchOptions", "Could not start ${service.simpleName}", it) }
        }
    }

    private fun openAppPermissions() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }.onFailure { label("App settings could not open. Open Android Settings, choose Apps, then HealthSync Watch and Permissions.") }
    }

    private fun hasOxygenSensor() = runCatching {
        (getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getSensorList(Sensor.TYPE_ALL)?.any { sensor ->
            com.healthsync.watch.sensor.OxygenCapturePolicy.isOxygenSensor(sensor.type, sensor.name, sensor.stringType)
        } == true
    }.getOrDefault(false)

    private fun batteryPercentage() = runCatching {
        (getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }.getOrDefault(-1)

    private fun allowed(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun intervalLabel(interval: Long) = when (interval) {
        WatchPreferences.INTERVAL_OFF -> "Off"
        WatchPreferences.INTERVAL_30_SEC -> "Every 30 seconds"
        WatchPreferences.INTERVAL_1_MIN -> "Every minute"
        WatchPreferences.INTERVAL_1_HOUR -> "Every hour"
        else -> "Every ${interval / 60_000L} minutes"
    }

    private fun heading(title: String, subtitle: String) {
        label(title, 23f, bold = true).setTextColor(WatchVisualStyle.TEXT)
        label(subtitle)
    }

    private fun navigationHeader() {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        fun button(title:String,click:()->Unit)=Button(this).apply {
            text=title; isAllCaps=false; textSize=12f; setTextColor(WatchVisualStyle.MUTED)
            minWidth=0; minimumWidth=0; minHeight=dp(44); minimumHeight=dp(44)
            setPadding(dp(4),0,dp(4),0); backgroundTintList=null; background=WatchVisualStyle.button(this@WatchOptionsActivity)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); click() }
        }
        row.addView(button("‹ Back") { onBackPressedDispatcher.onBackPressed() },LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(4) })
        row.addView(button("Clock") { openShell("clock") },LinearLayout.LayoutParams(0,dp(44),1f).apply { marginStart=dp(4) })
        content.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })
    }

    private fun label(value: String, size: Float = 13f, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(if (bold) Color.WHITE else MUTED)
        gravity = Gravity.CENTER
        typeface = Typeface.create(if(bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding=false
        setLineSpacing(dp(2).toFloat(),1f)
        setPadding(dp(2), dp(5), dp(2), dp(8))
        content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })
    }

    private fun action(title: String, selected: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
        text = title
        contentDescription = title
        textSize = 15f
        isAllCaps = false
        gravity = Gravity.CENTER
        minHeight = dp(56)
        minimumHeight = dp(56)
        minWidth=0; minimumWidth=0
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        backgroundTintList=null
        setPadding(dp(10), dp(12), dp(10), dp(12))
        setTextColor(if (selected) ACCENT else Color.WHITE)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), panel(0xFF254138.toInt()))
            addState(intArrayOf(), panel(if (selected) 0xFF173027.toInt() else 0xFF15221D.toInt()))
        }
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
        content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(9)
        })
    }

    private fun input(value: String, decimal: Boolean = false): EditText = EditText(this).apply {
        setText(value)
        textSize = 18f
        gravity = Gravity.CENTER
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
        setTextColor(Color.WHITE)
        setPadding(dp(8), dp(12), dp(8), dp(12))
        background = panel(0xFF15221D.toInt())
        setSelectAllOnFocus(true)
        content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(10) })
    }

    private fun batteryScreen() {
        heading("Battery & Power", "Manage stamina & power modes")

        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: -1
        val currentMode = preferences.powerSaveMode

        val batHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panel(0xFF14241E.toInt())
        }
        val batLevelText = if (batteryPct >= 0) "$batteryPct%" else "Battery"
        batHeader.addView(TextView(this).apply {
            text = "🔋 $batLevelText"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        batHeader.addView(TextView(this).apply {
            text = PowerSaveManager.getModeBadge(currentMode)
            textSize = 10.5f
            setTextColor(ACCENT)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
        content.addView(batHeader, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })

        // Mode 1: Normal Mode
        val isNormal = currentMode == PowerSaveMode.NORMAL
        val normalHours = if (batteryPct > 0) "~${PowerSaveManager.getEstimatedHours(batteryPct, PowerSaveMode.NORMAL)} hrs" else "Standard"
        action("1. Normal Mode" + if (isNormal) "  ✓" else "", isNormal) {
            if (!isNormal) {
                PowerSaveManager.applyMode(this, PowerSaveMode.NORMAL)
                Toast.makeText(this, "Normal Mode Active", Toast.LENGTH_SHORT).show()
                showScreen(SCREEN_BATTERY)
            }
        }
        label("Full 60 FPS animations, regular optical sensors & companion sync · Est: $normalHours")

        // Mode 2: Extreme Battery Saver (Stamina) - Keeps communication alive
        val isExtreme = currentMode == PowerSaveMode.EXTREME
        val extremeHours = if (batteryPct > 0) "~${PowerSaveManager.getEstimatedHours(batteryPct, PowerSaveMode.EXTREME)} hrs (3x)" else "3x Longevity"
        action("2. Extreme Saver" + if (isExtreme) "  ✓" else "", isExtreme) {
            if (!isExtreme) {
                PowerSaveManager.applyMode(this, PowerSaveMode.EXTREME)
                Toast.makeText(this, "Extreme Saver Active · Phone Connected", Toast.LENGTH_SHORT).show()
                showScreen(SCREEN_BATTERY)
            }
        }
        label("Extreme battery saving. Pauses animations & optical sensors. Phone communication KEPT ALIVE (Calls & Notifications active) · Est: $extremeHours", bold = isExtreme)

        // Mode 3: Super Extreme Saver (Emergency) - Stops communication
        val isSuper = currentMode == PowerSaveMode.SUPER_EXTREME
        val superHours = if (batteryPct > 0) "~${PowerSaveManager.getEstimatedHours(batteryPct, PowerSaveMode.SUPER_EXTREME)} hrs (8+ Days)" else "Max Standby"
        action("3. Super Extreme" + if (isSuper) "  ✓" else "", isSuper) {
            if (!isSuper) {
                confirmSuperExtremeMode()
            }
        }
        label("Emergency stamina. Communication with phone app is STOPPED (Bluetooth radio off). Emergency clock only. Tap to exit anytime · Est: $superHours", bold = isSuper)

        backToMenu()
    }

    private fun confirmSuperExtremeMode() {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val side = dp(16)
            setPadding(side, side, side, side)
            background = panel(0xFF1F1610.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "⚡ Enter Super Extreme Saver?"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = "Phone communication and background sensors will stop to save maximum battery. You can exit anytime from the emergency clock."
            textSize = 11.5f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
        })
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnRow.addView(Button(this).apply {
            text = "Cancel"
            textSize = 12f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = panel(0xFF2B2B2B.toInt())
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        btnRow.addView(Button(this).apply {
            text = "Enable"
            textSize = 12f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(0xFFB45309.toInt())
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                dialog.dismiss()
                PowerSaveManager.applyMode(this@WatchOptionsActivity, PowerSaveMode.SUPER_EXTREME)
                Toast.makeText(this@WatchOptionsActivity, "Super Extreme Saver Enabled", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@WatchOptionsActivity, WatchFaceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        layout.addView(btnRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dialog.setContentView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.show()
    }

    private fun panel(color: Int) = WatchVisualStyle.surface(this,color)

    private fun backToMenu() = action("Back to menu") { showScreen(SCREEN_MENU) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

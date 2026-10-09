package com.healthsync.watch.ui.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.ui.WatchOptionsActivity
import com.healthsync.watch.ui.shell.QuickControlsPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Foreground controls for ordinary Android watches, using the firmware's settings screens. */
class QuickSettingsActivity : LauncherPanelActivity() {
    companion object {
        const val EXTRA_SCREEN = "launcher_quick_screen"
        const val SCREEN_HOME = "home"
        const val SCREEN_DEVICE = "device"
        private const val SCREEN_MAIN = "main"
        private const val SCREEN_PICK_HOME = "pick_home"
    }

    private data class HomeApp(val component: ComponentName, val label: String)
    private data class HomeInfo(val status: String, val isHealthSyncHome: Boolean, val alternatives: List<HomeApp>)

    private lateinit var content: LinearLayout
    private var screen = SCREEN_MAIN
    private var homeInfo: HomeInfo? = null
    private var homeStatus: TextView? = null
    private var homeJob: Job? = null
    private var deviceJob: Job? = null
    private var deviceText: TextView? = null
    private var deviceReport: String? = null
    private var compactPanel: QuickControlsPanel? = null
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshHomeInfo()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(savedInstanceState?.getString("quick_screen") ?: intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_MAIN)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (compactPanel?.handleBack() == true) return
                when (screen) {
                    SCREEN_PICK_HOME -> showScreen(SCREEN_HOME)
                    SCREEN_HOME, SCREEN_DEVICE -> showScreen(SCREEN_MAIN)
                    else -> finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        compactPanel?.onResume()
        refreshHomeInfo()
        if (screen == SCREEN_DEVICE) refreshDeviceInfo()
    }

    override fun onPause() {
        compactPanel?.onPause()
        homeJob?.cancel()
        deviceJob?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        compactPanel?.destroy()
        compactPanel = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("quick_screen", screen)
        super.onSaveInstanceState(outState)
    }

    private fun showScreen(requested: String) {
        compactPanel?.destroy()
        compactPanel = null
        homeStatus = null
        deviceText = null
        deviceJob?.cancel()
        screen = when (requested) {
            SCREEN_HOME, SCREEN_PICK_HOME, SCREEN_DEVICE -> requested
            else -> SCREEN_MAIN
        }
        content = roundContent()
        when (screen) {
            SCREEN_HOME -> homeScreen()
            SCREEN_PICK_HOME -> homePickerScreen()
            SCREEN_DEVICE -> deviceScreen()
            else -> compactQuickScreen()
        }
    }

    private fun compactQuickScreen() {
        val panel = QuickControlsPanel(this,
            onClose = { backToClock() },
            onSettings = { launchFirst(listOf(Intent(this, WatchOptionsActivity::class.java)), "Watch settings could not open.") },
            onUtilities = {
                launchFirst(listOf(Intent().setClassName(this, "com.healthsync.watch.ui.shell.WatchUtilitiesActivity")),
                    "Watch tools could not open.")
            })
        compactPanel = panel
        setContentView(panel.view)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) panel.onResume()
    }

    private fun homeScreen() {
        heading(content, "Home app", "Choose your watch launcher")
        homeStatus = label(content, homeInfo?.status ?: "Checking default Home…", 14f, accent, bold = true)
        action(content, "Use HealthSync as Home") { requestHealthSyncHome() }
        action(content, "Choose / revert default Home") { openHomeSettings() }
        action(content, "Open another Home app") {
            val homes = homeInfo?.alternatives
            when {
                homes == null -> {
                    showScreen(SCREEN_PICK_HOME)
                    refreshHomeInfo()
                }
                homes.size == 1 -> openHome(homes.first())
                else -> showScreen(SCREEN_PICK_HOME)
            }
        }
        label(content, "To return to your original launcher, choose it in Android's Home settings. Opening another Home app is temporary; your default stays selected.")
        action(content, "Android settings") { openSystemSettings() }
        action(content, "Back to quick settings") { showScreen(SCREEN_MAIN) }
        action(content, "Back to clock") { backToClock() }
    }

    private fun deviceScreen() {
        heading(content, "Device information", "Read directly from this watch")
        action(content, "Copy device information") {
            val report = deviceReport
            if (report == null) {
                message("Device information is still loading.")
            } else {
                runCatching {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        ?: error("Clipboard unavailable")
                    clipboard.setPrimaryClip(ClipData.newPlainText("HealthSync watch information", report))
                }.onSuccess { message("Device information copied.") }
                    .onFailure { message("The clipboard is unavailable on this watch.") }
            }
        }
        action(content, "Health readings") {
            launchFirst(listOf(Intent(this, WatchOptionsActivity::class.java)
                .putExtra(WatchOptionsActivity.EXTRA_SCREEN, WatchOptionsActivity.SCREEN_HEALTH)),
                "Health readings could not open.")
        }
        action(content, "Heart rate details") {
            launchFirst(listOf(Intent(this, WatchOptionsActivity::class.java)
                .putExtra(WatchOptionsActivity.EXTRA_SCREEN, WatchOptionsActivity.SCREEN_HR_DETAILS)),
                "Heart rate details could not open.")
        }
        action(content, "Android settings") { openSystemSettings() }
        action(content, "Back to quick settings") { showScreen(SCREEN_MAIN) }
        action(content, "Back to clock") { backToClock() }
        label(content, "Sensor entries describe what Android exposes. Check real readings on the watch to confirm each health sensor works.", 11f)
        deviceText = label(content, deviceReport ?: "Reading Android and sensor information…", 12f, Color.WHITE)
        refreshDeviceInfo()
    }

    private fun refreshDeviceInfo() {
        deviceJob?.cancel()
        deviceJob = lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching { readDeviceInfo() }.getOrElse { "Device information is unavailable on this firmware." }
            }
            deviceReport = report
            if (screen == SCREEN_DEVICE) deviceText?.text = report
        }
    }

    @Suppress("DEPRECATION")
    private fun readDeviceInfo(): String {
        val metrics = resources.displayMetrics
        val appVersion = runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            "${info.versionName ?: "Unknown"} ($code)"
        }.getOrDefault("Unavailable")
        val sensors = runCatching {
            (getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getSensorList(Sensor.TYPE_ALL)
                .orEmpty().sortedWith(compareBy<Sensor> { it.type }.thenBy { it.name })
        }.getOrNull()
        return buildString {
            append("Model\n${Build.MODEL.ifBlank { "Unknown" }}\n\n")
            append("Manufacturer\n${Build.MANUFACTURER.ifBlank { "Unknown" }}\n\n")
            append("Device\n${Build.DEVICE.ifBlank { "Unknown" }}\n\n")
            append("Android\n${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n\n")
            append("HealthSync\n$appVersion\n\n")
            append("App display area\n${metrics.widthPixels} × ${metrics.heightPixels} px\n\n")
            append("Display density\n${metrics.densityDpi} dpi · ${String.format(Locale.US, "%.2f", metrics.density)} scale\n\n")
            append("Heart rate report\n${SensorCollectorService.heartRateDiagnosticReport(this@QuickSettingsActivity)}\n\n")
            if (sensors == null) {
                append("Android sensors\nUnavailable")
            } else {
                append("Android sensors\n${sensors.size} exposed\n")
                sensors.forEach { sensor ->
                    append("\n${sensor.name}\n")
                    append("Type ${sensor.type} · ${sensor.stringType}\n")
                    append("Vendor: ${sensor.vendor}\n")
                    append("Wake-up: ${if (sensor.isWakeUpSensor) "yes" else "no"}\n")
                }
            }
        }.trim()
    }

    private fun homePickerScreen() {
        heading(content, "Other Home apps", "Tap a launcher to open it")
        val homes = homeInfo?.alternatives
        when {
            homes == null -> label(content, "Finding installed Home apps…")
            homes.isEmpty() -> label(content, "No other accessible Home app was found on this watch. Use Android settings to choose a default Home app.")
            else -> homes.forEach { app ->
                action(content, "${app.label}\n${app.component.packageName}") { openHome(app) }
            }
        }
        action(content, "Choose / revert default Home") { openHomeSettings() }
        action(content, "Android settings") { openSystemSettings() }
        action(content, "Back to Home options") { showScreen(SCREEN_HOME) }
    }

    private fun openHome(app: HomeApp) {
        val opened = launchFirst(listOf(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
            component = app.component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }), "${app.label} is unavailable. Choose another Home app in Android settings.")
        if (!opened) refreshHomeInfo()
    }

    private fun openHomeSettings() = launchFirst(
        listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)),
        "Home selection is unavailable on this watch. Open its Android Settings from the original launcher."
    )

    private fun requestHealthSyncHome() {
        if (homeInfo?.isHealthSyncHome == true) {
            message("HealthSync is already the default Home app.")
            return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val roles = getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    homeRoleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            } catch (_: RuntimeException) {
                // Home settings is also available on firmware without a working RoleManager UI.
            }
        }
        openHomeSettings()
    }

    private fun refreshHomeInfo() {
        homeJob?.cancel()
        homeJob = lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { readHomeInfo() }.getOrElse {
                    HomeInfo("Default Home status unavailable", false, emptyList())
                }
            }
            homeInfo = info
            homeStatus?.text = info.status
            if (screen == SCREEN_PICK_HOME) showScreen(SCREEN_PICK_HOME)
        }
    }

    @Suppress("DEPRECATION")
    private fun readHomeInfo(): HomeInfo {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT)
        val homes = packageManager.queryIntentActivities(intent, 0).mapNotNull { resolved ->
            val info = resolved.activityInfo ?: return@mapNotNull null
            if (!info.enabled || !info.applicationInfo.enabled || !info.exported) return@mapNotNull null
            val title = runCatching { resolved.loadLabel(packageManager).toString().trim() }
                .getOrDefault("").ifEmpty { info.packageName }
            HomeApp(ComponentName(info.packageName, info.name), title)
        }.distinctBy { it.component }
        val defaultActivity = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        val selected = homes.firstOrNull {
            it.component.packageName == defaultActivity?.packageName && it.component.className == defaultActivity?.name
        }
        val roleHeld = if (Build.VERSION.SDK_INT >= 29) runCatching {
            getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        }.getOrDefault(false) else false
        val ours = roleHeld || selected?.component?.packageName == packageName
        val status = when {
            ours -> "Default Home: HealthSync"
            selected != null -> "Default Home: ${selected.label}"
            else -> "No default Home selected"
        }
        val collator = Collator.getInstance(Locale.getDefault())
        val alternatives = homes.filter { it.component.packageName != packageName }
            .sortedWith { left, right -> collator.compare(left.label, right.label) }
        return HomeInfo(status, ours, alternatives)
    }

}

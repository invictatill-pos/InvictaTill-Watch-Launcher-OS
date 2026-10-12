package com.healthsync.phone.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.WatchSettingsPayload
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.service.WatchCallSetup
import com.healthsync.phone.WatchCallSetupActivity
import com.healthsync.phone.ui.theme.*
import com.healthsync.phone.ui.components.ResumeEffect
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthsync.phone.viewmodel.NotificationsViewModel
import com.healthsync.phone.viewmodel.NotificationAppCategory
import com.healthsync.phone.viewmodel.filterNotificationApps

data class IntervalOption(val label: String, val ms: Long)

private val HR_INTERVALS = listOf(
    IntervalOption("30 seconds", 30_000L), IntervalOption("1 minute", 60_000L),
    IntervalOption("5 minutes", 300_000L), IntervalOption("10 minutes", 600_000L),
    IntervalOption("30 minutes", 1_800_000L), IntervalOption("1 hour", 3_600_000L), IntervalOption("Off", -1L)
)

private val SPO2_INTERVALS = listOf(
    IntervalOption("30 seconds", 30_000L),
    IntervalOption("1 minute", 60_000L), IntervalOption("5 minutes", 300_000L),
    IntervalOption("10 minutes", 600_000L), IntervalOption("30 minutes", 1_800_000L),
    IntervalOption("1 hour", 3_600_000L), IntervalOption("Off", -1L)
)

@Composable
fun SettingsScreen(
    onRequestPermissions: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    notificationViewModel: NotificationsViewModel = hiltViewModel(),
    updateViewModel: com.healthsync.phone.viewmodel.AppUpdateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefs = remember { PhonePreferences(context) }

    var hrIntervalMs by remember { mutableStateOf(prefs.hrIntervalMs) }
    var spo2IntervalMs by remember { mutableStateOf(prefs.spo2IntervalMs) }
    val allAppsEnabled by notificationViewModel.allAppsEnabled.collectAsState()
    val installedApps by notificationViewModel.appFilters.collectAsState()
    val loadingApps by notificationViewModel.isLoadingApps.collectAsState()
    val appLoadError by notificationViewModel.appLoadError.collectAsState()
    var settingsSent by remember { mutableStateOf(false) }
    var stepGoal by remember { mutableStateOf(prefs.stepGoal.toString()) }
    var weightText by rememberSaveable { mutableStateOf(if (prefs.hasUserWeight) prefs.userWeightKg.toString() else "") }
    var heightText by rememberSaveable { mutableStateOf(if (prefs.hasUserHeight) prefs.userHeightCm.toString() else "") }
    var syncNotifications by remember { mutableStateOf(prefs.syncNotifications) }
    var syncCalls by remember { mutableStateOf(prefs.syncCalls) }
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var dialogCategory by remember { mutableStateOf(NotificationAppCategory.ALL) }
    var dialogSearch by rememberSaveable { mutableStateOf("") }
    var hasNotifAccess by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    var missingPermissions by remember { mutableStateOf(emptyList<String>()) }
    var watchCallAssociated by remember { mutableStateOf(false) }
    val connection by BluetoothSyncService.connectionState.collectAsState()
    val validWeight = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 30.0..250.0 }
    val validHeight = heightText.toIntOrNull()?.takeIf { it in 100..230 }
    val validGoal = stepGoal.toIntOrNull()?.takeIf { it in 1..100000 }
    val weightUsesDefault = weightText.isBlank() && !prefs.hasUserWeight
    val heightUsesDefault = heightText.isBlank() && !prefs.hasUserHeight
    val canApply = (validWeight != null || weightUsesDefault) && (validHeight != null || heightUsesDefault) && validGoal != null
    ResumeEffect {
        hasNotifAccess = isNotificationAccessGranted(context)
        watchCallAssociated = WatchCallSetup.associated(context, connection.deviceAddress.orEmpty())
        notificationViewModel.loadInstalledApps()
        missingPermissions = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null,
            Manifest.permission.READ_PHONE_STATE,
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Manifest.permission.ANSWER_PHONE_CALLS else null,
            Manifest.permission.READ_CONTACTS,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        ).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }
    LaunchedEffect(connection.isConnected, connection.deviceAddress) {
        if (!connection.isConnected) settingsSent = false
        watchCallAssociated = WatchCallSetup.associated(context, connection.deviceAddress.orEmpty())
    }
    DisposableEffect(prefs) {
        val stopObserving = prefs.observeSensorIntervals {
            val snapshot = prefs.sensorIntervals()
            hrIntervalMs = snapshot.hrIntervalMs
            spo2IntervalMs = snapshot.spo2IntervalMs
            settingsSent = false
        }
        onDispose { stopObserving() }
    }

    Box(Modifier.fillMaxSize().background(BgDeep)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                com.healthsync.phone.ui.components.ScreenHeading("Settings", "Make HealthSync work for you")
            }

            if (missingPermissions.isNotEmpty()) {
                item {
                    SettingsCard("Phone Permissions", Icons.Default.Security) {
                        Text("Allow nearby devices for watch connection, Phone for call alerts, Contacts for caller names, and Notifications for service status. Call and contact access are optional for health capture.", color = TextSecondary, fontSize = 13.sp)
                        Text("Missing: " + missingPermissions.map { permission -> when (permission) { Manifest.permission.BLUETOOTH_CONNECT -> "Nearby devices"; Manifest.permission.READ_PHONE_STATE -> "Phone"; Manifest.permission.READ_CONTACTS -> "Contacts"; Manifest.permission.ANSWER_PHONE_CALLS -> "Answer calls"; else -> "Notifications" } }.distinct().joinToString(", "), color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRequestPermissions) { Text("Grant permissions", color = AccentCyan) }
                            TextButton(onClick = {
                                try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { android.widget.Toast.makeText(context, "Could not open Android settings on this phone.", android.widget.Toast.LENGTH_LONG).show() }
                            }) { Text("App settings", color = TextDim) }
                        }
                    }
                }
            }

            // Notification Access
            if (!hasNotifAccess) {
                item { NotificationAccessBanner(context) }
            } else {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = StatusSuccess.copy(alpha = 0.08f), border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.25f))) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = StatusSuccess, modifier = Modifier.size(22.dp))
                            Column {
                                Text("Notification Access Granted", color = StatusSuccess, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (syncNotifications) "Allowed notifications can mirror to your watch" else "Mirroring is paused in the controls below", color = TextWhite.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard("Watch Mirroring", Icons.Default.Watch) {
                    MasterToggle("Notification mirroring", syncNotifications) { syncNotifications = it; prefs.syncNotifications = it }
                    HorizontalDivider(color = BorderSubtle)
                    MasterToggle("Call alerts", syncCalls) { syncCalls = it; prefs.syncCalls = it }
                    Text("Choose what reaches your wrist. App filters apply when notification mirroring is enabled.", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            item {
                SettingsCard("Watch Calls", Icons.Default.Call) {
                    Text(when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "Call alerts and answering use Phone permission on Android 8–11. Ending calls requires Android 9 or newer. Audio and mute controls use the phone call screen."
                        watchCallAssociated -> "Connected watch association is ready. Android confirms available controls during each call."
                        else -> "Enable Android companion call access to answer, decline, end, and control calls from your watch."
                    }, color = TextSecondary, fontSize = 13.sp)
                    Text("To speak and listen on the watch, enable Calls for this watch in phone Bluetooth settings. The watch must support Bluetooth calling with a microphone and speaker. Audio stays on its current route until you choose a route on the watch.",
                        color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { context.startActivity(Intent(context, WatchCallSetupActivity::class.java)) },
                            enabled = connection.isConnected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Text(if (watchCallAssociated) "Check call setup" else "Enable watch call controls", color = AccentCyan)
                        }
                        TextButton(onClick = {
                            try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { android.widget.Toast.makeText(context, "Could not open Android settings on this phone.", android.widget.Toast.LENGTH_LONG).show() }
                        }) { Text("Bluetooth", color = TextDim) }
                    }
                    if (!connection.isConnected) Text("Connect your watch first to enable call controls.", color = TextDim, fontSize = 12.sp)
                }
            }

            // Sensor Intervals
            item {
                SettingsCard("Sensor Intervals", Icons.Default.Timer) {
                    IntervalPicker("Heart Rate", Icons.Default.Favorite, MetricHeart, HR_INTERVALS, hrIntervalMs) { hrIntervalMs = it; prefs.hrIntervalMs = it; settingsSent = false }
                    HorizontalDivider(color = BorderSubtle)
                    IntervalPicker("SpO2", Icons.Default.WaterDrop, MetricSpO2, SPO2_INTERVALS, spo2IntervalMs) { spo2IntervalMs = it; prefs.spo2IntervalMs = it; settingsSent = false }
                    Text("Heart rate stops after one valid sensor reading. Off disables automatic readings; manual measurement stays available. Apply phone changes to the connected watch. Interval changes on either device sync, keeping the latest saved edit.", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    HorizontalDivider(color = BorderSubtle)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.DirectionsWalk, null, tint = MetricSteps, modifier = Modifier.size(20.dp))
                        Text("Steps", color = TextWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(20.dp), color = MetricSteps.copy(alpha = 0.12f)) {
                            Text("Watch sensor", color = MetricSteps, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }

            // Goals
            item {
                SettingsCard("Fitness Goals", Icons.Default.Flag) {
                    GoalRow("Daily Steps", Icons.Default.DirectionsWalk, MetricSteps, stepGoal, "steps") { v ->
                        stepGoal = v.filter(Char::isDigit).take(6)
                        stepGoal.toIntOrNull()?.takeIf { it in 1..100000 }?.let { prefs.stepGoal = it; settingsSent = false }
                    }
                    if (stepGoal.toIntOrNull()?.let { it in 1..100000 } != true) {
                        Text("Enter a goal between 1 and 100,000. Your last valid goal is retained.", color = StatusError, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5000, 8000, 10000).forEach { goal ->
                            TextButton(onClick = { stepGoal = goal.toString(); prefs.stepGoal = goal; settingsSent = false }) {
                                Text("${goal / 1000}k", color = MetricSteps)
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard("Your Profile", Icons.Default.Person) {
                    Text("Optional entries help calculate activity estimates. These are values you enter, stored on your phone and sent to your paired watch.", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    if (weightUsesDefault || heightUsesDefault) Text("Unentered values use calculation defaults: 70 kg and 170 cm. They are not measured body data.", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { raw ->
                                val text = raw.replace(',', '.').filter { it.isDigit() || it == '.' }.take(6)
                                if (text.count { it == '.' } <= 1) {
                                    weightText = text
                                    text.toDoubleOrNull()?.takeIf { it.isFinite() && it in 30.0..250.0 }?.let { prefs.userWeightKg = it; settingsSent = false }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Weight") },
                            placeholder = { Text("Enter weight") },
                            suffix = { Text("kg") },
                            supportingText = { Text("30–250 kg") },
                            isError = validWeight == null && !weightUsesDefault,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { raw ->
                                heightText = raw.filter(Char::isDigit).take(3)
                                heightText.toIntOrNull()?.takeIf { it in 100..230 }?.let { prefs.userHeightCm = it; settingsSent = false }
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Height") },
                            placeholder = { Text("Enter height") },
                            suffix = { Text("cm") },
                            supportingText = { Text("100–230 cm") },
                            isError = validHeight == null && !heightUsesDefault,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    if ((validWeight == null && !weightUsesDefault) || (validHeight == null && !heightUsesDefault)) Text("Your last valid entries are retained while you finish editing.", color = StatusError, fontSize = 12.sp)
                }
            }

            // Apply to Watch
            item {
                Surface(
                    onClick = {
                        settingsSent = BluetoothSyncService.sendSettingsToWatch(context, WatchSettingsPayload(hrIntervalMs, spo2IntervalMs, prefs.stepGoal, prefs.userWeightKg, prefs.userHeightCm))
                        if (!settingsSent) android.widget.Toast.makeText(context, "Settings could not be sent. Reconnect your watch and try again.", android.widget.Toast.LENGTH_LONG).show()
                    },
                    enabled = connection.isConnected && canApply,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (settingsSent) StatusSuccess.copy(alpha = 0.12f) else AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (settingsSent) StatusSuccess.copy(alpha = 0.3f) else AccentCyan.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(if (settingsSent) Icons.Default.CheckCircle else Icons.Default.Sync, null, tint = if (settingsSent) StatusSuccess else AccentCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (!canApply) "Check goal and profile values above" else if (!connection.isConnected) "Saved · applies when watch reconnects" else if (settingsSent) "Settings request sent" else "Apply to Watch", color = if (settingsSent) StatusSuccess else AccentCyan, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Notification Apps
            item {
                SettingsCard("Notification Apps", Icons.Default.Notifications) {
                    TextButton(onClick = onOpenNotifications) { Text("Open alerts and notification history", color = AccentCyan) }
                    MasterToggle("All Apps", allAppsEnabled) {
                        notificationViewModel.setAllAppsEnabled(it)
                    }
                    Text(
                        if (allAppsEnabled) "All apps are allowed, including system apps. Turn off 'All Apps' to filter specific notifications."
                        else "Only notifications from your selected apps will appear on your watch.",
                        color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)
                    )
                    val selectedAppsCount = remember(installedApps) { installedApps.count { it.isEnabled } }
                    val totalAppsCount = installedApps.size

                    Spacer(Modifier.height(4.dp))
                    Surface(
                        onClick = { showAppSelectionDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (!allAppsEnabled) AccentCyan.copy(alpha = 0.12f) else BgInput,
                        border = BorderStroke(1.dp, if (!allAppsEnabled) AccentCyan.copy(alpha = 0.35f) else BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Tune, null, tint = if (!allAppsEnabled) AccentCyan else TextDim, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        text = if (allAppsEnabled) "Manage App Filter" else "Manage App Selection",
                                        color = TextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (allAppsEnabled) "$totalAppsCount sources available (all active)" else "$selectedAppsCount of $totalAppsCount enabled",
                                        color = if (!allAppsEnabled) AccentCyan else TextDim,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Configure", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.ChevronRight, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Software Updates
            item {
                val updateStatus by updateViewModel.status.collectAsState()
                val phoneUpdateInfo by updateViewModel.phoneUpdateInfo.collectAsState()
                val watchUpdateInfo by updateViewModel.watchUpdateInfo.collectAsState()

                SettingsCard("Software Updates", Icons.Default.SystemUpdate) {
                    val phoneVersion = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" } catch (_: Exception) { "Unknown" } }
                    val liveWatchVersion by BluetoothSyncService.connectedWatchVersionName.collectAsState()
                    val watchVersion = liveWatchVersion ?: remember { PhonePreferences(context).lastKnownWatchVersion }
                    val displayWatchVersion = if (watchVersion == "Unknown") "Version unknown" else if (watchVersion.startsWith("v", ignoreCase = true)) watchVersion else "v$watchVersion"
                    val updateManifest by updateViewModel.manifest.collectAsState()

                    // Phone App Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Phone App", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("v$phoneVersion", color = TextDim, fontSize = 12.sp)
                        }
                        if (phoneUpdateInfo != null) {
                            Button(
                                onClick = { updateViewModel.updatePhone(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Update (v${phoneUpdateInfo!!.versionName})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = StatusSuccess.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = StatusSuccess, modifier = Modifier.size(12.dp))
                                    Text(if (updateManifest == null) "Check needed" else "No newer version listed", color = if (updateManifest == null) TextDim else StatusSuccess, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))

                    // Watch Companion Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Watch Companion", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(if (liveWatchVersion != null && connection.isConnected) "$displayWatchVersion · connected watch" else "$displayWatchVersion · last known", color = TextDim, fontSize = 12.sp)
                        }
                        if (watchUpdateInfo != null) {
                            Button(
                                onClick = { updateViewModel.updateWatch(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Update Watch (v${watchUpdateInfo!!.versionName})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = StatusSuccess.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, tint = StatusSuccess, modifier = Modifier.size(12.dp))
                                        Text(when { watchVersion == "Unknown" -> "Version unknown"; updateManifest == null -> "Check needed"; else -> "Last known version current" }, color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                TextButton(
                                    onClick = { updateViewModel.forcePushWatchUpdate(context) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Re-send", color = AccentCyan, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Status display during checking, downloading, or streaming
                    when (val s = updateStatus) {
                        is com.healthsync.phone.data.update.UpdateStatus.Checking -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentCyan, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Checking for updates…", color = TextDim, fontSize = 13.sp)
                            }
                        }
                        is com.healthsync.phone.data.update.UpdateStatus.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                LinearProgressIndicator(
                                    progress = { if (s.percent >= 0) s.percent / 100f else 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = AccentCyan
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (s.percent >= 0) "Downloading update: ${s.percent}%" else "Downloading update…",
                                    color = AccentCyan,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        is com.healthsync.phone.data.update.UpdateStatus.TransferringToWatch -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                LinearProgressIndicator(
                                    progress = { s.percent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = AccentCyan
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (s.percent >= 100) "Transfer complete · waiting for watch verification" else "Sending to watch: ${s.percent}% (chunk ${s.currentChunk}/${s.totalChunks})",
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        is com.healthsync.phone.data.update.UpdateStatus.WatchInstallRequired -> {
                            Text(if (s.verified) "Confirm installation on your watch" else "Sent. Check the watch notification and confirm installation.", color = AccentCyan, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        is com.healthsync.phone.data.update.UpdateStatus.ReadyToInstall -> {
                            Button(
                                onClick = { updateViewModel.startUpdate(context) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Install Downloaded Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        is com.healthsync.phone.data.update.UpdateStatus.Error -> {
                            Text(
                                text = "Update Error: ${s.message}",
                                color = StatusError,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        else -> Unit
                    }

                    // Check for Updates Button
                    Surface(
                        onClick = { updateViewModel.checkForUpdates(silent = false) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = AccentCyan.copy(alpha = 0.12f)
                    ) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Refresh, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check for Updates", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // About
            item {
                SettingsCard("About", Icons.Default.Info) {
                    AboutRow("Version", remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" } catch (_: Exception) { "Unknown" } })
                    HorizontalDivider(color = BorderSubtle)
                    AboutRow("Protocol", "Bluetooth RFCOMM")
                    HorizontalDivider(color = BorderSubtle)
                    AboutRow("Storage", "On this phone")
                    HorizontalDivider(color = BorderSubtle)
                    AboutRow("Health data", "Captured on watch")
                    Text("Heart rate and oxygen require supported watch sensors and permission. Rest duration is estimated from motion. Calories and some distances are calculated from activity and profile values.", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // Open System Settings
            item {
                Surface(
                    onClick = { try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { android.widget.Toast.makeText(context, "Could not open Android settings on this phone.", android.widget.Toast.LENGTH_LONG).show() } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BgCard
                ) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Bluetooth, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Bluetooth Settings", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }

        // Dedicated Modal App Notification Selection Dialog
        if (showAppSelectionDialog) {
            val selectedAppsCount = remember(installedApps) { installedApps.count { it.isEnabled } }
            val totalAppsCount = installedApps.size
            val matchingApps = remember(installedApps, dialogCategory, dialogSearch) {
                filterNotificationApps(installedApps, dialogCategory, dialogSearch)
            }

            Dialog(
                onDismissRequest = { showAppSelectionDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.88f),
                    shape = RoundedCornerShape(24.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("App Notifications", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (allAppsEnabled) "All apps currently active via Master Switch"
                                    else "$selectedAppsCount of $totalAppsCount enabled",
                                    color = TextDim, fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { showAppSelectionDialog = false }) {
                                Icon(Icons.Default.Close, "Close", tint = TextDim)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Category chips
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = dialogCategory == NotificationAppCategory.ALL,
                                onClick = { dialogCategory = NotificationAppCategory.ALL },
                                label = { Text("All ($totalAppsCount)", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = dialogCategory == NotificationAppCategory.SELECTED,
                                onClick = { dialogCategory = NotificationAppCategory.SELECTED },
                                label = { Text("Selected ($selectedAppsCount)", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = dialogCategory == NotificationAppCategory.SYSTEM,
                                onClick = { dialogCategory = NotificationAppCategory.SYSTEM },
                                label = { Text("System", fontSize = 12.sp) }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Search
                        OutlinedTextField(
                            value = dialogSearch,
                            onValueChange = { dialogSearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search $totalAppsCount apps…", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = AccentCyan, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (dialogSearch.isNotEmpty()) {
                                    IconButton(onClick = { dialogSearch = "" }) {
                                        Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        // Quick enable / disable matching
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${matchingApps.size} sources", color = TextDim, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        matchingApps.forEach { app ->
                                            if (!app.isEnabled) notificationViewModel.toggleApp(app.packageName, true)
                                        }
                                    },
                                    enabled = !allAppsEnabled
                                ) {
                                    Text("Enable All", fontSize = 12.sp, color = AccentCyan)
                                }
                                TextButton(
                                    onClick = {
                                        matchingApps.forEach { app ->
                                            if (app.isEnabled) notificationViewModel.toggleApp(app.packageName, false)
                                        }
                                    },
                                    enabled = !allAppsEnabled
                                ) {
                                    Text("Disable All", fontSize = 12.sp, color = StatusError)
                                }
                            }
                        }

                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

                        appLoadError?.let { message ->
                            Text(message, color = StatusError, fontSize = 12.sp)
                            TextButton(onClick = notificationViewModel::loadInstalledApps) { Text("Retry loading apps", color = AccentCyan) }
                        }

                        // Scrollable List
                        if (loadingApps && installedApps.isEmpty()) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AccentCyan)
                            }
                        } else if (matchingApps.isEmpty()) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("No apps match your filter.", color = TextDim, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(matchingApps, key = { it.packageName }) { appInfo ->
                                    AppToggleItem(
                                        appInfo.label,
                                        appInfo.packageName,
                                        allAppsEnabled || appInfo.isEnabled,
                                        selectable = !allAppsEnabled,
                                        isSystemApp = appInfo.isSystemApp
                                    ) { enabled ->
                                        notificationViewModel.toggleApp(appInfo.packageName, enabled)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Done button
                        Button(
                            onClick = { showAppSelectionDialog = false },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                        ) {
                            Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntervalPicker(
    label: String, icon: ImageVector, color: Color, options: List<IntervalOption>, current: Long, onChange: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.ms == current }?.label ?: "Custom"

    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = TextWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box {
            Row(
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(selectedLabel, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowDropDown, null, tint = color, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.label, color = if (opt.ms == current) color else TextWhite, fontWeight = if (opt.ms == current) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp) },
                        onClick = { onChange(opt.ms); expanded = false },
                        leadingIcon = if (opt.ms == current) { { Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(14.dp)) } } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalRow(label: String, icon: ImageVector, color: Color, value: String, unit: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(label, color = TextWhite, fontSize = 14.sp)
        }
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.width(142.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = value.toIntOrNull()?.let { it in 1..100000 } != true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color, unfocusedBorderColor = BorderDefault, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, cursorColor = color),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            suffix = { Text(unit, color = TextDim, fontSize = 11.sp) }
        )
    }
}

@Composable
private fun MasterToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Apps, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
        Text(label, color = TextWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentCyan, uncheckedThumbColor = TextDim, uncheckedTrackColor = BgInput))
    }
}

@Composable
private fun AppToggleItem(
    appName: String, pkg: String, isChecked: Boolean,
    selectable: Boolean, isSystemApp: Boolean, onToggle: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = BgCard) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(AccentCyan.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Text(appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pkg, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isSystemApp) Text("Preinstalled / system app", color = AccentBlue, fontSize = 11.sp)
            }
            Switch(checked = isChecked, onCheckedChange = onToggle, enabled = selectable, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentCyan, uncheckedThumbColor = TextDim, uncheckedTrackColor = BgInput))
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim, fontSize = 13.sp)
        Text(value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(icon, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                Text(title, color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

private fun isNotificationAccessGranted(context: Context): Boolean {
    return context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
}

@Composable
private fun NotificationAccessBanner(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StatusError.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, null, tint = StatusError, modifier = Modifier.size(20.dp))
                Text("Notification Access Required", color = StatusError, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text("Enable HealthSync in Android's Notification Access screen. Then choose Gmail or other sources in Notification Apps below; Android's access screen lists notification readers, not message source apps.", color = TextWhite.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
            Surface(
                onClick = { try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { android.widget.Toast.makeText(context, "Could not open Android settings on this phone.", android.widget.Toast.LENGTH_LONG).show() } },
                shape = RoundedCornerShape(10.dp),
                color = StatusError.copy(alpha = 0.15f)
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.OpenInNew, null, tint = StatusError, modifier = Modifier.size(16.dp))
                    Text("Open Notification Settings", color = StatusError, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

package com.healthsync.phone.ui.device

import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.healthsync.phone.ui.components.ResumeEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.ConnectionInfo
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.ui.theme.AccentBlue
import com.healthsync.phone.ui.theme.AccentCyan
import com.healthsync.phone.ui.theme.BgCard
import com.healthsync.phone.ui.theme.BgCardHover
import com.healthsync.phone.ui.theme.BgDeep
import com.healthsync.phone.ui.theme.BorderSubtle
import com.healthsync.phone.ui.theme.StatusError
import com.healthsync.phone.ui.theme.StatusSuccess
import com.healthsync.phone.ui.theme.StatusWarning
import com.healthsync.phone.ui.theme.TextDim
import com.healthsync.phone.ui.theme.TextSecondary
import com.healthsync.phone.ui.theme.TextWhite

@Composable
fun DeviceScreen(
    viewModel: ConnectionViewModel = hiltViewModel(),
    updateViewModel: com.healthsync.phone.viewmodel.AppUpdateViewModel = hiltViewModel(),
    onRequestPermissions: () -> Unit = {},
    onOpenWatchFaceStore: () -> Unit = {}
) {
    val state by viewModel.connectionState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    var refreshVersion by remember { mutableIntStateOf(0) }
    ResumeEffect { refreshVersion++ }
    val pairedDevices = remember(refreshVersion, state.isConnected) { viewModel.pairedDevices }
    val phoneName = remember(refreshVersion) { viewModel.phoneName }
    val hasPermission = remember(refreshVersion) { viewModel.hasBluetoothPermission }
    val bluetoothEnabled = remember(refreshVersion, state.isConnected) { viewModel.bluetoothEnabled }
    val openBluetoothSettings = {
        try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        catch (_: Exception) { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Device", color = TextWhite, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
                Text("Watch connection", color = TextSecondary, fontSize = 13.sp)
            }
        }

        item { ConnectionStatusCard(state = state, phoneName = phoneName) }

        if (!hasPermission || !bluetoothEnabled) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = StatusWarning.copy(alpha = 0.1f), border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (!hasPermission) "Nearby devices permission needed" else "Bluetooth is turned off", color = TextWhite, fontWeight = FontWeight.Bold)
                        Text(if (!hasPermission) "Allow access so HealthSync can communicate with your paired watch." else "Enable Bluetooth to receive workouts and live readings.", color = TextSecondary, fontSize = 13.sp)
                        TextButton(onClick = if (!hasPermission) onRequestPermissions else openBluetoothSettings) {
                            Text(if (!hasPermission) "Allow access" else "Open Bluetooth settings", color = AccentCyan)
                        }
                        if (!hasPermission) TextButton(onClick = {
                            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { }
                        }) { Text("Open app permissions", color = AccentCyan) }
                    }
                }
            }
        }

        errorMessage?.let { message -> item { Text(message, color = StatusError, fontSize = 13.sp) } }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceActionButton(
                    label = if (state.isConnected) "Reconnect" else if (state.isListening) "Restart" else "Connect",
                    icon = Icons.Default.Refresh,
                    color = AccentCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.reconnect() }
                )
                if (state.isConnected || state.isListening) {
                    DeviceActionButton(
                        label = "Disconnect",
                        icon = Icons.Default.BluetoothDisabled,
                        color = StatusError,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.disconnect() }
                    )
                }
            }
        }

        if (!state.isConnected) {
            item { ConnectionStepsCard() }
        } else {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Watch, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Text("Watch Firmware & Launcher", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        val liveWatchVersion by BluetoothSyncService.connectedWatchVersionName.collectAsState()
                        val watchVersion = liveWatchVersion ?: remember { PhonePreferences(context).lastKnownWatchVersion }
                        val displayWatchVersion = if (watchVersion.startsWith("v", ignoreCase = true)) watchVersion else "v$watchVersion"
                        Text("Kolabee U8 Ultra · Firmware $displayWatchVersion is connected. Keep the watch launcher up to date via Bluetooth OTA.", color = TextDim, fontSize = 12.sp)
                        Surface(
                            onClick = { updateViewModel.checkWatchUpdateManually() },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = AccentCyan.copy(alpha = 0.12f)
                        ) {
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.SystemUpdate, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Check for Watch Updates", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Watch, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Text("Watch Face Studio & Store", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Explore luxury, cyberpunk, sport, and minimalist dials. Live interactive preview and 1-tap beam to watch.", color = TextDim, fontSize = 12.sp)
                        Surface(
                            onClick = onOpenWatchFaceStore,
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = AccentCyan
                        ) {
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Watch, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open Watch Face Studio", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            TextButton(onClick = openBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.SettingsBluetooth, "Bluetooth settings", tint = AccentCyan)
                Spacer(Modifier.size(8.dp))
                Text("Pair a watch in Bluetooth settings", color = AccentCyan)
            }
        }

        item { SectionHeader("Paired Devices", Icons.Default.Bluetooth, AccentCyan) }

        if (pairedDevices.isEmpty()) {
            item { EmptyDeviceCard() }
        } else {
            items(pairedDevices, key = { it.address }) { device ->
                val name = viewModel.deviceName(device)
                val isCurrent = state.deviceAddress == device.address && state.isConnected
                PairedDeviceRow(name = name, address = device.address, isConnected = isCurrent)
            }
        }

        item { SectionHeader("Connection Details", Icons.Default.Info, AccentBlue) }
        item { ServerInfoCard(phoneName = phoneName, state = state) }
    }
}

@Composable
private fun ConnectionStatusCard(state: ConnectionInfo, phoneName: String) {
    val isConnected = state.isConnected
    val isListening = state.isListening
    val statusColor by animateColorAsState(
        targetValue = when {
            isConnected -> StatusSuccess
            isListening -> StatusWarning
            else -> StatusError
        },
        animationSpec = tween(500),
        label = "device-status"
    )
    val pulse = rememberInfiniteTransition(label = "device-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "pulse-scale"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = BgCard,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.22f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(86.dp).scale(if (isConnected || isListening) pulseScale else 1f).clip(CircleShape).background(statusColor.copy(alpha = 0.08f)))
                Box(
                    modifier = Modifier.size(62.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)).border(2.dp, statusColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            isConnected -> Icons.Default.CheckCircle
                            isListening -> Icons.Default.BluetoothSearching
                            else -> Icons.Default.BluetoothDisabled
                        },
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Text(
                when {
                    isConnected -> "Connected"
                    isListening -> "Waiting for watch"
                    else -> "Not connected"
                },
                color = statusColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )

            val detail = when {
                isConnected && state.deviceName != null -> state.deviceName
                isListening -> "Open HealthSync on your paired watch to connect to $phoneName"
                else -> "Connect to receive your watch’s data"
            }
            Text(detail ?: "", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
            state.deviceAddress?.let {
                Text(it, color = TextDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DeviceActionButton(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(7.dp))
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ConnectionStepsCard() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = BgCard, border = BorderStroke(1.dp, BorderSubtle)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.SettingsBluetooth, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                Text("Pairing Checklist", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
            GuideStep("1", "Pair the watch in Android Bluetooth settings")
            GuideStep("2", "Open HealthSync on the watch")
            GuideStep("3", "Return here after the watch connects")
        }
    }
}

@Composable
private fun GuideStep(step: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(AccentCyan.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text(step, color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionHeader(label: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(label.uppercase(), color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun EmptyDeviceCard() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = BgCard, border = BorderStroke(1.dp, BorderSubtle)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.BluetoothDisabled, null, tint = TextDim, modifier = Modifier.size(34.dp))
            Text("No paired devices", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Use Android Bluetooth settings", color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PairedDeviceRow(name: String, address: String, isConnected: Boolean) {
    val color = if (isConnected) StatusSuccess else AccentBlue
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (isConnected) StatusSuccess.copy(alpha = 0.32f) else BorderSubtle)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Watch, null, tint = color, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(address, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isConnected) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(StatusSuccess.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ServerInfoCard(phoneName: String, state: ConnectionInfo) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = BgCard, border = BorderStroke(1.dp, BorderSubtle)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Phone Name", phoneName, Icons.Default.PhoneAndroid, AccentCyan)
            HorizontalDivider(color = BorderSubtle)
            InfoRow("Status", when {
                state.isConnected -> "Connected"
                state.isListening -> "Listening"
                else -> "Stopped"
            }, Icons.Default.Router, if (state.isConnected) StatusSuccess else AccentBlue)
            HorizontalDivider(color = BorderSubtle)
            InfoRow("Protocol", "Bluetooth RFCOMM", Icons.Default.Bluetooth, AccentBlue)
            HorizontalDivider(color = BorderSubtle)
            InfoRow("Paired devices", "Android Bluetooth", Icons.Default.Tag, TextDim)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, icon: ImageVector, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

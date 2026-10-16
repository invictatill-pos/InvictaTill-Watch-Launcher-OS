package com.healthsync.phone.ui.device

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.healthsync.phone.data.InternetSharingPolicy
import com.healthsync.phone.data.WatchInternetReport
import com.healthsync.phone.data.model.ConnectionInfo
import com.healthsync.phone.service.PhoneInternetSharing
import com.healthsync.phone.ui.theme.*
import kotlinx.coroutines.delay

@Composable
internal fun InternetSharingCard(state: ConnectionInfo, onRefresh: () -> Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var phoneStatus by remember { mutableStateOf(PhoneInternetSharing.readStatus(context)) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var requestedAt by remember { mutableLongStateOf(0L) }
    var requestFailed by remember { mutableStateOf(false) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                phoneStatus = PhoneInternetSharing.readStatus(context)
                now = SystemClock.elapsedRealtime()
                delay(3_000)
            }
        }
    }
    LaunchedEffect(state.isConnected) { requestedAt = 0L; requestFailed = false }
    // A report can arrive between timer ticks. Read the clock on that recomposition too.
    val currentNow = maxOf(now, SystemClock.elapsedRealtime())
    val fresh = WatchInternetReport.isFresh(state.isConnected, state.watchInternetStatus, state.watchInternetReceivedAt, currentNow)
    val shared = fresh && InternetSharingPolicy.isBluetoothInternet(state.watchInternetStatus)
    val waiting = state.isConnected && requestedAt > state.watchInternetReceivedAt && currentNow - requestedAt < 12_000L
    val timedOut = state.isConnected && requestedAt > state.watchInternetReceivedAt && currentNow - requestedAt >= 12_000L

    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = BgCard,
        border = BorderStroke(1.dp, if (shared) StatusSuccess.copy(alpha = .3f) else BorderSubtle)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Router, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                Text("Internet for watch apps", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text("Share your phone's internet using the existing Bluetooth pairing.", color = TextSecondary, fontSize = 13.sp)
            Text("Phone: ${InternetSharingPolicy.statusText(phoneStatus)}", color = TextDim, fontSize = 12.sp)
            Text(
                "Watch: " + WatchInternetReport.statusText(state.isConnected, state.watchInternetStatus, state.watchInternetReceivedAt, currentNow),
                color = if (shared) StatusSuccess else TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Text("Watch-reported default connection. Updates can take 20 seconds; HealthSync sync and internet sharing have separate status.", color = TextDim, fontSize = 12.sp)
            if (waiting) Text("Checking watch connection…", color = TextSecondary, fontSize = 12.sp)
            if (timedOut) Text("No new report received. Open HealthSync on the watch and try again.", color = StatusWarning, fontSize = 12.sp)
            if (requestFailed) Text("Could not request watch status. Check the HealthSync connection and try again.", color = StatusWarning, fontSize = 12.sp)
            TextButton(onClick = { showSetup = !showSetup }) {
                Text(if (showSetup) "Hide setup" else "Set up Bluetooth internet", color = AccentCyan)
            }
            if (showSetup) {
                Text("1. Turn on mobile data (or a shareable Wi-Fi connection) on this phone.", color = TextSecondary, fontSize = 13.sp)
                Text("2. Open phone network settings → Hotspot & tethering / Mobile Hotspot and Tethering → Bluetooth tethering, then turn it on.", color = TextSecondary, fontSize = 13.sp)
                TextButton(onClick = {
                    val opened = runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)); true }.getOrDefault(false)
                    if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        .onFailure { Toast.makeText(context, "Android network settings could not open.", Toast.LENGTH_LONG).show() }
                }) { Text("Phone network settings", color = AccentCyan) }
                Text("3. On the watch, open HealthSync Settings → Phone connection → Internet from phone. In watch Bluetooth settings, select this same paired phone and enable Internet access.", color = TextSecondary, fontSize = 13.sp)
                Text("4. Return here and refresh. Internet via Bluetooth means Android verified the watch's Bluetooth internet connection. Apps can then use that connection.", color = TextSecondary, fontSize = 13.sp)
                Text("If Bluetooth tethering or Internet access is missing, the device firmware may not support it. Use your phone's Wi-Fi hotspot instead. Android and your carrier control sharing availability.", color = TextDim, fontSize = 12.sp)
                Text("To stop sharing, turn off Bluetooth tethering in phone settings. Disconnecting HealthSync stops sync only.", color = TextDim, fontSize = 12.sp)
            }
            TextButton(onClick = {
                now = SystemClock.elapsedRealtime()
                requestFailed = !onRefresh()
                requestedAt = if (requestFailed) 0L else now
                phoneStatus = PhoneInternetSharing.readStatus(context)
            }, enabled = state.isConnected && !waiting) {
                Text(if (waiting) "Checking…" else "Refresh watch status", color = if (state.isConnected && !waiting) AccentCyan else TextDim)
            }
        }
    }
}

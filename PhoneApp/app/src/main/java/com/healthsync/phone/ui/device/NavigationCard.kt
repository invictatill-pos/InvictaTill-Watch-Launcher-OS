package com.healthsync.phone.ui.device

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthsync.phone.data.model.ConnectionInfo
import com.healthsync.phone.service.PhoneNavigationBridge
import com.healthsync.phone.service.PhoneScreenShareService
import com.healthsync.phone.ui.components.ResumeEffect
import com.healthsync.phone.ui.theme.*

@Composable
internal fun NavigationCard(connection: ConnectionInfo) {
    val context = LocalContext.current
    val navigation by PhoneNavigationBridge.state.collectAsState()
    var launchMessage by remember { mutableStateOf("") }
    ResumeEffect { PhoneNavigationBridge.refresh(context) }
    LaunchedEffect(connection.isConnected, connection.supportsNavigation) { PhoneNavigationBridge.refresh(context) }
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = BgCard,
        border = BorderStroke(1.dp, if (navigation.status == "ACTIVE") StatusSuccess.copy(alpha = .3f) else BorderSubtle)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Watch navigation", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Large turn directions and distance from Google Maps, made for your watch.", color = TextSecondary, fontSize = 13.sp)
            Text(navigation.message, color = if (navigation.status == "ACTIVE") StatusSuccess else TextSecondary, fontSize = 13.sp)
            if (!connection.isConnected) Text("Connect your watch over Bluetooth.", color = TextDim, fontSize = 12.sp)
            else if (!connection.supportsNavigation) Text("Install the navigation update on your watch first.", color = StatusWarning, fontSize = 12.sp)
            Button(onClick = {
                val enable = !navigation.enabled
                if (enable) PhoneScreenShareService.stopSharing(context)
                PhoneNavigationBridge.configure(context, enable)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (navigation.enabled) "Disable watch navigation" else "Enable watch navigation")
            }
            if (navigation.enabled && !navigation.hasNotificationAccess) {
                Button(onClick = {
                    launchMessage = runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        ""
                    }.getOrDefault("Open Android Settings → Notification access and enable HealthSync.")
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Allow notification access") }
            }
            TextButton(onClick = {
                val intent = (context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).setPackage("com.google.android.apps.maps"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchMessage = runCatching { context.startActivity(intent); "" }
                    .getOrDefault("Install Google Maps on this phone, then start navigation in the Maps app.")
            }) { Text("Open Google Maps", color = AccentCyan) }
            if (launchMessage.isNotEmpty()) Text(launchMessage, color = StatusWarning, fontSize = 12.sp)
            Text("Enable navigation and notification access, start a route in Google Maps, then open Apps → Navigation on your watch. Allow Maps notifications on the phone.", color = TextDim, fontSize = 12.sp)
            Text("Directions can continue with the phone screen locked while Maps and Bluetooth stay connected. Trip details appear when Maps includes them in its navigation notification.", color = TextDim, fontSize = 12.sp)
        }
    }
}

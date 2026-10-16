package com.healthsync.phone.ui.device

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.healthsync.phone.PhoneScreenShareActivity
import com.healthsync.phone.data.model.ConnectionInfo
import com.healthsync.phone.service.PhoneScreenShareService
import com.healthsync.phone.ui.theme.*

@Composable
internal fun PhoneScreenShareCard(connection: ConnectionInfo) {
    val context = LocalContext.current
    val share by PhoneScreenShareService.state.collectAsState()
    var openMessage by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = BgCard,
        border = BorderStroke(1.dp, if (share.active) StatusSuccess.copy(alpha = .3f) else BorderSubtle)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google Maps on watch", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Share the live Google Maps screen from your phone over Bluetooth.", color = TextSecondary, fontSize = 13.sp)
            Text(share.message, color = if (share.active && share.acknowledgedFrame > 0) StatusSuccess else TextSecondary, fontSize = 13.sp)
            if (!connection.isConnected) Text("Connect your watch to start sharing.", color = TextDim, fontSize = 12.sp)
            else if (!connection.supportsPhoneScreenShare) Text("Update HealthSync on your watch to enable live phone maps.", color = StatusWarning, fontSize = 12.sp)
            if (share.active || share.pending) {
                Button(onClick = { PhoneScreenShareService.stopSharing(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Stop sharing")
                }
            } else {
                Button(
                    onClick = { context.startActivity(Intent(context, PhoneScreenShareActivity::class.java)) },
                    enabled = connection.isConnected && connection.supportsPhoneScreenShare,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) { Text("Share Google Maps") }
            }
            TextButton(onClick = {
                openMessage = if (PhoneScreenShareActivity.openGoogleMaps(context)) "" else "Google Maps could not be opened. Open or install it on your phone."
            }) { Text("Open Google Maps", color = AccentCyan) }
            if (openMessage.isNotEmpty()) Text(openMessage, color = StatusWarning, fontSize = 12.sp)
            Text("Approve screen sharing on your phone each time. Open Live phone map on the watch. Refreshes about every 1.5 seconds or slower; keep your phone screen on and unlocked.", color = TextDim, fontSize = 12.sp)
        }
    }
}

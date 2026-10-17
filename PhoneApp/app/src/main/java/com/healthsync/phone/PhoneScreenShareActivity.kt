package com.healthsync.phone

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.service.PhoneScreenShareService
import com.healthsync.phone.ui.theme.HealthSyncTheme

/** Consent stays in a visible Activity; a watch request can never silently start projection. */
class PhoneScreenShareActivity : ComponentActivity() {
    companion object {
        /** Prefer the actual Google Maps app; Google Maps on the web is a fallback when unavailable. */
        fun openGoogleMaps(context: Context): Boolean {
            // Launch its existing task so an already running route is preserved.
            val app = (context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).setPackage("com.google.android.apps.maps"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(app)
                true
            } catch (_: RuntimeException) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                } catch (_: RuntimeException) { false }
            }
        }
    }

    private var launchingService = false
    private var consentAttemptId: String? = null
    private var launchMessage by mutableStateOf("")
    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val attemptId = consentAttemptId ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK || result.data == null) {
            PhoneScreenShareService.consentFailed("Screen sharing canceled. Nothing is being shared.", attemptId)
        } else if (!PhoneScreenShareService.state.value.pending || !canShare()) {
            PhoneScreenShareService.consentFailed("The watch connection changed. Connect your watch and approve sharing again.", attemptId)
        } else {
            try {
                launchingService = true
                PhoneScreenShareService.startWithConsent(this, result.resultCode, result.data!!, attemptId)
                if (openGoogleMaps(this)) finish()
                else launchMessage = "Open Google Maps on your phone, then start a route. You can stop sharing here."
            } catch (_: RuntimeException) {
                launchingService = false
                PhoneScreenShareService.consentFailed("Android could not start sharing. Keep HealthSync open and try again.", attemptId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consentAttemptId = savedInstanceState?.getString("consent_attempt_id")
        setContent {
            val share by PhoneScreenShareService.state.collectAsState()
            val connection by BluetoothSyncService.connectionState.collectAsState()
            HealthSyncTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Phone screen viewer", style = MaterialTheme.typography.headlineMedium)
                        Text("For large turn directions, enable Watch navigation from the Device screen instead.")
                        Text("Show the real Google Maps screen from this phone on your watch through your existing Bluetooth connection.")
                        if (Build.VERSION.SDK_INT >= 34) {
                            Text("1. Tap Start sharing. In Android's prompt, choose A single app and select Google Maps. You can also choose the entire screen.")
                        } else {
                            Text("1. Tap Start sharing and approve Android's screen capture prompt. This Android version shares your entire phone screen, including other apps and notifications. Keep Google Maps visible while sharing.")
                        }
                        Text("2. Start navigation in Google Maps on this phone. Open Live phone map on the watch to view it. Zoom and pan on the watch change the watch view only.")
                        Text("Images refresh about every 1.5 seconds or slower over Bluetooth. Navigation and spoken directions run on the phone. Keep its screen on and unlocked; locking it stops sharing.")
                        Text(share.message)
                        if (launchMessage.isNotEmpty()) Text(launchMessage)
                        if (!connection.isConnected) Text("Connect your watch in HealthSync before sharing.")
                        else if (!connection.supportsPhoneScreenShare) Text("Update HealthSync on your watch to use live phone maps.")
                        if (share.active || share.pending) {
                            Button(onClick = { PhoneScreenShareService.stopSharing(this@PhoneScreenShareActivity) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Stop sharing")
                            }
                        } else {
                            Button(onClick = { requestConsent() }, enabled = connection.isConnected && connection.supportsPhoneScreenShare, modifier = Modifier.fillMaxWidth()) {
                                Text("Start sharing")
                            }
                        }
                        TextButton(onClick = {
                            if (!openGoogleMaps(this@PhoneScreenShareActivity)) launchMessage = "Google Maps could not be opened. Install or open Google Maps on your phone."
                        }) { Text("Open Google Maps") }
                        TextButton(onClick = { finish() }) { Text("Back to HealthSync") }
                    }
                }
            }
        }
    }

    private fun canShare(): Boolean = BluetoothSyncService.connectionState.value.let { it.isConnected && it.supportsPhoneScreenShare }

    private fun requestConsent() {
        if (!canShare()) return
        consentAttemptId = PhoneScreenShareService.beginConsent() ?: return
        try {
            // Never retain this result or reuse an old token. Android offers app-only capture on API 34+.
            consentLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        } catch (_: RuntimeException) {
            PhoneScreenShareService.consentFailed("Screen sharing is unavailable on this phone.", consentAttemptId)
        }
    }

    override fun onDestroy() {
        if (isFinishing && !launchingService && consentAttemptId != null && PhoneScreenShareService.state.value.pending) {
            PhoneScreenShareService.consentFailed("Screen sharing canceled. Nothing is being shared.", consentAttemptId)
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // An attempt identifier is safe to restore; the Android projection token is never saved.
        outState.putString("consent_attempt_id", consentAttemptId)
        super.onSaveInstanceState(outState)
    }
}

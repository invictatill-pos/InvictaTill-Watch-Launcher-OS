package com.healthsync.phone

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.service.WatchCallSetup
import com.healthsync.phone.ui.theme.HealthSyncTheme

/** Explicit user setup: associate only the watch that is currently connected over RFCOMM. */
class WatchCallSetupActivity : ComponentActivity() {
    private var status by mutableStateOf("Preparing watch call controls…")
    private var pending by mutableStateOf(false)
    private var address = ""
    private val associationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        pending = false
        status = if (WatchCallSetup.associated(this, address))
            "Watch associated. Your next phone call will show available watch controls."
        else "Setup was not completed. You can try again."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        address = BluetoothSyncService.connectionState.value.deviceAddress.orEmpty()
        setContent {
            HealthSyncTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Watch calls", style = MaterialTheme.typography.headlineMedium)
                        Text("Allow Android to share call controls with your connected watch. Caller names use Contacts access. To talk on the watch, its Bluetooth connection must support Calls and its microphone and speaker must be available.")
                        Text(status)
                        Button(onClick = { associateWatch() }, enabled = !pending) { Text("Enable call controls") }
                        TextButton(onClick = { finish() }) { Text("Back to Settings") }
                    }
                }
            }
        }
        status = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "On Android 8–11, grant Phone permission in Settings for call alerts and answering. Android companion audio controls require Android 12 or newer."
            address.isBlank() -> "Connect your watch in HealthSync first, then return to this screen."
            WatchCallSetup.associated(this, address) -> "Your connected watch is already associated. Try a phone call to confirm available controls."
            else -> "Ready to associate ${BluetoothSyncService.connectionState.value.deviceName ?: "your watch"}."
        }
    }

    private fun associateWatch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { status = "Call controls require Android 12 or newer"; return }
        if (address.isBlank() || !BluetoothSyncService.connectionState.value.isConnected ||
            BluetoothSyncService.connectionState.value.deviceAddress != address) {
            status = "Reconnect your watch and open call setup again"; return
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            status = "This phone does not support Android companion call controls"; return
        }
        if (WatchCallSetup.associated(this, address)) { status = "Watch association is ready. Try a phone call"; return }
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true).setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH).build()
        val callback = object : CompanionDeviceManager.Callback() {
            @Deprecated("Deprecated in Java")
            override fun onDeviceFound(chooserLauncher: IntentSender) = showApproval(chooserLauncher)
            override fun onAssociationPending(intentSender: IntentSender) = showApproval(intentSender)
            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
                if (associationInfo.deviceMacAddress?.toString().equals(address, true)) {
                    getSharedPreferences("watch_call_setup", MODE_PRIVATE).edit()
                        .putString("address", address).putInt("association_id", associationInfo.id).apply()
                    pending = false
                    status = "Watch associated. Call controls become available during a phone call."
                } else { pending = false; status = "Android associated a different device. Reconnect your watch and retry" }
            }
            override fun onFailure(error: CharSequence?) {
                pending = false; status = error?.toString().orEmpty().ifBlank { "Android could not associate the watch. Try again" }
            }
        }
        pending = true
        status = "Confirm your connected watch in the Android setup dialog"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) manager.associate(request, mainExecutor, callback)
            else { @Suppress("DEPRECATION") manager.associate(request, callback, Handler(Looper.getMainLooper())) }
        } catch (e: Exception) { pending = false; status = e.message ?: "Watch call setup unavailable" }
    }

    private fun showApproval(sender: IntentSender) {
        if (isFinishing || isDestroyed) return
        try { associationLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
        catch (e: Exception) { pending = false; status = e.message ?: "Could not open Android watch setup" }
    }
}

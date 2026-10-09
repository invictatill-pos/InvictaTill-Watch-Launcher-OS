package com.healthsync.phone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.service.CallMonitorService
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val bluetoothAllowed = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!bluetoothAllowed) return
            if (com.healthsync.phone.data.PhonePreferences(context).connectionPaused) return
            try { ContextCompat.startForegroundService(context, Intent(context, BluetoothSyncService::class.java)) } catch (_: Exception) {}
            if (com.healthsync.phone.data.PhonePreferences(context).syncCalls && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                try { ContextCompat.startForegroundService(context, Intent(context, CallMonitorService::class.java)) } catch (_: Exception) {}
            }
        }
    }
}

package com.healthsync.watch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.service.SensorCollectorService
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log

class WatchBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            com.healthsync.watch.service.BezelOverlayService.reconcile(context)
            val bluetoothAllowed = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (bluetoothAllowed) try {
                ContextCompat.startForegroundService(context, Intent(context, BluetoothClientService::class.java))
            } catch (e: Exception) {
                Log.w("WatchBootReceiver", "Bluetooth recovery unavailable; reopen HealthSync", e)
            }
            // Modern Android restricts body-sensor foreground services launched from boot.
            val healthAllowed = Build.VERSION.SDK_INT < 34 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED ||
                (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS_BACKGROUND) == PackageManager.PERMISSION_GRANTED)
            if (!healthAllowed) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, SensorCollectorService::class.java))
            } catch (e: Exception) {
                Log.w("WatchBootReceiver", "Sensor recovery unavailable; reopen HealthSync", e)
            }
        }
    }
}


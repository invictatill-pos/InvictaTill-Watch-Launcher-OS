package com.healthsync.watch.sensor

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/** Start with only the types used by this service, rather than every manifest type. */
object ForegroundPolicy {
    fun start(service: Service, id: Int, notification: Notification, includeLocation: Boolean = false): Boolean {
        fun allowed(permission: String) = ContextCompat.checkSelfPermission(service, permission) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT < 29) {
            return try { service.startForeground(id, notification); true }
            catch (e: RuntimeException) { Log.w("ForegroundPolicy", "Foreground start unavailable", e); false }
        }
        var types = 0
        if (Build.VERSION.SDK_INT >= 34 && (allowed(Manifest.permission.ACTIVITY_RECOGNITION) || allowed(Manifest.permission.BODY_SENSORS))) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }
        if (includeLocation && (allowed(Manifest.permission.ACCESS_FINE_LOCATION) || allowed(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 34 && types == 0) return false
        // Call the API34 platform directly; older AndroidX releases mask out
        // service types introduced after API29, including HEALTH.
        return try { service.startForeground(id, notification, types); true }
        catch (e: RuntimeException) { Log.w("ForegroundPolicy", "Foreground start unavailable", e); false }
    }

    fun startConnectedDevice(service: Service, id: Int, notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29)
            service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else service.startForeground(id, notification)
        true
    } catch (e: RuntimeException) {
        Log.w("ForegroundPolicy", "Bluetooth foreground start unavailable", e)
        false
    }
}

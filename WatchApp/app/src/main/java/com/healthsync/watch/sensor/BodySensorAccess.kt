package com.healthsync.watch.sensor

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun bodySensorAccessStatus(context: Context): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
        return "permission_required"
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS_BACKGROUND) != PackageManager.PERMISSION_GRANTED) {
        val process = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(process)
        val visibleActivity = process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        if (!visibleActivity) return "background_permission_required"
    }
    return null
}

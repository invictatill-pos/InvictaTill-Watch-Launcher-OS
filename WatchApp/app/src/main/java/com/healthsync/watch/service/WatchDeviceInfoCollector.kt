package com.healthsync.watch.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.healthsync.watch.data.DeviceInfoPayload
import com.healthsync.watch.data.WatchPreferences
import kotlin.math.roundToInt

object WatchDeviceInfoCollector {

    fun collect(context: Context): DeviceInfoPayload {
        val appContext = context.applicationContext

        // 1. App Version
        val (vName, vCode) = try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) pInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pInfo.versionCode
            (pInfo.versionName ?: "2.8.4") to code
        } catch (_: Exception) {
            "2.8.4" to 32
        }

        // 2. Battery & Charging via Sticky Intent
        var batteryPct = -1
        var isCharging = false
        try {
            val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100f / scale).roundToInt().coerceIn(0, 100)
                }
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {}

        // Fallback to BatteryManager property if sticky intent was unavailable
        if (batteryPct < 0) {
            try {
                val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            } catch (_: Exception) {}
        }

        // 3. Storage Free / Total
        var freeMb = -1L
        var totalMb = -1L
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            freeMb = (stat.availableBlocksLong * blockSize) / (1024L * 1024L)
            totalMb = (stat.blockCountLong * blockSize) / (1024L * 1024L)
        } catch (_: Exception) {}

        // 4. Power Save Mode
        val prefs = WatchPreferences(appContext)
        val powerSaveMode = when {
            prefs.isSuperExtremePowerSave -> "SUPER_EXTREME"
            prefs.isExtremePowerSave -> "EXTREME"
            else -> "NORMAL"
        }

        // 5. Hardware Sensors
        val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasHr = sm?.let { SensorCollectorService.hasHeartRateSensor(appContext) } ?: true
        val hasOxygen = SensorCollectorService.spo2Available || runCatching {
            sm?.getSensorList(Sensor.TYPE_ALL)?.any { it.name.contains("oxygen", ignoreCase = true) || it.name.contains("spo2", ignoreCase = true) }
        }.getOrDefault(false) ?: false
        val hasStep = sm?.let {
            it.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null || it.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
        } ?: true

        return DeviceInfoPayload(
            versionName = vName,
            versionCode = vCode,
            model = Build.MODEL.ifBlank { "Kolabee U8 Ultra" },
            batteryPercent = batteryPct,
            isCharging = isCharging,
            powerSaveMode = powerSaveMode,
            storageFreeMb = freeMb,
            storageTotalMb = totalMb,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            manufacturer = Build.MANUFACTURER,
            hasHeartRateSensor = hasHr,
            hasSpO2Sensor = hasOxygen,
            hasStepSensor = hasStep,
            lastUpdated = System.currentTimeMillis()
        )
    }
}

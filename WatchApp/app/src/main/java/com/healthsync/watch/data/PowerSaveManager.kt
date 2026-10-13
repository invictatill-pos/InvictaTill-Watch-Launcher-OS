package com.healthsync.watch.data

import android.content.Context
import android.content.Intent
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.service.SensorCollectorService
import kotlin.math.roundToInt

/**
 * Manages power-saving tiers across the HealthSync watch ecosystem.
 *
 * Tiers:
 * 1. NORMAL: Full 60 FPS animations, regular health sensor polling, full Bluetooth phone companion sync.
 * 2. EXTREME (Stamina): Saves battery at an extreme level while KEEPING COMMUNICATION ALIVE with the phone app.
 *    - Throttles high-FPS animations to 1 Hz static ticks.
 *    - Pauses continuous high-frequency optical sensors (HR/SpO2 to manual measure only).
 *    - Disables wrist wake gesture and wallpaper backdrop (OLED true-black).
 *    - Bluetooth connection, incoming calls, phone notifications and sync remain 100% functional!
 * 3. SUPER_EXTREME (Emergency): Maximum battery preservation.
 *    - STOPS communication from the phone app to the watch (RF radio powered off).
 *    - Runs ONLY the bare minimum emergency watch feature (minimal high-contrast clock, date, battery %).
 *    - Fully terminates background sensors, fall detection, and sync loops.
 */
object PowerSaveManager {

    const val ACTION_POWER_SAVE_CHANGED = "com.healthsync.watch.POWER_SAVE_CHANGED"
    const val EXTRA_MODE = "power_save_mode"

    /**
     * Applies a new power saving mode, updates preferences, broadcasts state changes,
     * and reconfigures Bluetooth and health sensor services accordingly.
     */
    fun applyMode(context: Context, mode: PowerSaveMode) {
        val appContext = context.applicationContext
        val prefs = WatchPreferences(appContext)
        val previous = prefs.powerSaveMode
        prefs.powerSaveMode = mode

        // Broadcast to WatchFaceActivity and UI components
        val intent = Intent(ACTION_POWER_SAVE_CHANGED).apply {
            setPackage(appContext.packageName)
            putExtra(EXTRA_MODE, mode.code)
        }
        appContext.sendBroadcast(intent)

        // Apply service-level power policies
        when (mode) {
            PowerSaveMode.SUPER_EXTREME -> {
                // Terminate active workout tracking if ongoing
                if (com.healthsync.watch.service.WorkoutTrackingService.isActive) {
                    try {
                        appContext.startService(Intent(appContext, com.healthsync.watch.service.WorkoutTrackingService::class.java).apply {
                            action = com.healthsync.watch.service.WorkoutTrackingService.ACTION_STOP
                        })
                    } catch (e: Exception) {
                        android.util.Log.w("PowerSaveManager", "Could not stop workout: ${e.message}")
                    }
                }
                // Terminate phone RF communication completely to conserve mA draw
                BluetoothClientService.onSuperPowerSaveModeChanged(appContext, isSuperExtreme = true)
                SensorCollectorService.notifyPowerSaveChanged(appContext)
            }
            PowerSaveMode.EXTREME -> {
                // Communication MUST be kept alive in Extreme mode
                BluetoothClientService.onSuperPowerSaveModeChanged(appContext, isSuperExtreme = false)
                SensorCollectorService.notifyPowerSaveChanged(appContext)
            }
            PowerSaveMode.NORMAL -> {
                // Restore all services to full functionality
                BluetoothClientService.onSuperPowerSaveModeChanged(appContext, isSuperExtreme = false)
                SensorCollectorService.notifyPowerSaveChanged(appContext)
            }
        }
    }

    /** Returns true if communication between phone app and watch is active in this mode. */
    fun isCommunicationAlive(mode: PowerSaveMode): Boolean {
        return mode != PowerSaveMode.SUPER_EXTREME
    }

    fun getModeTitle(mode: PowerSaveMode): String = when (mode) {
        PowerSaveMode.NORMAL -> "Normal Mode"
        PowerSaveMode.EXTREME -> "Extreme Battery Saver"
        PowerSaveMode.SUPER_EXTREME -> "Super Extreme Saver"
    }

    fun getModeBadge(mode: PowerSaveMode): String = when (mode) {
        PowerSaveMode.NORMAL -> "BALANCED"
        PowerSaveMode.EXTREME -> "STAMINA · PHONE LINKED"
        PowerSaveMode.SUPER_EXTREME -> "EMERGENCY STANDBY"
    }

    fun getCommunicationStatus(mode: PowerSaveMode): String = when (mode) {
        PowerSaveMode.NORMAL -> "Phone communication: Active"
        PowerSaveMode.EXTREME -> "Phone communication: Alive (Calls & Notifications)"
        PowerSaveMode.SUPER_EXTREME -> "Phone communication: Disconnected (Max Power Saving)"
    }

    fun getEstimatedHours(batteryPercent: Int, mode: PowerSaveMode): Int {
        val pct = batteryPercent.coerceIn(1, 100)
        return when (mode) {
            PowerSaveMode.NORMAL -> (pct * 0.24f).roundToInt().coerceAtLeast(1)        // ~24 hrs at 100%
            PowerSaveMode.EXTREME -> (pct * 0.72f).roundToInt().coerceAtLeast(2)       // ~72 hrs at 100% (3x)
            PowerSaveMode.SUPER_EXTREME -> (pct * 2.0f).roundToInt().coerceAtLeast(4) // ~200 hrs at 100% (8+ days)
        }
    }
}

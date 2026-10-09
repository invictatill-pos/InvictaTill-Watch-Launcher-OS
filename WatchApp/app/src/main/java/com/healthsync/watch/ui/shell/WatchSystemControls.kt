package com.healthsync.watch.ui.shell

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** Ordinary Android controls. Restricted firmware controls always use the watch's settings. */
class WatchSystemControls(private val activity: AppCompatActivity) {
    data class State(val enabled: Boolean?, val detail: String, val changing: Boolean = false)
    private val wifi get() = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val notifications get() = activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val audio get() = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun battery(): String = runCatching {
        val value = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = value?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = value?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return@runCatching "Battery —"
        val percent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
        val plugged = (value?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        "$percent%${if (plugged) " · charging" else ""}"
    }.getOrDefault("Battery —")

    fun wifiState(): State = runCatching {
        val state = wifi?.wifiState
        val connectivity = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivity?.activeNetwork
        val capabilities = network?.let { connectivity?.getNetworkCapabilities(it) }
        when (state) {
            WifiManager.WIFI_STATE_ENABLED -> State(true,
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) "Connected" else "On")
            WifiManager.WIFI_STATE_DISABLED -> State(false, "Off")
            WifiManager.WIFI_STATE_ENABLING -> State(false, "Starting", true)
            WifiManager.WIFI_STATE_DISABLING -> State(true, "Stopping", true)
            else -> State(null, "Settings")
        }
    }.getOrDefault(State(null, "Settings"))

    @SuppressLint("MissingPermission")
    fun bluetoothState(): State {
        if (!bluetoothPermissionGranted()) return State(null, "Settings")
        return runCatching {
            when (bluetoothAdapter()?.state) {
                BluetoothAdapter.STATE_ON -> State(true, "On")
                BluetoothAdapter.STATE_OFF -> State(false, "Off")
                BluetoothAdapter.STATE_TURNING_ON -> State(false, "Starting", true)
                BluetoothAdapter.STATE_TURNING_OFF -> State(true, "Stopping", true)
                else -> State(null, "Unavailable")
            }
        }.getOrDefault(State(null, "Settings"))
    }

    fun dndState(): State = runCatching {
        when (notifications?.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> State(false, "Off")
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> State(true, "Priority")
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> State(true, "Alarms only")
            NotificationManager.INTERRUPTION_FILTER_NONE -> State(true, "On")
            else -> State(null, "Settings")
        }
    }.getOrDefault(State(null, "Settings"))

    fun airplaneState(): State = runCatching {
        val on = Settings.Global.getInt(activity.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        State(on, if (on) "On" else "Off")
    }.getOrDefault(State(null, "Settings"))

    fun saverState(): State = runCatching {
        val manager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val on = manager?.isPowerSaveMode ?: return@runCatching State(null, "Settings")
        State(on, if (on) "On" else "Off")
    }.getOrDefault(State(null, "Settings"))

    /** Old Android permits a direct toggle. Current Android supplies its consent/settings UI. */
    @Suppress("DEPRECATION")
    fun toggleWifi() {
        if (wifiState().changing) return
        if (Build.VERSION.SDK_INT < 29 && permissionGranted(Manifest.permission.CHANGE_WIFI_STATE)) {
            val changed = runCatching { wifi?.setWifiEnabled(wifi?.isWifiEnabled != true) == true }.getOrDefault(false)
            if (changed) return
        }
        openWifiSettings()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun toggleBluetooth() {
        if (bluetoothState().changing) return
        if (Build.VERSION.SDK_INT < 33 && bluetoothPermissionGranted()) {
            val changed = runCatching {
                val adapter = bluetoothAdapter() ?: return@runCatching false
                if (adapter.isEnabled) adapter.disable() else adapter.enable()
            }.getOrDefault(false)
            if (changed) return
        }
        openBluetoothSettings()
    }

    fun toggleDnd() {
        val manager = notifications
        if (manager == null) {
            openSoundSettings()
            return
        }
        if (!runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)) {
            launch(listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                Intent("android.settings.ZEN_MODE_SETTINGS"), Intent(Settings.ACTION_SOUND_SETTINGS)))
            return
        }
        runCatching {
            manager.setInterruptionFilter(if (dndState().enabled == true)
                NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }.onFailure { openSoundSettings() }
    }

    fun mediaVolumePercent(): String = runCatching {
        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        if (max <= 0) "Settings" else "${((audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) * 100f / max).roundToInt()}%"
    }.getOrDefault("Settings")

    fun volume(stream: Int): Int = runCatching { audio?.getStreamVolume(stream) ?: 0 }.getOrDefault(0)
    fun maxVolume(stream: Int): Int = runCatching { audio?.getStreamMaxVolume(stream) ?: 0 }.getOrDefault(0)

    /** Ringer changes that cross a DND boundary can be denied; leave the slider recoverable. */
    fun setVolume(stream: Int, value: Int): Boolean = runCatching {
        val manager = audio ?: return@runCatching false
        manager.setStreamVolume(stream, value.coerceIn(0, manager.getStreamMaxVolume(stream)), 0)
        true
    }.getOrElse {
        message("Use Android sound settings for this volume level.")
        false
    }

    fun openWifiSettings() {
        val choices = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= 29) choices.add(Intent(Settings.Panel.ACTION_WIFI))
        choices.add(Intent(Settings.ACTION_WIFI_SETTINGS))
        choices.add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        launch(choices)
    }

    fun openBluetoothSettings() = launch(listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
    fun openSoundSettings() = launch(listOf(Intent(Settings.ACTION_SOUND_SETTINGS)))
    fun openAirplaneSettings() = launch(listOf(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
    fun openSaverSettings() = launch(listOf(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent("android.settings.BATTERY_SETTINGS"),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
    fun openAndroidSettings() = launch(emptyList())

    private fun bluetoothAdapter() = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private fun bluetoothPermissionGranted() = Build.VERSION.SDK_INT < 31 || permissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
    private fun permissionGranted(permission: String) = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun launch(choices: List<Intent>): Boolean {
        for (intent in choices + Intent(Settings.ACTION_SETTINGS)) {
            try {
                activity.startActivity(intent)
                return true
            } catch (_: RuntimeException) {
                // Firmware frequently omits some Android settings activities.
            }
        }
        message("This control is unavailable in the watch firmware.")
        return false
    }

    private fun message(value: String) = Toast.makeText(activity, value, Toast.LENGTH_SHORT).show()
}

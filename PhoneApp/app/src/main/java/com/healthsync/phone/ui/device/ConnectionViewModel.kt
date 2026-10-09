package com.healthsync.phone.ui.device

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthsync.phone.data.model.ConnectionInfo
import com.healthsync.phone.service.BluetoothSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val hasBluetoothPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    val bluetoothEnabled: Boolean
        get() = try {
            hasBluetoothPermission && (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
        } catch (_: SecurityException) { false }

    /** Live connection state from the BT service */
    val connectionState: StateFlow<ConnectionInfo> = BluetoothSyncService.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionInfo())

    /** All bonded (paired) Bluetooth devices — requires BLUETOOTH_CONNECT on API 31+ */
    val pairedDevices: List<BluetoothDevice>
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) return emptyList()
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return try { manager?.adapter?.bondedDevices?.toList()?.sortedBy { deviceName(it) } ?: emptyList() }
            catch (_: SecurityException) { emptyList() }
        }

    /** Phone's own Bluetooth name */
    val phoneName: String
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) return "Unknown"
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return try { manager?.adapter?.name ?: Build.MODEL }
            catch (_: SecurityException) { Build.MODEL }
        }

    /** Send a RECONNECT command to the running service */
    fun reconnect() {
        _errorMessage.value = null
        if (!hasBluetoothPermission) {
            _errorMessage.value = "Allow Nearby devices to connect your watch."
            return
        }
        if (!bluetoothEnabled) {
            _errorMessage.value = "Turn on Bluetooth in Android settings."
            return
        }
        try {
            ContextCompat.startForegroundService(context,
                Intent(context, BluetoothSyncService::class.java).apply {
                    action = BluetoothSyncService.ACTION_RECONNECT
                }
            )
        } catch (e: Exception) { _errorMessage.value = "Connection could not start. Reopen the app and try again." }
    }

    /** Send a DISCONNECT command to the running service */
    fun disconnect() {
        try {
            context.startService(
                Intent(context, BluetoothSyncService::class.java).apply {
                    action = BluetoothSyncService.ACTION_DISCONNECT
                }
            )
        } catch (e: Exception) { _errorMessage.value = "Unable to disconnect. Please try again." }
    }

    fun deviceName(device: BluetoothDevice): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) device.address
            else device.name ?: device.address
        } catch (e: Exception) { device.address }
    }
}

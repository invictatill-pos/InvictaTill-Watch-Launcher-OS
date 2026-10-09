package com.healthsync.phone.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthsync.phone.data.update.AppUpdateManager
import com.healthsync.phone.data.update.AppVersionInfo
import com.healthsync.phone.data.update.UpdateManifest
import com.healthsync.phone.data.update.UpdateStatus
import com.healthsync.phone.service.BluetoothSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val _manifest = MutableStateFlow<UpdateManifest?>(null)
    val manifest: StateFlow<UpdateManifest?> = _manifest.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _dialogUpdateInfo = MutableStateFlow<AppVersionInfo?>(null)
    val dialogUpdateInfo: StateFlow<AppVersionInfo?> = _dialogUpdateInfo.asStateFlow()

    private val _isWatchUpdate = MutableStateFlow(false)
    val isWatchUpdate: StateFlow<Boolean> = _isWatchUpdate.asStateFlow()

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _status.value = UpdateStatus.Checking
            val fetched = AppUpdateManager.fetchManifest()
            _manifest.value = fetched
            if (fetched == null) {
                if (!silent) _status.value = UpdateStatus.Error("Unable to reach update server.")
                return@launch
            }

            val context = getApplication<Application>()
            val currentPhoneCode = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= 28) pInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pInfo.versionCode
            } catch (_: Exception) { 0 }

            // 1. Check Phone App update
            if (fetched.phone.versionCode > currentPhoneCode) {
                _dialogUpdateInfo.value = fetched.phone
                _isWatchUpdate.value = false
                _status.value = UpdateStatus.UpdateAvailable(fetched.phone, isWatch = false)
                _showDialog.value = true
                return@launch
            }

            // 2. Check Watch App update if phone is current
            val currentWatchCode = 16 // baseline Kolabee watch firmware
            if (fetched.watch.versionCode > currentWatchCode) {
                _dialogUpdateInfo.value = fetched.watch
                _isWatchUpdate.value = true
                _status.value = UpdateStatus.UpdateAvailable(fetched.watch, isWatch = true)
                if (!silent) {
                    _showDialog.value = true
                }
                return@launch
            }

            if (!silent) {
                _status.value = UpdateStatus.UpToDate
            } else {
                _status.value = UpdateStatus.Idle
            }
        }
    }

    fun promptUpdate(info: AppVersionInfo, isWatch: Boolean) {
        _dialogUpdateInfo.value = info
        _isWatchUpdate.value = isWatch
        _status.value = UpdateStatus.UpdateAvailable(info, isWatch)
        _showDialog.value = true
    }

    fun startUpdate(context: Context) {
        val info = _dialogUpdateInfo.value ?: return
        val isWatch = _isWatchUpdate.value

        viewModelScope.launch {
            if (isWatch) {
                if (!BluetoothSyncService.connectionState.value.isConnected) {
                    _status.value = UpdateStatus.Error("Please connect your watch via Bluetooth before sending update.")
                    return@launch
                }

                _status.value = UpdateStatus.Downloading(0, 0, 0)
                val file = AppUpdateManager.downloadApk(context, info.apkUrl, "watch-update-${info.versionName}.apk") { pct, dl, total ->
                    _status.value = UpdateStatus.Downloading(pct, dl, total)
                }

                if (file == null) {
                    _status.value = UpdateStatus.Error("Failed to download watch update package.")
                    return@launch
                }

                _status.value = UpdateStatus.TransferringToWatch(0, 0, 100)
                val success = AppUpdateManager.streamApkToWatchViaBluetooth(file, info.versionName, info.versionCode) { pct, current, total ->
                    _status.value = UpdateStatus.TransferringToWatch(pct, current, total)
                }

                if (success) {
                    _showDialog.value = false
                    _status.value = UpdateStatus.Idle
                } else {
                    _status.value = UpdateStatus.Error("Bluetooth transmission interrupted.")
                }
            } else {
                _status.value = UpdateStatus.Downloading(0, 0, 0)
                val file = AppUpdateManager.downloadApk(context, info.apkUrl, "phone-update-${info.versionName}.apk") { pct, dl, total ->
                    _status.value = UpdateStatus.Downloading(pct, dl, total)
                }

                if (file == null) {
                    _status.value = UpdateStatus.Error("Failed to download phone update package.")
                    return@launch
                }

                _status.value = UpdateStatus.ReadyToInstall(file)
                val launched = AppUpdateManager.installApk(context, file)
                if (launched) {
                    _showDialog.value = false
                    _status.value = UpdateStatus.Idle
                } else {
                    _status.value = UpdateStatus.Error("Could not launch package installer.")
                }
            }
        }
    }

    fun dismissDialog() {
        _showDialog.value = false
        if (_status.value !is UpdateStatus.Downloading && _status.value !is UpdateStatus.TransferringToWatch) {
            _status.value = UpdateStatus.Idle
        }
    }
}

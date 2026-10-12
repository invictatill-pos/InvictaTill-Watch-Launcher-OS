package com.healthsync.phone.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.update.*
import com.healthsync.phone.service.BluetoothSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status = _status.asStateFlow()
    private val _manifest = MutableStateFlow<UpdateManifest?>(null)
    val manifest = _manifest.asStateFlow()
    private val _phoneUpdateInfo = MutableStateFlow<AppVersionInfo?>(null)
    val phoneUpdateInfo = _phoneUpdateInfo.asStateFlow()
    private val _watchUpdateInfo = MutableStateFlow<AppVersionInfo?>(null)
    val watchUpdateInfo = _watchUpdateInfo.asStateFlow()
    private val _showDialog = MutableStateFlow(false)
    val showDialog = _showDialog.asStateFlow()
    private val _dialogUpdateInfo = MutableStateFlow<AppVersionInfo?>(null)
    val dialogUpdateInfo = _dialogUpdateInfo.asStateFlow()
    private val _isWatchUpdate = MutableStateFlow(false)
    val isWatchUpdate = _isWatchUpdate.asStateFlow()
    private var downloadedPhone: Pair<AppVersionInfo, File>? = null

    private fun busy() = _status.value is UpdateStatus.Checking || _status.value is UpdateStatus.Downloading ||
        _status.value is UpdateStatus.TransferringToWatch

    private fun watchCode(): Int = BluetoothSyncService.connectedWatchVersionCode.value ?: PhonePreferences(getApplication()).lastKnownWatchVersionCode

    @Suppress("DEPRECATION")
    private fun phoneCode(): Int {
        val context = getApplication<Application>()
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode.toInt() else installed.versionCode
    }

    fun checkForUpdates(silent: Boolean = false) = check(silent, watchOnly = false, pushWatch = false, context = null)
    fun checkWatchUpdateManually() = check(silent = false, watchOnly = true, pushWatch = false, context = null)

    private fun check(silent: Boolean, watchOnly: Boolean, pushWatch: Boolean, context: Context?) {
        if (busy()) return
        _status.value = UpdateStatus.Checking
        viewModelScope.launch {
            val fetched = AppUpdateManager.fetchManifest()
            if (fetched == null) {
                _status.value = if (silent) UpdateStatus.Idle else UpdateStatus.Error(AppUpdateManager.lastError ?: "Unable to reach update server")
                return@launch
            }
            _manifest.value = fetched
            _phoneUpdateInfo.value = fetched.phone.takeIf { it.versionCode > phoneCode() }
            _watchUpdateInfo.value = fetched.watch.takeIf { it.versionCode > watchCode() }
            val info = if (pushWatch) fetched.watch else if (watchOnly) _watchUpdateInfo.value else _phoneUpdateInfo.value ?: _watchUpdateInfo.value
            if (info == null) {
                _status.value = if (silent) UpdateStatus.Idle else UpdateStatus.UpToDate
                return@launch
            }
            val isWatch = pushWatch || watchOnly || _phoneUpdateInfo.value == null
            _status.value = UpdateStatus.Idle
            select(info, isWatch, show = !silent || pushWatch)
            if (pushWatch) startUpdate(context)
        }
    }

    private fun select(info: AppVersionInfo, isWatch: Boolean, show: Boolean = true) {
        _dialogUpdateInfo.value = info
        _isWatchUpdate.value = isWatch
        _status.value = UpdateStatus.UpdateAvailable(info, isWatch)
        _showDialog.value = show
    }

    fun updatePhone(context: Context? = null) {
        if (busy()) return
        val info = _phoneUpdateInfo.value ?: return
        select(info, false)
        startUpdate(context)
    }

    fun updateWatch(context: Context? = null) {
        if (busy()) return
        val info = _watchUpdateInfo.value ?: return
        select(info, true)
        startUpdate(context)
    }

    fun forcePushWatchUpdate(context: Context? = null) {
        if (busy()) return
        check(silent = false, watchOnly = true, pushWatch = true, context = context)
    }

    fun promptUpdate(info: AppVersionInfo, isWatch: Boolean) {
        if (!busy()) select(info, isWatch)
    }

    fun startUpdate(context: Context? = null) {
        if (busy()) return
        val targetContext = context ?: getApplication<Application>()
        val info = _dialogUpdateInfo.value ?: return
        val isWatch = _isWatchUpdate.value
        if (isWatch && !BluetoothSyncService.connectionState.value.isConnected) {
            _status.value = UpdateStatus.Error("Connect your watch before sending its update")
            return
        }
        if (isWatch && info.versionCode < watchCode()) {
            _status.value = UpdateStatus.Error("The connected watch already has a newer app version")
            return
        }
        val savedPhone = downloadedPhone?.takeIf { !isWatch && it.first == info && it.second.exists() }
        if (savedPhone != null) {
            _status.value = UpdateStatus.ReadyToInstall(savedPhone.second)
            if (AppUpdateManager.installApk(targetContext, savedPhone.second)) _showDialog.value = false
            return
        }
        _status.value = UpdateStatus.Downloading(0, 0, 0)
        viewModelScope.launch {
            val file = AppUpdateManager.downloadApk(targetContext, info, isWatch) { percent, downloaded, total ->
                _status.value = UpdateStatus.Downloading(percent, downloaded, total)
            }
            if (file == null) {
                _status.value = UpdateStatus.Error(AppUpdateManager.lastError ?: "Could not download verified update")
                return@launch
            }
            if (isWatch) {
                val expectsVerification = (BluetoothSyncService.connectedWatchVersionCode.value ?: 0) >= 26
                _status.value = UpdateStatus.TransferringToWatch(0, 0, 1)
                val sent = AppUpdateManager.streamApkToWatchViaBluetooth(file, info.versionName, info.versionCode) { percent, chunk, total ->
                    _status.value = UpdateStatus.TransferringToWatch(percent, chunk, total)
                }
                _status.value = if (sent) UpdateStatus.WatchInstallRequired(expectsVerification)
                    else UpdateStatus.Error(AppUpdateManager.lastError ?: "Watch update transfer interrupted")
            } else {
                downloadedPhone = info to file
                _status.value = UpdateStatus.ReadyToInstall(file)
                if (AppUpdateManager.installApk(targetContext, file)) _showDialog.value = false
            }
        }
    }

    fun dismissDialog() {
        if (busy()) return
        _showDialog.value = false
        if (_status.value !is UpdateStatus.ReadyToInstall && _status.value !is UpdateStatus.WatchInstallRequired) _status.value = UpdateStatus.Idle
    }
}

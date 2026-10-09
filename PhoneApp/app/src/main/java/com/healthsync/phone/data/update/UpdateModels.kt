package com.healthsync.phone.data.update

import java.io.File

data class AppVersionInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val changelog: String = "",
    val forceUpdate: Boolean = false,
    val sha256: String = ""
)

data class UpdateManifest(
    val phone: AppVersionInfo = AppVersionInfo(),
    val watch: AppVersionInfo = AppVersionInfo()
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val info: AppVersionInfo, val isWatch: Boolean = false) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class TransferringToWatch(val percent: Int, val currentChunk: Int, val totalChunks: Int) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

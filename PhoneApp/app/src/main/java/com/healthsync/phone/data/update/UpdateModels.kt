package com.healthsync.phone.data.update

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.File

@Keep
data class AppVersionInfo(
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("versionName") val versionName: String = "",
    @SerializedName("apkUrl") val apkUrl: String = "",
    @SerializedName("changelog") val changelog: String = "",
    @SerializedName("forceUpdate") val forceUpdate: Boolean = false,
    @SerializedName("sha256") val sha256: String = ""
)

@Keep
data class UpdateManifest(
    @SerializedName("phone") val phone: AppVersionInfo = AppVersionInfo(),
    @SerializedName("watch") val watch: AppVersionInfo = AppVersionInfo()
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

package com.healthsync.watch.update

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.healthsync.watch.data.*
import com.healthsync.watch.service.BluetoothClientService
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** App updates retain Android's installation confirmation and verify bytes before installation. */
object WatchUpdateManager {
    private const val TAG = "WatchUpdateManager"
    private const val CHANNEL_ID = "ota_update_channel"
    private const val NOTIF_ID = 3001
    const val DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/main/version.json"
    const val CDN_MANIFEST_URL = "https://cdn.jsdelivr.net/gh/invictatill-pos/InvictaTill-Watch-Launcher-OS@main/version.json"
    const val FALLBACK_MANIFEST_URL = CDN_MANIFEST_URL
    private const val RELEASE_MANIFEST_URL = "https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/releases/latest/download/version.json"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private var transfer: OtaStartPayload? = null
    private var output: RandomAccessFile? = null
    private var partialFile: File? = null
    private val received = mutableSetOf<Int>()
    private var lastPercent = -5
    private var directActive = false
    private var transferChunkSize: Int? = null
    private var lastTransferActivityElapsed = 0L
    private var transferTimeout: Job? = null

    @Synchronized
    fun onOtaStart(context: Context, payload: OtaStartPayload) {
        if (directActive) {
            sendProgress(payload, "ERROR", "A Wi-Fi update is already in progress")
            return
        }
        closeTransfer()
        transfer = payload
        ensureChannel(context)
        if (!OtaTransferPolicy.validStart(payload)) {
            failTransfer(context, "Invalid update metadata")
            return
        }
        try {
            val dir = File(context.cacheDir, "ota_updates").apply { mkdirs() }
            val file = File(dir, "watch-update.apk.part")
            partialFile = file
            output = RandomAccessFile(file, "rw").apply { setLength(payload.fileSize) }
            lastTransferActivityElapsed = SystemClock.elapsedRealtime()
            transferTimeout = scope.launch {
                while (isActive) {
                    delay(30_000L)
                    synchronized(this@WatchUpdateManager) {
                        if (transfer === payload && SystemClock.elapsedRealtime() - lastTransferActivityElapsed >= 120_000L)
                            failTransfer(context, "Update transfer timed out. Reconnect the phone and retry.")
                    }
                }
            }
            updateProgressNotification(context, "Receiving update from phone…", 0, payload.totalChunks)
            sendProgress(payload, "RECEIVING", "Receiving verified app update")
        } catch (error: Exception) { failTransfer(context, error.message ?: "Could not prepare update storage") }
    }

    @Synchronized
    fun onOtaChunk(context: Context, payload: OtaChunkPayload) {
        val start = transfer ?: return
        val file = output ?: return
        // Frames from an earlier cancelled transfer must not corrupt the active file.
        if (!OtaTransferPolicy.matchesTransfer(start, payload.transferId)) return
        try {
            require(payload.dataBase64.length <= 12_000) { "Update chunk exceeds size limit" }
            val bytes = Base64.decode(payload.dataBase64, Base64.NO_WRAP)
            require(OtaTransferPolicy.validChunk(start, payload, bytes.size, transferChunkSize)) { "Invalid update chunk or offset" }
            transferChunkSize = payload.chunkSize
            lastTransferActivityElapsed = SystemClock.elapsedRealtime()
            if (payload.chunkIndex in received) return
            file.seek(payload.chunkIndex.toLong() * payload.chunkSize)
            file.write(bytes)
            received.add(payload.chunkIndex)
            val percent = received.size * 100 / start.totalChunks
            if (percent >= lastPercent + 5 || received.size == start.totalChunks) {
                lastPercent = percent
                updateProgressNotification(context, "Receiving update: $percent%", received.size, start.totalChunks)
                sendProgress(start, "RECEIVING", "Receiving update")
            }
        } catch (error: Exception) { failTransfer(context, error.message ?: "Could not write update chunk") }
    }

    @Synchronized
    fun onOtaComplete(context: Context, payload: OtaCompletePayload) {
        val start = transfer ?: return
        val partial = partialFile ?: return
        if (!OtaTransferPolicy.matchesTransfer(start, payload.transferId)) return
        try {
            require(payload.success && received.size == start.totalChunks) { "Incomplete update received; retry from phone" }
            require(payload.sha256.equals(start.sha256, true)) { "Transfer verification metadata changed" }
            output?.fd?.sync()
            output?.close()
            output = null
            require(partial.length() == start.fileSize && calculateSha256(partial).equals(start.sha256, true)) { "Update checksum did not match" }
            verifyPackage(context, partial, start.versionCode, start.versionName)
            val ready = File(partial.parentFile, "watch-update.apk")
            ready.delete()
            require(partial.renameTo(ready)) { "Could not save verified update" }
            sendProgress(start, "READY", "Verified. Confirm installation on the watch.")
            closeTransfer()
            showInstallReadyNotification(context, ready)
            scope.launch(Dispatchers.Main) { installApk(context, ready) }
        } catch (error: Exception) { failTransfer(context, error.message ?: "Watch could not verify update") }
    }

    private fun sendProgress(start: OtaStartPayload, status: String, message: String) {
        BluetoothClientService.sendRawMsg(SyncMessage(MessageType.OTA_PROGRESS, payload = gson.toJson(
            OtaProgressPayload(received.size, start.totalChunks, if (start.totalChunks > 0) received.size * 100 / start.totalChunks else 0,
                start.transferId.orEmpty(), status, message))), gson)
    }

    private fun closeTransfer() {
        transferTimeout?.cancel(); transferTimeout = null
        try { output?.close() } catch (_: Exception) { }
        output = null
        partialFile?.delete()
        partialFile = null
        transfer = null
        received.clear()
        lastPercent = -5
        transferChunkSize = null
    }

    private fun failTransfer(context: Context, message: String) {
        transfer?.let { sendProgress(it, "ERROR", message) }
        closeTransfer()
        showErrorNotification(context, message)
    }

    fun installApk(context: Context, apkFile: File): Boolean = try {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            showInstallReadyNotification(context, apkFile)
            false
        } else {
            context.startActivity(installIntent(context, apkFile))
            true
        }
    } catch (error: Exception) {
        Log.w(TAG, "Android installer could not open: ${error.message}")
        showInstallReadyNotification(context, apkFile)
        false
    }

    fun checkForDirectUpdate(context: Context, onResult: (message: String) -> Unit) {
        synchronized(this) {
            if (directActive || output != null) {
                onResult("An update is already in progress")
                return
            }
            directActive = true
        }
        scope.launch {
            try {
                var info: org.json.JSONObject? = null
                for (candidate in listOf(DEFAULT_MANIFEST_URL, CDN_MANIFEST_URL, RELEASE_MANIFEST_URL)) {
                    currentCoroutineContext().ensureActive()
                    var connection: HttpURLConnection? = null
                    try {
                        connection = openConnection("$candidate?t=${System.currentTimeMillis()}")
                        val json = connection.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(out.size() + count <= 256 * 1024) { "Update metadata exceeds size limit" }
                                out.write(buffer, 0, count)
                            }
                            out.toString("UTF-8")
                        }
                        val candidateInfo = org.json.JSONObject(json).getJSONObject("watch")
                        require(candidateInfo.getInt("versionCode") > 0 && candidateInfo.getString("versionName").isNotBlank() &&
                            Regex("[a-fA-F0-9]{64}").matches(candidateInfo.getString("sha256"))) { "Incomplete update metadata" }
                        require(URL(candidateInfo.getString("apkUrl")).protocol == "https") { "Update requires HTTPS" }
                        info = candidateInfo
                        break
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (error: Exception) { Log.w(TAG, "Update source unavailable: ${error.message}")
                    } finally { connection?.disconnect() }
                }
                val remote = info ?: throw java.io.IOException("Could not check updates. Try Wi-Fi again or use the phone companion.")
                @Suppress("DEPRECATION")
                val installed = context.packageManager.getPackageInfo(context.packageName, 0)
                @Suppress("DEPRECATION")
                val currentCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
                if (remote.getInt("versionCode") <= currentCode) {
                    withContext(Dispatchers.Main) { onResult("Watch is up to date (v${installed.versionName})") }
                } else {
                    withContext(Dispatchers.Main) { onResult("Downloading v${remote.getString("versionName")}…") }
                    downloadDirect(context, remote)
                    withContext(Dispatchers.Main) { onResult("Update verified. Confirm installation in Android.") }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                showErrorNotification(context, error.message ?: "Update failed")
                withContext(Dispatchers.Main) { onResult(error.message ?: "Update failed") }
            } finally { synchronized(this@WatchUpdateManager) { directActive = false } }
        }
    }

    private suspend fun downloadDirect(context: Context, info: org.json.JSONObject) {
        ensureChannel(context)
        val dir = File(context.cacheDir, "ota_updates").apply { mkdirs() }
        val partial = File(dir, "watch-direct.apk.part")
        val connection = openConnection(info.getString("apkUrl"))
        try {
            val totalLength = connection.contentLength.toLong()
            require(totalLength <= OtaTransferPolicy.MAX_APK_BYTES) { "Update package exceeds size limit" }
            var downloaded = 0L
            var lastPercent = -1
            updateProgressNotification(context, "Downloading update…", 0, 100)
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        require(downloaded <= OtaTransferPolicy.MAX_APK_BYTES) { "Update package exceeds size limit" }
                        output.write(buffer, 0, count)
                        val percent = if (totalLength > 0) (downloaded * 100 / totalLength).toInt().coerceIn(0, 99) else 0
                        if (percent >= lastPercent + 5) {
                            lastPercent = percent
                            updateProgressNotification(context, "Downloading update: $percent%", percent, if (totalLength > 0) 100 else 0)
                        }
                    }
                    output.fd.sync()
                }
            }
            require(downloaded > 0 && (totalLength <= 0 || downloaded == totalLength)) { "Update download was incomplete" }
            require(calculateSha256(partial).equals(info.getString("sha256"), true)) { "Update checksum did not match" }
            verifyPackage(context, partial, info.getInt("versionCode"), info.getString("versionName"))
            val ready = File(dir, "watch-update.apk")
            ready.delete()
            require(partial.renameTo(ready)) { "Could not save verified update" }
            showInstallReadyNotification(context, ready)
            withContext(Dispatchers.Main) { installApk(context, ready) }
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun openConnection(address: String): HttpURLConnection {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https" && url.userInfo == null) { "Update requires a secure HTTPS address" }
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "HealthSync-Watch")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val status = try { connection.responseCode } catch (error: Exception) {
                connection.disconnect(); throw error
            }
            if (status in listOf(301, 302, 303, 307, 308)) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                require(!next.isNullOrBlank()) { "Update redirect is missing its destination" }
                url = URL(url, next)
            } else {
                if (status !in 200..299) {
                    connection.disconnect()
                    throw java.io.IOException("Update server returned HTTP $status")
                }
                return connection
            }
        }
        throw java.io.IOException("Too many update redirects")
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(context: Context, file: File, versionCode: Int, versionName: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IllegalArgumentException("Downloaded file is not an Android app")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == context.packageName && code == versionCode.toLong() && archive.versionName == versionName) {
            "Update does not match the advertised watch app/version"
        }
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        require(code >= installedCode) { "An older watch version cannot replace the installed app" }
        val actual = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val current = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        require(!actual.isNullOrEmpty() && !current.isNullOrEmpty() && actual.toSet() == current.toSet()) {
            "Update signing certificate differs from the installed watch app"
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun installIntent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun updateProgressNotification(context: Context, text: String, current: Int, max: Int) = notify(context,
        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("HealthSync update").setContentText(text).setProgress(max, current, max <= 0).setOngoing(true).setSilent(true))

    private fun showInstallReadyNotification(context: Context, file: File) = notify(context,
        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Watch update verified").setContentText("Allow updates if prompted, then tap to install")
            .setContentIntent(PendingIntent.getActivity(context, 0, installIntent(context, file), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true))

    private fun showErrorNotification(context: Context, error: String) {
        ensureChannel(context)
        notify(context, NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Update failed").setContentText(error).setAutoCancel(true))
    }

    private fun notify(context: Context, builder: NotificationCompat.Builder) {
        try { context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, builder.build()) } catch (_: Exception) { }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

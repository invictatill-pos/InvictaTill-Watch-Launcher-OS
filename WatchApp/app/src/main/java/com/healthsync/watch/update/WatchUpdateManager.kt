package com.healthsync.watch.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.healthsync.watch.data.MessageType
import com.healthsync.watch.data.OtaChunkPayload
import com.healthsync.watch.data.OtaCompletePayload
import com.healthsync.watch.data.OtaProgressPayload
import com.healthsync.watch.data.OtaStartPayload
import com.healthsync.watch.data.SyncMessage
import com.healthsync.watch.service.BluetoothClientService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Manages firmware and app updates on the Standalone Kolabee U8 Ultra Android watch.
 * Supports Bluetooth chunked streaming from Phone companion, as well as direct Wi-Fi updates.
 */
object WatchUpdateManager {
    private const val TAG = "WatchUpdateManager"
    private const val CHANNEL_ID = "ota_update_channel"
    private const val NOTIF_ID = 3001
    const val DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/main/version.json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private var activeOutputFile: File? = null
    private var fileOutputStream: FileOutputStream? = null
    private var expectedChunks: Int = 0
    private var receivedChunks: Int = 0
    private var expectedSha256: String = ""
    private var targetVersionName: String = ""

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "System Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Watch app OTA updates and installation"
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** Called when Phone begins OTA transmission */
    @Synchronized
    fun onOtaStart(context: Context, payload: OtaStartPayload) {
        Log.i(TAG, "OTA transfer started: version ${payload.versionName} (${payload.fileSize} bytes, ${payload.totalChunks} chunks)")
        ensureChannel(context)

        // Close any stale stream
        try { fileOutputStream?.close() } catch (_: Exception) {}
        fileOutputStream = null

        expectedChunks = payload.totalChunks
        receivedChunks = 0
        expectedSha256 = payload.sha256
        targetVersionName = payload.versionName

        try {
            val otaDir = File(context.cacheDir, "ota_updates").apply { mkdirs() }
            val target = File(otaDir, "watch-update.apk")
            if (target.exists()) target.delete()

            activeOutputFile = target
            fileOutputStream = FileOutputStream(target)

            updateProgressNotification(context, "Receiving update from phone…", 0, expectedChunks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OTA file: ${e.message}", e)
        }
    }

    /** Called when an individual 12KB chunk arrives via Bluetooth */
    @Synchronized
    fun onOtaChunk(context: Context, payload: OtaChunkPayload) {
        val stream = fileOutputStream ?: return
        try {
            val bytes = Base64.decode(payload.dataBase64, Base64.NO_WRAP)
            stream.write(bytes)
            receivedChunks++

            val total = if (expectedChunks > 0) expectedChunks else payload.totalChunks
            val pct = if (total > 0) ((receivedChunks * 100) / total).coerceIn(0, 100) else 0

            // Update watch UI and phone duplex progress periodically
            if (receivedChunks % 10 == 0 || receivedChunks == total) {
                updateProgressNotification(context, "Receiving update: $pct%", receivedChunks, total)
                val progressPayload = OtaProgressPayload(receivedChunks, total, pct)
                BluetoothClientService.sendRawMsg(
                    SyncMessage(MessageType.OTA_PROGRESS, payload = gson.toJson(progressPayload)),
                    gson
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing OTA chunk ${payload.chunkIndex}: ${e.message}", e)
        }
    }

    /** Called when transmission completes */
    @Synchronized
    fun onOtaComplete(context: Context, payload: OtaCompletePayload) {
        Log.i(TAG, "OTA transfer complete signal received")
        try {
            fileOutputStream?.flush()
            fileOutputStream?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing OTA stream: ${e.message}")
        }
        fileOutputStream = null

        val file = activeOutputFile
        if (file == null || !file.exists() || file.length() == 0L) {
            showErrorNotification(context, "Update failed: Incomplete file received")
            return
        }

        // Verify SHA-256 hash if provided
        val expectedHash = if (payload.sha256.isNotBlank()) payload.sha256 else expectedSha256
        if (expectedHash.isNotBlank()) {
            val actualHash = calculateSha256(file)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                Log.e(TAG, "SHA-256 mismatch! Expected: $expectedHash, Actual: $actualHash")
                file.delete()
                showErrorNotification(context, "Update failed: Verification hash mismatch")
                return
            }
            Log.i(TAG, "SHA-256 hash verified successfully: $actualHash")
        }

        // Prompt or launch install
        showInstallReadyNotification(context, file)
        installApk(context, file)
    }

    /** Launches Android PackageInstaller Intent via FileProvider */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched package installer for: ${apkFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer intent: ${e.message}", e)
            false
        }
    }

    /** Checks online manifest if Watch has direct Wi-Fi internet access */
    fun checkForDirectUpdate(context: Context, onResult: (message: String) -> Unit) {
        scope.launch {
            val candidates = listOf(
                DEFAULT_MANIFEST_URL,
                "https://cdn.jsdelivr.net/gh/invictatill-pos/InvictaTill-Watch-Launcher-OS@main/version.json"
            ).distinct()

            var fetchedJson: String? = null
            for (candidate in candidates) {
                try {
                    val url = URL(candidate)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 8_000
                    conn.setRequestProperty("User-Agent", "HealthSync-Watch-App")

                    if (conn.responseCode in 200..299) {
                        fetchedJson = conn.inputStream.bufferedReader().use { it.readText() }
                        break
                    }
                } catch (_: Exception) {}
            }

            if (fetchedJson != null) {
                try {
                    val root = gson.fromJson(fetchedJson, Map::class.java)
                    @Suppress("UNCHECKED_CAST")
                    val watchInfo = root["watch"] as? Map<String, Any>

                    val remoteVersionCode = (watchInfo?.get("versionCode") as? Number)?.toInt() ?: 0
                    val remoteVersionName = watchInfo?.get("versionName") as? String ?: ""
                    val downloadUrl = watchInfo?.get("downloadUrl") as? String ?: ""

                    val currentVersionCode = try {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        if (Build.VERSION.SDK_INT >= 28) pInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pInfo.versionCode
                    } catch (_: Exception) { 0 }

                    if (remoteVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            onResult("New version v$remoteVersionName available! Downloading…")
                        }
                        downloadAndInstallDirectly(context, downloadUrl)
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult("Watch is up to date (v${context.packageManager.getPackageInfo(context.packageName, 0).versionName})")
                        }
                    }
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing manifest: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                onResult("No direct Wi-Fi connection. Updates are received via phone companion.")
            }
        }
    }

    private suspend fun downloadAndInstallDirectly(context: Context, downloadUrl: String) = withContext(Dispatchers.IO) {
        try {
            ensureChannel(context)
            updateProgressNotification(context, "Downloading update…", 0, 100)

            val dir = File(context.cacheDir, "ota_updates").apply { mkdirs() }
            val file = File(dir, "watch-update.apk")
            if (file.exists()) file.delete()

            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val totalLen = conn.contentLength.toLong()
            conn.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalRead: Long = 0
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (totalLen > 0) {
                            val pct = ((totalRead * 100) / totalLen).toInt()
                            if (pct % 10 == 0) {
                                updateProgressNotification(context, "Downloading: $pct%", pct, 100)
                            }
                        }
                    }
                }
            }

            showInstallReadyNotification(context, file)
            withContext(Dispatchers.Main) {
                installApk(context, file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct download failed: ${e.message}", e)
            showErrorNotification(context, "Download failed: ${e.message}")
        }
    }

    private fun updateProgressNotification(context: Context, text: String, current: Int, max: Int) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("HealthSync Update")
                .setContentText(text)
                .setProgress(max, current, max <= 0)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            nm.notify(NOTIF_ID, builder.build())
        } catch (_: Exception) {}
    }

    private fun showInstallReadyNotification(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nm = context.getSystemService(NotificationManager::class.java)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Watch Update Ready")
                .setContentText("Tap here to complete installation")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            nm.notify(NOTIF_ID, builder.build())
        } catch (_: Exception) {}
    }

    private fun showErrorNotification(context: Context, error: String) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Update Failed")
                .setContentText(error)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            nm.notify(NOTIF_ID, builder.build())
        } catch (_: Exception) {}
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

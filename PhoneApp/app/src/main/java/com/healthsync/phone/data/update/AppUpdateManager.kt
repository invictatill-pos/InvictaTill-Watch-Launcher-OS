package com.healthsync.phone.data.update

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.healthsync.phone.data.model.MessageType
import com.healthsync.phone.data.model.OtaChunkPayload
import com.healthsync.phone.data.model.OtaCompletePayload
import com.healthsync.phone.data.model.OtaStartPayload
import com.healthsync.phone.data.model.SyncMessage
import com.healthsync.phone.service.BluetoothSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    const val DEFAULT_MANIFEST_URL = "https://cdn.jsdelivr.net/gh/invictatill-pos/InvictaTill-Watch-Launcher-OS@main/version.json"
    const val FALLBACK_MANIFEST_URL = "https://raw.githubusercontent.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/main/version.json"

    var lastError: String? = null
        private set

    private val gson = Gson()

    /** Fetches remote version manifest from CDN or fallback GitHub URL */
    suspend fun fetchManifest(manifestUrl: String = DEFAULT_MANIFEST_URL): UpdateManifest? = withContext(Dispatchers.IO) {
        lastError = null
        val ts = System.currentTimeMillis()
        val candidates = listOf(
            manifestUrl,
            "https://cdn.jsdelivr.net/gh/invictatill-pos/InvictaTill-Watch-Launcher-OS@latest/version.json?t=$ts",
            "https://raw.githubusercontent.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/main/version.json?t=$ts",
            DEFAULT_MANIFEST_URL,
            FALLBACK_MANIFEST_URL
        ).distinct()

        for (candidate in candidates) {
            try {
                val url = URL(candidate)
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                connection.setRequestProperty("Pragma", "no-cache")
                connection.requestMethod = "GET"

                if (connection.responseCode in 200..299) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    // 1. Try Gson
                    try {
                        val parsed = gson.fromJson(json, UpdateManifest::class.java)
                        if (parsed?.phone != null && parsed.phone.versionCode > 0) {
                            return@withContext parsed
                        }
                    } catch (ge: Exception) {
                        Log.w(TAG, "Gson parse error: ${ge.message}")
                    }

                    // 2. Android framework JSONObject parser (immune to R8 obfuscation)
                    try {
                        val root = org.json.JSONObject(json)
                        val phoneObj = root.optJSONObject("phone")
                        val watchObj = root.optJSONObject("watch")
                        val phoneInfo = AppVersionInfo(
                            versionCode = phoneObj?.optInt("versionCode", 0) ?: 0,
                            versionName = phoneObj?.optString("versionName", "") ?: "",
                            apkUrl = phoneObj?.optString("apkUrl", "") ?: "",
                            changelog = phoneObj?.optString("changelog", "") ?: "",
                            forceUpdate = phoneObj?.optBoolean("forceUpdate", false) ?: false,
                            sha256 = phoneObj?.optString("sha256", "") ?: ""
                        )
                        val watchInfo = AppVersionInfo(
                            versionCode = watchObj?.optInt("versionCode", 0) ?: 0,
                            versionName = watchObj?.optString("versionName", "") ?: "",
                            apkUrl = watchObj?.optString("apkUrl", "") ?: "",
                            changelog = watchObj?.optString("changelog", "") ?: "",
                            forceUpdate = watchObj?.optBoolean("forceUpdate", false) ?: false,
                            sha256 = watchObj?.optString("sha256", "") ?: ""
                        )
                        return@withContext UpdateManifest(phone = phoneInfo, watch = watchInfo)
                    } catch (je: Exception) {
                        Log.w(TAG, "JSONObject parse error: ${je.message}")
                    }
                } else {
                    lastError = "Server HTTP ${connection.responseCode} ($candidate)"
                    Log.w(TAG, "Failed to fetch from $candidate: HTTP ${connection.responseCode}")
                }
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                Log.w(TAG, "Error fetching from $candidate: ${e.message}")
            }
        }
        null
    }

    /** Downloads an APK file from given URL with progress reporting */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        fileName: String,
        onProgress: (percent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = apkUrl
            var connection: HttpURLConnection
            var redirects = 0

            // Follow redirects (GitHub Releases redirect to AWS S3)
            while (true) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "HealthSync-Phone-App")

                val status = connection.responseCode
                if (status in 300..399) {
                    val newUrl = connection.getHeaderField("Location") ?: break
                    currentUrl = newUrl
                    redirects++
                    if (redirects > 5) break
                } else break
            }

            val totalLength = connection.contentLength.toLong()
            val otaDir = File(context.cacheDir, "ota_updates").apply { mkdirs() }
            val targetFile = File(otaDir, fileName)
            if (targetFile.exists()) targetFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloaded: Long = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val pct = if (totalLength > 0) ((downloaded * 100) / totalLength).toInt() else -1
                        onProgress(pct, downloaded, totalLength)
                    }
                }
            }
            return@withContext targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK: ${e.message}", e)
            null
        }
    }

    /** Triggers the Android native PackageInstaller dialog using FileProvider */
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer intent: ${e.message}", e)
            false
        }
    }

    /** Streams a Watch APK chunk-by-chunk to the connected Watch via Bluetooth RFCOMM */
    suspend fun streamApkToWatchViaBluetooth(
        apkFile: File,
        versionName: String,
        versionCode: Int,
        onProgress: (percent: Int, currentChunk: Int, totalChunks: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || apkFile.length() == 0L) return@withContext false
        val bytes = apkFile.readBytes()
        val sha256 = calculateSha256(bytes)
        val chunkSize = 12 * 1024 // 12 KB chunks for smooth RFCOMM throughput
        val totalChunks = (bytes.size + chunkSize - 1) / chunkSize

        // 1. Send OTA_START
        val startPayload = OtaStartPayload(versionName, versionCode, bytes.size.toLong(), totalChunks, sha256)
        if (!BluetoothSyncService.sendDirectSyncMessage(SyncMessage(MessageType.OTA_START, payload = gson.toJson(startPayload)))) {
            Log.w(TAG, "Bluetooth not connected; unable to start OTA")
            return@withContext false
        }
        delay(250)

        // 2. Stream OTA chunks
        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, bytes.size)
            val chunkBytes = bytes.copyOfRange(start, end)
            val base64 = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)
            val chunkPayload = OtaChunkPayload(i, totalChunks, base64)

            val sent = BluetoothSyncService.sendDirectSyncMessage(
                SyncMessage(MessageType.OTA_CHUNK, payload = gson.toJson(chunkPayload))
            )
            if (!sent) {
                Log.e(TAG, "Lost connection during chunk $i of $totalChunks")
                return@withContext false
            }

            val pct = ((i + 1) * 100) / totalChunks
            onProgress(pct, i + 1, totalChunks)
            delay(35) // Bounded rate to keep Bluetooth buffers clean
        }

        // 3. Send OTA_COMPLETE
        val completePayload = OtaCompletePayload(true, sha256)
        BluetoothSyncService.sendDirectSyncMessage(SyncMessage(MessageType.OTA_COMPLETE, payload = gson.toJson(completePayload)))
        Log.i(TAG, "Watch OTA streaming completed successfully: $totalChunks chunks")
        true
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}

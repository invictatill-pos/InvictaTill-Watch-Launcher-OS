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
    const val DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/main/version.json"
    const val CDN_MANIFEST_URL = "https://cdn.jsdelivr.net/gh/invictatill-pos/InvictaTill-Watch-Launcher-OS@main/version.json"
    const val FALLBACK_MANIFEST_URL = "https://cdn.jsdelivr.net/gh/invictatill-pos/InvictaTill-Watch-Launcher-OS@main/version.json"
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/invictatill-pos/InvictaTill-Watch-Launcher-OS/releases/latest"

    var lastError: String? = null
        private set

    private val gson = Gson()

    /** Fetches remote version manifest from raw GitHub, jsDelivr CDN, or GitHub Releases API */
    suspend fun fetchManifest(manifestUrl: String = DEFAULT_MANIFEST_URL): UpdateManifest? = withContext(Dispatchers.IO) {
        lastError = null
        val ts = System.currentTimeMillis()
        val candidates = listOf(
            "$DEFAULT_MANIFEST_URL?t=$ts",
            "$CDN_MANIFEST_URL?t=$ts",
            manifestUrl,
            DEFAULT_MANIFEST_URL,
            CDN_MANIFEST_URL
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

        // 3. Fallback: Query GitHub Releases API directly if raw manifest endpoints were blocked
        try {
            val apiUrl = URL(GITHUB_RELEASES_API)
            val apiConn = apiUrl.openConnection() as HttpURLConnection
            apiConn.connectTimeout = 8_000
            apiConn.readTimeout = 8_000
            apiConn.setRequestProperty("User-Agent", "HealthSync-Phone-App")
            apiConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            if (apiConn.responseCode in 200..299) {
                val apiJson = apiConn.inputStream.bufferedReader().use { it.readText() }
                val releaseObj = org.json.JSONObject(apiJson)
                val tagName = releaseObj.optString("tag_name", "").removePrefix("v")
                val body = releaseObj.optString("body", "")
                val assets = releaseObj.optJSONArray("assets")
                var phoneApkUrl = ""
                var watchApkUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        val dl = asset.optString("browser_download_url", "")
                        if (name.contains("Phone", ignoreCase = true) && name.endsWith(".apk")) phoneApkUrl = dl
                        if (name.contains("Watch", ignoreCase = true) && name.endsWith(".apk")) watchApkUrl = dl
                    }
                }
                if (phoneApkUrl.isNotBlank() || watchApkUrl.isNotBlank()) {
                    val parts = tagName.split(".")
                    val derivedCode = (parts.getOrNull(0)?.toIntOrNull() ?: 2) * 10 + (parts.getOrNull(1)?.toIntOrNull() ?: 4)
                    return@withContext UpdateManifest(
                        phone = AppVersionInfo(versionCode = derivedCode, versionName = tagName, apkUrl = phoneApkUrl, changelog = body),
                        watch = AppVersionInfo(versionCode = derivedCode + 6, versionName = tagName, apkUrl = watchApkUrl, changelog = body)
                    )
                }
            }
        } catch (apiErr: Exception) {
            Log.w(TAG, "GitHub Releases API fallback failed: ${apiErr.message}")
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

            if (connection.responseCode !in 200..299) {
                lastError = "Download server returned HTTP ${connection.responseCode}"
                Log.e(TAG, "Download failed: HTTP ${connection.responseCode} for $currentUrl")
                return@withContext null
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
        val chunkSize = 4096 // 4 KB chunks for safe, rock-solid RFCOMM buffer throughput
        val totalChunks = (bytes.size + chunkSize - 1) / chunkSize

        Log.i(TAG, "Starting Watch OTA: $versionName (build $versionCode), ${bytes.size} bytes across $totalChunks chunks (SHA-256: $sha256)")

        // 1. Send OTA_START
        val startPayload = OtaStartPayload(versionName, versionCode, bytes.size.toLong(), totalChunks, sha256)
        val started = BluetoothSyncService.sendStreamingSyncMessage(
            SyncMessage(MessageType.OTA_START, payload = gson.toJson(startPayload))
        )
        if (!started) {
            Log.w(TAG, "Bluetooth not connected; unable to start OTA")
            return@withContext false
        }
        delay(400) // Allow watch to allocate RandomAccessFile and prepare buffers

        // 2. Stream OTA chunks with gentle, robust RFCOMM pacing
        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, bytes.size)
            val chunkBytes = bytes.copyOfRange(start, end)
            val base64 = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)
            val chunkPayload = OtaChunkPayload(
                chunkIndex = i,
                totalChunks = totalChunks,
                dataBase64 = base64,
                offset = start.toLong(),
                chunkSize = chunkSize
            )

            val sent = BluetoothSyncService.sendStreamingSyncMessage(
                SyncMessage(MessageType.OTA_CHUNK, payload = gson.toJson(chunkPayload))
            )
            if (!sent) {
                Log.e(TAG, "Bluetooth transmission failed at chunk $i of $totalChunks")
                return@withContext false
            }

            val pct = ((i + 1) * 100) / totalChunks
            onProgress(pct, i + 1, totalChunks)

            // Pacing: 35ms per 4KB chunk (~100 KB/s), perfectly matching RFCOMM hardware bandwidth
            delay(35)
            // Periodic buffer drain pause every 25 chunks (~100 KB)
            if (i > 0 && i % 25 == 0) {
                delay(120)
            }
        }

        // 3. Send OTA_COMPLETE
        delay(400)
        val completePayload = OtaCompletePayload(true, sha256)
        val finished = BluetoothSyncService.sendStreamingSyncMessage(
            SyncMessage(MessageType.OTA_COMPLETE, payload = gson.toJson(completePayload))
        )
        Log.i(TAG, "Watch OTA streaming completed: $totalChunks chunks delivered (finishSignal=$finished)")
        finished
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}

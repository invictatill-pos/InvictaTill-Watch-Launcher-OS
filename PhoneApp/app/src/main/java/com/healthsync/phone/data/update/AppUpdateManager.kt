package com.healthsync.phone.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.healthsync.phone.data.model.*
import com.healthsync.phone.service.BluetoothSyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    const val DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/main/version.json"
    const val CDN_MANIFEST_URL = "https://cdn.jsdelivr.net/gh/invictatill-pos/InvictaTill-Watch-Launcher-OS@main/version.json"
    const val FALLBACK_MANIFEST_URL = CDN_MANIFEST_URL
    private const val RELEASE_MANIFEST_URL = "https://github.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/releases/latest/download/version.json"
    private val gson = Gson()
    var lastError: String? = null
        private set

    /** Release metadata contains real Android version codes; never derive them from a tag. */
    suspend fun fetchManifest(manifestUrl: String = DEFAULT_MANIFEST_URL): UpdateManifest? = withContext(Dispatchers.IO) {
        lastError = null
        val timestamp = System.currentTimeMillis()
        val candidates = listOf(manifestUrl, DEFAULT_MANIFEST_URL, CDN_MANIFEST_URL, RELEASE_MANIFEST_URL).distinct()
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            var connection: HttpURLConnection? = null
            try {
                val separator = if ('?' in candidate) "&" else "?"
                connection = openConnection("$candidate${separator}t=$timestamp", 8_000)
                val body = connection.inputStream.use { input ->
                    val bytes = input.readBytesBounded(UpdatePolicy.MAX_MANIFEST_BYTES)
                    bytes.toString(Charsets.UTF_8)
                }
                val parsed = gson.fromJson(body, UpdateManifest::class.java)
                if (!UpdatePolicy.validManifest(parsed)) throw java.io.IOException("Update metadata is incomplete or unverified")
                lastError = null
                return@withContext parsed
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                lastError = error.message ?: "Unable to read update metadata"
                Log.w(TAG, "Update check failed: ${error.message}")
            } finally { connection?.disconnect() }
        }
        null
    }

    private suspend fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw java.io.IOException("Update metadata exceeds size limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun openConnection(address: String, timeout: Int = 15_000): HttpURLConnection {
        var current = URL(address)
        repeat(6) {
            require(UpdatePolicy.secureUrl(current.toString())) { "Update requires a secure HTTPS address" }
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeout
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "HealthSync-Phone")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val status = try { connection.responseCode } catch (error: Exception) {
                connection.disconnect(); throw error
            }
            if (status in listOf(301, 302, 303, 307, 308)) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                if (next.isNullOrBlank()) throw java.io.IOException("Update redirect is missing its destination")
                current = URL(current, next)
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

    /** Only a complete file matching the selected manifest and Android package is returned. */
    suspend fun downloadApk(
        context: Context,
        info: AppVersionInfo,
        isWatch: Boolean,
        onProgress: (percent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        lastError = null
        var connection: HttpURLConnection? = null
        val directory = File(context.cacheDir, "ota_updates").apply { mkdirs() }
        val target = File(directory, if (isWatch) "watch-update.apk" else "phone-update.apk")
        val partial = File(directory, target.name + ".part")
        try {
            require(UpdatePolicy.validInfo(info)) { "Update metadata is incomplete or unverified" }
            val candidateUrls = listOfNotNull(
                info.apkUrl,
                if (info.apkUrl.contains("/releases/download/"))
                    "https://raw.githubusercontent.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/main/dist/${if (isWatch) "HealthSync-Watch.apk" else "HealthSync-Phone.apk"}"
                else null,
                if (info.apkUrl.contains("/releases/download/"))
                    "https://raw.githubusercontent.com/invictatill-pos/InvictaTill-Watch-Launcher-OS/main/dist/${if (isWatch) "HealthSync-Watch-${info.versionName}-Android8.apk" else "HealthSync-Phone-${info.versionName}.apk"}"
                else null,
                if (info.apkUrl.contains("/releases/download/"))
                    "https://cdn.jsdelivr.net/gh/invictatill-pos/InvictaTill-Watch-Launcher-OS@main/dist/${if (isWatch) "HealthSync-Watch.apk" else "HealthSync-Phone.apk"}"
                else null
            ).distinct()

            var downloadedSuccessfully = false
            for (url in candidateUrls) {
                currentCoroutineContext().ensureActive()
                connection = null
                try {
                    connection = openConnection(url)
                    val totalLength = connection.contentLength.toLong()
                    require(totalLength <= UpdatePolicy.MAX_APK_BYTES) { "Update package exceeds size limit" }
                    var downloaded = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                downloaded += count
                                require(downloaded <= UpdatePolicy.MAX_APK_BYTES) { "Update package exceeds size limit" }
                                output.write(buffer, 0, count)
                                onProgress(if (totalLength > 0) ((downloaded * 100 / totalLength).toInt()).coerceIn(0, 99) else -1, downloaded, totalLength)
                            }
                            output.fd.sync()
                        }
                    }
                    require(downloaded > 0 && (totalLength <= 0 || downloaded == totalLength)) { "Update download was incomplete" }
                    require(calculateSha256(partial).equals(info.sha256, true)) { "Update checksum did not match; retry the download" }
                    verifyPackage(context, partial, info, if (isWatch) "com.healthsync.watch" else context.packageName)
                    target.delete()
                    require(partial.renameTo(target)) { "Could not save verified update" }
                    onProgress(100, downloaded, downloaded)
                    downloadedSuccessfully = true
                    break
                } catch (cancelled: CancellationException) {
                    partial.delete()
                    throw cancelled
                } catch (candidateError: Exception) {
                    partial.delete()
                    Log.w(TAG, "Download attempt failed for $url: ${candidateError.message}")
                    lastError = candidateError.message ?: "Unable to download verified update"
                } finally {
                    connection?.disconnect()
                }
            }
            if (downloadedSuccessfully) {
                lastError = null
                target
            } else {
                null
            }
        } catch (cancelled: CancellationException) {
            partial.delete()
            throw cancelled
        } catch (error: Exception) {
            partial.delete()
            lastError = error.message ?: "Unable to download verified update"
            Log.w(TAG, "Update download failed: ${error.message}")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(context: Context, file: File, info: AppVersionInfo, expectedPackage: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IllegalArgumentException("Downloaded file is not an Android app")
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == expectedPackage && code == info.versionCode.toLong() && archive.versionName == info.versionName) {
            "Update package does not match its advertised app/version"
        }
        if (expectedPackage == context.packageName) {
            val installed = context.packageManager.getPackageInfo(context.packageName, flags)
            val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
            require(code >= installedCode) { "An older phone version cannot replace the installed app" }
            val actualSigners = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
            val installedSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
            require(!actualSigners.isNullOrEmpty() && !installedSigners.isNullOrEmpty() && actualSigners.toSet() == installedSigners.toSet()) {
                "Update signing certificate differs from the installed app"
            }
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean = try {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            lastError = "Allow HealthSync to install updates, return here, then tap Install."
            false
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
    } catch (error: Exception) {
        lastError = error.message ?: "Unable to open Android installer"
        false
    }

    /** Sends bytes, then awaits correlated verification on updated watches. Android still confirms installation. */
    suspend fun streamApkToWatchViaBluetooth(
        apkFile: File,
        versionName: String,
        versionCode: Int,
        onProgress: (percent: Int, currentChunk: Int, totalChunks: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        lastError = null
        val transferId = UUID.randomUUID().toString()
        var started = false
        var completed = false
        var checksum = ""
        try {
            require(apkFile.length() in 1..UpdatePolicy.MAX_APK_BYTES) { "Verified watch update file is missing" }
            val size = apkFile.length()
            val sha256 = calculateSha256(apkFile)
            checksum = sha256
            val chunkSize = 4096
            val totalChunks = ((size + chunkSize - 1) / chunkSize).toInt()
            suspend fun send(type: MessageType, payload: Any) {
                currentCoroutineContext().ensureActive()
                val sent = BluetoothSyncService.sendStreamingSyncMessage(SyncMessage(type, payload = gson.toJson(payload)))
                currentCoroutineContext().ensureActive()
                check(sent) {
                    "Bluetooth connection interrupted; reconnect and retry"
                }
            }
            send(MessageType.OTA_START, OtaStartPayload(versionName, versionCode, size, totalChunks, sha256, transferId))
            started = true
            delay(400)
            apkFile.inputStream().use { input ->
                for (index in 0 until totalChunks) {
                    currentCoroutineContext().ensureActive()
                    val bytes = ByteArray(minOf(chunkSize.toLong(), size - index.toLong() * chunkSize).toInt())
                    var filled = 0
                    while (filled < bytes.size) {
                        val read = input.read(bytes, filled, bytes.size - filled)
                        check(read > 0) { "Watch update file changed during transfer" }
                        filled += read
                    }
                    val result = BluetoothSyncService.otaProgress.value
                    check(result?.transferId != transferId || result.status != "ERROR") { result?.message ?: "Watch rejected update" }
                    send(MessageType.OTA_CHUNK, OtaChunkPayload(index, totalChunks, Base64.encodeToString(bytes, Base64.NO_WRAP), index.toLong() * chunkSize, chunkSize, transferId))
                    onProgress(((index + 1) * 100) / totalChunks, index + 1, totalChunks)
                    delay(35)
                    if (index > 0 && index % 25 == 0) delay(120)
                }
            }
            send(MessageType.OTA_COMPLETE, OtaCompletePayload(true, sha256, transferId))
            val supportsResult = (BluetoothSyncService.connectedWatchVersionCode.value ?: 0) >= 26
            if (supportsResult) {
                val result = withTimeoutOrNull(30_000) {
                    BluetoothSyncService.otaProgress.first { it?.transferId == transferId && it.status in setOf("READY", "ERROR") }
                }
                check(result?.status == "READY") { result?.message ?: "Watch verification was not confirmed. Check the watch before retrying." }
            }
            completed = true
            true
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            lastError = error.message ?: "Watch update transfer failed"
            false
        } finally {
            if (started && !completed) withContext(NonCancellable) {
                withTimeoutOrNull(2_000L) {
                    BluetoothSyncService.sendStreamingSyncMessage(SyncMessage(MessageType.OTA_COMPLETE,
                        payload = gson.toJson(OtaCompletePayload(false, checksum, transferId))))
                }
            }
        }
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

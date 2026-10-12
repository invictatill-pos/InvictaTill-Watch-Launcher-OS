package com.healthsync.phone.data.update

import java.net.URI

internal object UpdatePolicy {
    const val MAX_APK_BYTES = 100L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 256 * 1024

    fun secureUrl(value: String?): Boolean = try {
        val uri = URI(value.orEmpty())
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

    fun validInfo(info: AppVersionInfo?): Boolean = info != null && info.versionCode > 0 &&
        info.versionName.orEmpty().length <= 80 && info.apkUrl.orEmpty().length <= 4096 &&
        info.changelog != null && info.changelog.length <= 16_384 &&
        Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?").matches(info.versionName.orEmpty()) &&
        secureUrl(info.apkUrl) && Regex("[a-fA-F0-9]{64}").matches(info.sha256.orEmpty())

    fun validManifest(manifest: UpdateManifest?): Boolean = manifest != null &&
        validInfo(manifest.phone) && validInfo(manifest.watch)
}

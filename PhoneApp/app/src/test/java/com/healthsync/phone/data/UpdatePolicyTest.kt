package com.healthsync.phone.data

import com.healthsync.phone.data.update.*
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val info = AppVersionInfo(20, "2.7.0", "https://example.com/phone.apk", sha256 = "a".repeat(64))

    @Test fun requiresActualVersionAndIntegrityMetadata() {
        assertTrue(UpdatePolicy.validInfo(info))
        assertFalse(UpdatePolicy.validInfo(info.copy(versionCode = 0)))
        assertFalse(UpdatePolicy.validInfo(info.copy(versionName = "")))
        assertFalse(UpdatePolicy.validInfo(info.copy(sha256 = "")))
        assertFalse(UpdatePolicy.validInfo(info.copy(sha256 = "x".repeat(64))))
        assertFalse(UpdatePolicy.validInfo(info.copy(versionName = "2.7.0+" + "a".repeat(100))))
        assertFalse(UpdatePolicy.validInfo(info.copy(changelog = "a".repeat(16_385))))
        assertFalse(UpdatePolicy.validManifest(UpdateManifest(info, AppVersionInfo())))
    }

    @Test fun rejectsInsecureOrMalformedUrls() {
        assertFalse(UpdatePolicy.secureUrl("http://example.com/update.apk"))
        assertFalse(UpdatePolicy.secureUrl("https://user:password@example.com/update.apk"))
        assertFalse(UpdatePolicy.secureUrl("file:///tmp/update.apk"))
        assertFalse(UpdatePolicy.secureUrl(null))
        assertTrue(UpdatePolicy.secureUrl("https://github.com/org/repo/releases/download/v2.7.0/watch.apk"))
    }
}

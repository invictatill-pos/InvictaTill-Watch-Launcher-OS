package com.healthsync.phone.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class NotificationAppCatalogTest {
    @Test
    fun preinstalledGmailRemainsSearchableBeforePostingAnyNotification() {
        val gmail = AppFilterInfo("com.google.android.gm", "Gmail", false, isSystemApp = true)
        val apps = buildNotificationAppCatalog(
            installedApps = listOf(gmail), knownApps = emptyList(),
            selectedPackages = emptySet(), observedPackages = emptySet(), ownPackage = "com.healthsync.phone"
        )

        // Both Settings and Alerts search this same inventory, including system applications.
        assertEquals(listOf(gmail), filterNotificationApps(apps, NotificationAppCategory.ALL, "gmail"))
        assertEquals(listOf(gmail), filterNotificationApps(apps, NotificationAppCategory.SYSTEM, "com.google.android.gm"))
        assertFalse(apps.single().hasPostedNotification)
    }

    @Test
    fun failedPackageInventoryStillShowsRealLauncherAndNotificationSources() {
        val launcherApp = AppFilterInfo("example.mail", "Mail", false)
        val inventory = loadNotificationAppInventory(
            { throw SecurityException("Inventory restricted by firmware") },
            { listOf(launcherApp) },
            { emptyList() }
        )
        val apps = buildNotificationAppCatalog(inventory.apps,
            listOf(AppFilterInfo("vendor.active", "Active source", false)), emptySet(),
            setOf("vendor.active"), "com.healthsync.phone")

        assertEquals(1, inventory.failedQueries)
        assertEquals(setOf("example.mail", "vendor.active"), apps.map { it.packageName }.toSet())
        assertTrue(apps.first { it.packageName == "vendor.active" }.hasPostedNotification)
    }

    @Test(expected = CancellationException::class)
    fun canceledInventoryLoadDoesNotPublishPartialResultsAsACompletedRefresh() {
        loadNotificationAppInventory({ throw CancellationException("New refresh superseded this one") })
    }

    @Test
    fun launcherLabelEnrichesPackageFallbackWithoutLosingSystemStatusOrSelection() {
        val apps = buildNotificationAppCatalog(
            installedApps = listOf(AppFilterInfo("example.preinstalled", "example.preinstalled", false, isSystemApp = true)),
            knownApps = listOf(AppFilterInfo("example.preinstalled", "Mail", false)),
            selectedPackages = setOf("example.preinstalled"), observedPackages = emptySet(), ownPackage = "com.healthsync.phone"
        )

        assertEquals(1, apps.size)
        assertEquals("Mail", apps.single().label)
        assertTrue(apps.single().isSystemApp)
        assertTrue(apps.single().isEnabled)
    }

    @Test
    fun catalogIncludesSystemServicesAndObservedSourcesWithoutLauncherIcons() {
        val apps = buildNotificationAppCatalog(
            installedApps = listOf(
                AppFilterInfo("com.android.bluetooth", "Bluetooth", false, isSystemApp = true),
                AppFilterInfo("android", "Android System", false, isSystemApp = true),
                AppFilterInfo("com.healthsync.phone", "HealthSync", false)
            ),
            knownApps = listOf(AppFilterInfo("vendor.alerts", "vendor.alerts", false)),
            selectedPackages = setOf("com.android.bluetooth", "vendor.alerts"),
            observedPackages = setOf("vendor.alerts"),
            ownPackage = "com.healthsync.phone"
        )

        assertEquals(listOf("android", "com.android.bluetooth", "vendor.alerts"), apps.map { it.packageName })
        assertTrue(apps.first { it.packageName == "com.android.bluetooth" }.isEnabled)
        assertTrue(apps.last().hasPostedNotification)
        assertFalse(apps.first().isEnabled)
    }

    @Test
    fun mergingDoesNotDuplicatePackagesOrReplaceInstalledMetadata() {
        val apps = buildNotificationAppCatalog(
            installedApps = listOf(AppFilterInfo("com.android.settings", "Settings", false, isSystemApp = true)),
            knownApps = listOf(AppFilterInfo("com.android.settings", "com.android.settings", true)),
            selectedPackages = emptySet(),
            observedPackages = setOf("com.android.settings"),
            ownPackage = "com.healthsync.phone"
        )

        assertEquals(1, apps.size)
        assertEquals("Settings", apps.single().label)
        assertTrue(apps.single().isSystemApp)
        assertFalse(apps.single().isEnabled)
        assertTrue(apps.single().hasPostedNotification)
    }

    @Test
    fun categoryAndSearchCombineWithoutChangingSelections() {
        val apps = listOf(
            AppFilterInfo("com.android.bluetooth", "Bluetooth", true, isSystemApp = true),
            AppFilterInfo("com.android.settings", "Settings", false, isSystemApp = true),
            AppFilterInfo("example.messages", "Messages", true)
        )

        assertEquals(listOf(apps[0]), filterNotificationApps(apps, NotificationAppCategory.SYSTEM, " BLUETOOTH "))
        assertEquals(listOf(apps[2]), filterNotificationApps(apps, NotificationAppCategory.SELECTED, "example."))
        assertEquals(emptyList<AppFilterInfo>(), filterNotificationApps(apps, NotificationAppCategory.SELECTED, "Settings"))
        assertEquals(apps, filterNotificationApps(apps, NotificationAppCategory.ALL, ""))
    }
}

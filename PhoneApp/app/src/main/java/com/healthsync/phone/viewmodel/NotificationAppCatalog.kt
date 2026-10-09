package com.healthsync.phone.viewmodel

import java.util.Locale
import java.util.concurrent.CancellationException

data class AppFilterInfo(
    val packageName: String,
    val label: String,
    val isEnabled: Boolean,
    val isSystemApp: Boolean = false,
    val hasPostedNotification: Boolean = false
)

enum class NotificationAppCategory(val label: String) {
    ALL("All apps"), SYSTEM("System"), SELECTED("Selected")
}

internal data class NotificationAppInventory(val apps: List<AppFilterInfo>, val failedQueries: Int)

/** Package enumeration and launcher discovery can fail independently on vendor firmware. */
internal fun loadNotificationAppInventory(vararg queries: () -> List<AppFilterInfo>): NotificationAppInventory {
    val apps = mutableListOf<AppFilterInfo>()
    var failedQueries = 0
    queries.forEach { query ->
        try {
            apps.addAll(query())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failedQueries++
        }
    }
    return NotificationAppInventory(apps, failedQueries)
}

/** Keep notification-only packages selectable when package metadata is unavailable. */
internal fun buildNotificationAppCatalog(
    installedApps: List<AppFilterInfo>,
    knownApps: List<AppFilterInfo>,
    selectedPackages: Set<String>,
    observedPackages: Set<String>,
    ownPackage: String
): List<AppFilterInfo> = (installedApps + knownApps)
    .filter { it.packageName.isNotBlank() && it.packageName != ownPackage }
    .groupBy { it.packageName }
    .map { (packageName, candidates) ->
        candidates.first().copy(
            label = candidates.firstOrNull { it.label.isNotBlank() && it.label != packageName }?.label ?: packageName,
            isSystemApp = candidates.any { it.isSystemApp },
            isEnabled = packageName in selectedPackages,
            hasPostedNotification = packageName in observedPackages
        )
    }
    .sortedWith(compareBy<AppFilterInfo> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })

internal fun filterNotificationApps(
    apps: List<AppFilterInfo>,
    category: NotificationAppCategory,
    query: String
): List<AppFilterInfo> {
    val search = query.trim()
    return apps.filter { app ->
        val matchesCategory = when (category) {
            NotificationAppCategory.ALL -> true
            NotificationAppCategory.SYSTEM -> app.isSystemApp
            NotificationAppCategory.SELECTED -> app.isEnabled
        }
        matchesCategory && (search.isEmpty() || app.label.contains(search, ignoreCase = true) ||
            app.packageName.contains(search, ignoreCase = true))
    }
}

package com.healthsync.phone.viewmodel

import android.app.Application
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthsync.phone.data.FitnessRepository
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.NotificationHistoryEntity
import com.healthsync.phone.service.HealthNotificationListenerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    application: Application,
    private val repository: FitnessRepository
) : AndroidViewModel(application) {

    private val prefs = PhonePreferences(application)
    private val sharedPreferences = application.getSharedPreferences("health_sync_prefs", Context.MODE_PRIVATE)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "all_apps_enabled" || key == "allowed_apps" || key == "observed_notification_apps") loadInstalledApps()
    }
    private var loadJob: Job? = null
    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { loadInstalledApps() }
    }
    private var packageReceiverRegistered = false
    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()
    private val _appLoadError = MutableStateFlow<String?>(null)
    val appLoadError: StateFlow<String?> = _appLoadError.asStateFlow()

    // ── Notification history ────────────────────────────────────────────────
    val notifications: Flow<List<NotificationHistoryEntity>> = repository.getRecentNotifications()

    // ── App filter list ─────────────────────────────────────────────────────
    private val _appFilters = MutableStateFlow<List<AppFilterInfo>>(emptyList())
    val appFilters: StateFlow<List<AppFilterInfo>> = _appFilters.asStateFlow()

    /** Whether ALL apps are allowed (master toggle) */
    private val _allAppsEnabled = MutableStateFlow(prefs.allAppsEnabled)
    val allAppsEnabled: StateFlow<Boolean> = _allAppsEnabled.asStateFlow()

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        runCatching {
            ContextCompat.registerReceiver(application, packageChanges, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            packageReceiverRegistered = true
        }
        loadInstalledApps()
    }

    /**
     * Includes system services without launcher icons and notification sources
     * already discovered by the listener. Only our own sync app is excluded.
     */
    fun loadInstalledApps() {
        loadJob?.cancel()
        _isLoadingApps.value = true
        _appLoadError.value = null
        _allAppsEnabled.value = prefs.allAppsEnabled
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val application = getApplication<Application>()
            val pm = application.packageManager
            val selected = prefs.allowedApps
            val observed = prefs.observedNotificationApps + HealthNotificationListenerService.activeNotificationPackages()
            fun appInfo(info: ApplicationInfo) = AppFilterInfo(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(info.packageName).ifBlank { info.packageName },
                isEnabled = info.packageName in selected,
                isSystemApp = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            )

            val inventory = loadNotificationAppInventory(
                { pm.getInstalledApplications(0).map { appInfo(it) } },
                {
                    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                        .mapNotNull { it.activityInfo?.applicationInfo }.map { appInfo(it) }
                },
                {
                    // A companion is not a cross-profile launcher. Query its own Android profile.
                    application.getSystemService(LauncherApps::class.java)
                        ?.getActivityList(null, Process.myUserHandle()).orEmpty().map { activity ->
                            appInfo(activity.applicationInfo).copy(label = activity.label?.toString()
                                ?.takeIf { it.isNotBlank() } ?: activity.applicationInfo.packageName)
                        }
                }
            )
            val loadError = if (inventory.failedQueries > 0)
                "Some apps could not be listed. Other discovered sources are still shown. Refresh to retry." else null
            val installed = inventory.apps
            val installedPackages = installed.mapTo(mutableSetOf()) { it.packageName }
            val discovered = (observed + selected - installedPackages).map { packageName ->
                try {
                    appInfo(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Some OEM notification sources expose no visible application metadata.
                    AppFilterInfo(packageName, packageName, packageName in selected)
                }
            }
            val apps = buildNotificationAppCatalog(installed, discovered, selected, observed, application.packageName)

            ensureActive()
            _appLoadError.value = loadError
            _appFilters.value = apps
            _isLoadingApps.value = false
        }
    }

    /** Toggle master "allow all apps" switch */
    fun setAllAppsEnabled(enabled: Boolean) {
        prefs.allAppsEnabled = enabled
        _allAppsEnabled.value = enabled
        // Refresh list to update per-app states
        loadInstalledApps()
    }

    /** Toggle a single app on/off */
    fun toggleApp(packageName: String, enabled: Boolean) {
        val current = prefs.allowedApps.toMutableSet()
        if (enabled) current.add(packageName) else current.remove(packageName)
        prefs.allowedApps = current
        // Reflect in UI state immediately
        _appFilters.value = _appFilters.value.map { app ->
            if (app.packageName == packageName) app.copy(isEnabled = enabled) else app
        }
    }

    override fun onCleared() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (packageReceiverRegistered) getApplication<Application>().unregisterReceiver(packageChanges)
        super.onCleared()
    }
}

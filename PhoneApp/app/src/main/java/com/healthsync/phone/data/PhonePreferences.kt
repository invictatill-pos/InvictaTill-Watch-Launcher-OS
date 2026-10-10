package com.healthsync.phone.data

import android.content.Context
import android.content.SharedPreferences
import com.healthsync.phone.data.model.SensorIntervalsPayload

/** Stores phone-side settings in SharedPreferences */
class PhonePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("health_sync_prefs", Context.MODE_PRIVATE)

    // ── Sensor intervals (mirrored on watch) ──────────────────────────────────

    /** Heart rate measurement interval (ms) to send to watch; -1 = disabled */
    var hrIntervalMs: Long
        get() = prefs.getLong("hr_interval_ms", 60_000L)
        set(v) { synchronized(prefs) { updateSensorIntervalsLocally(validInterval(v), spo2IntervalMs) } }

    /** SpO2 measurement interval (ms) to send to watch; -1 = disabled */
    var spo2IntervalMs: Long
        get() = prefs.getLong("spo2_interval_ms", 300_000L)
        set(v) { synchronized(prefs) { updateSensorIntervalsLocally(hrIntervalMs, validInterval(v)) } }

    fun sensorIntervals(): SensorIntervalsPayload = synchronized(prefs) {
        SensorIntervalsPayload(hrIntervalMs, spo2IntervalMs, prefs.getLong("sensor_settings_revision", 0L).coerceAtLeast(0L))
    }

    fun updateSensorIntervalsLocally(hr: Long, oxygen: Long, forceRevision: Boolean = false): SensorIntervalsPayload = synchronized(prefs) {
        val current = sensorIntervals()
        val updated = SensorIntervalPolicy.localEdit(current, hr, oxygen, System.currentTimeMillis(), forceRevision)
        if (current == updated) return current
        writeSensorIntervals(updated)
        updated
    }

    /** Revision 0 watch snapshots establish the pre-sync watch baseline only before either device edits. */
    fun applySensorIntervals(incoming: SensorIntervalsPayload): Boolean = synchronized(prefs) {
        val current = sensorIntervals()
        if (!SensorIntervalPolicy.shouldApply(current, incoming, acceptLegacyWatchBaseline = true) || current == incoming) return false
        writeSensorIntervals(incoming)
        true
    }

    fun observeSensorIntervals(onChanged: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf("hr_interval_ms", "spo2_interval_ms", "sensor_settings_revision")) onChanged()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun writeSensorIntervals(value: SensorIntervalsPayload) {
        prefs.edit().putLong("hr_interval_ms", value.hrIntervalMs).putLong("spo2_interval_ms", value.spo2IntervalMs)
            .putLong("sensor_settings_revision", value.revision).apply()
    }

    // ── Notification app filter ───────────────────────────────────────────────

    /** If true, all apps are allowed to sync (no filter). If false, use allowedApps */
    var allAppsEnabled: Boolean
        get() = prefs.getBoolean("all_apps_enabled", true)
        set(v) { prefs.edit().putBoolean("all_apps_enabled", v).apply() }

    /**
     * Set of package names allowed to send notifications to watch.
     * Only used when allAppsEnabled = false.
     */
    var allowedApps: Set<String>
        get() = prefs.getStringSet("allowed_apps", emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet("allowed_apps", v).apply() }

    /** Source packages discovered by notification access, including sources not mirrored. */
    val observedNotificationApps: Set<String>
        get() = prefs.getStringSet("observed_notification_apps", emptySet())?.toSet() ?: emptySet()

    fun recordNotificationSources(packageNames: Set<String>) {
        val existing = observedNotificationApps
        val sources = existing + packageNames.filter { it.isNotBlank() }
        if (sources != existing) {
            prefs.edit().putStringSet("observed_notification_apps", sources).apply()
        }
    }

    fun isAppAllowed(packageName: String): Boolean {
        return syncNotifications && (allAppsEnabled || packageName in allowedApps)
    }

    var stepGoal: Int
        get() = prefs.getInt("step_goal", 10000).coerceIn(1, 100_000)
        set(v) { prefs.edit().putInt("step_goal", v.coerceIn(1, 100_000)).apply() }

    var syncNotifications: Boolean
        get() = prefs.getBoolean("sync_notifications", true)
        set(v) { prefs.edit().putBoolean("sync_notifications", v).apply() }

    var syncCalls: Boolean
        get() = prefs.getBoolean("sync_calls", true)
        set(v) { prefs.edit().putBoolean("sync_calls", v).apply() }

    var connectionPaused: Boolean
        get() = prefs.getBoolean("connection_paused", false)
        set(v) { prefs.edit().putBoolean("connection_paused", v).apply() }

    var userWeightKg: Double
        get() = prefs.getFloat("user_weight_kg", 70f).toDouble().coerceIn(30.0, 250.0)
        set(v) { if (v.isFinite()) prefs.edit().putFloat("user_weight_kg", v.coerceIn(30.0, 250.0).toFloat()).apply() }

    var userHeightCm: Int
        get() = prefs.getInt("user_height_cm", 170).coerceIn(100, 230)
        set(v) { prefs.edit().putInt("user_height_cm", v.coerceIn(100, 230)).apply() }

    var lastKnownWatchVersion: String
        get() = prefs.getString("last_known_watch_version", "2.4.3") ?: "2.4.3"
        set(v) { prefs.edit().putString("last_known_watch_version", v).apply() }

    var lastKnownWatchVersionCode: Int
        get() = prefs.getInt("last_known_watch_version_code", 19)
        set(v) { prefs.edit().putInt("last_known_watch_version_code", v).apply() }

    private fun validInterval(value: Long) = if (value == -1L) value else value.coerceIn(30_000L, 3_600_000L)
}

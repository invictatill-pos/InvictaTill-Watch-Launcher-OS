package com.healthsync.watch.data

import android.content.Context

/** Stores watch sensor configuration in SharedPreferences */
class WatchPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("watch_sync_prefs", Context.MODE_PRIVATE)

    companion object {
        val FACE_STYLES get() = com.healthsync.watch.ui.WatchFaceCatalog.styles
        // Interval constants (ms)
        const val INTERVAL_30_SEC  =     30_000L
        const val INTERVAL_1_MIN   =     60_000L
        const val INTERVAL_5_MIN   =    300_000L
        const val INTERVAL_10_MIN  =    600_000L
        const val INTERVAL_30_MIN  =  1_800_000L
        const val INTERVAL_1_HOUR  =  3_600_000L
        const val INTERVAL_OFF     =         -1L
    }

    /** Heart rate measurement interval in ms; -1 = disabled */
    var hrIntervalMs: Long
        get() = prefs.getLong("hr_interval_ms", INTERVAL_1_MIN)
        set(v) { synchronized(prefs) { updateSensorIntervalsLocally(validInterval(v), spo2IntervalMs) } }

    /** SpO2 measurement interval in ms; -1 = disabled */
    var spo2IntervalMs: Long
        get() = prefs.getLong("spo2_interval_ms", INTERVAL_5_MIN)
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

    /** Apply both fields and their revision together without creating a new local edit. */
    fun applySensorIntervals(incoming: SensorIntervalsPayload): Boolean = synchronized(prefs) {
        val current = sensorIntervals()
        if (incoming.revision == 0L || !SensorIntervalPolicy.shouldApply(current, incoming) || current == incoming) return false
        writeSensorIntervals(incoming)
        true
    }

    private fun writeSensorIntervals(value: SensorIntervalsPayload) {
        prefs.edit().putLong("hr_interval_ms", value.hrIntervalMs).putLong("spo2_interval_ms", value.spo2IntervalMs)
            .putLong("sensor_settings_revision", value.revision).apply()
    }

    var stepGoal: Int
        get() = prefs.getInt("step_goal", 10_000).coerceIn(1, 100_000)
        set(v) { prefs.edit().putInt("step_goal", v.coerceIn(1, 100_000)).apply() }

    var lastPhoneAddress: String?
        get() = prefs.getString("last_phone_address", null)
        set(v) { prefs.edit().putString("last_phone_address", v).apply() }

    var userWeightKg: Double
        get() = prefs.getFloat("user_weight_kg", 70f).toDouble().coerceIn(30.0, 250.0)
        set(v) { if (v.isFinite()) prefs.edit().putFloat("user_weight_kg", v.coerceIn(30.0, 250.0).toFloat()).apply() }

    var userHeightCm: Int
        get() = prefs.getInt("user_height_cm", 170).coerceIn(100, 230)
        set(v) { prefs.edit().putInt("user_height_cm", v.coerceIn(100, 230)).apply() }

    /** Foreground Always-On Display is opt-in because it keeps the watch screen awake. */
    var alwaysOnDisplayEnabled: Boolean
        get() = prefs.getBoolean("aod_enabled", false)
        set(v) { prefs.edit().putBoolean("aod_enabled", v).apply() }

    var aodStyle: String
        get() = prefs.getString("aod_style", "face")?.takeIf {
            it in com.healthsync.watch.ui.WatchFaceCatalog.ambientStyles
        } ?: "face"
        set(v) { if (v in com.healthsync.watch.ui.WatchFaceCatalog.ambientStyles) prefs.edit().putString("aod_style", v).apply() }

    /** Clean face variants always hide shortcuts; this controls the other faces. */
    var clockShortcutsEnabled: Boolean
        get() = prefs.getBoolean("clock_shortcuts", true)
        set(v) { prefs.edit().putBoolean("clock_shortcuts", v).apply() }

    var aodIdleDelayMs: Long
        get() = prefs.getLong("aod_idle_delay", 15_000L).takeIf { it in listOf(15_000L, 30_000L, 60_000L) } ?: 15_000L
        set(v) { if (v in listOf(15_000L, 30_000L, 60_000L)) prefs.edit().putLong("aod_idle_delay", v).apply() }

    var aodBrightness: Float
        get() = prefs.getFloat("aod_brightness", .03f).takeIf { it.isFinite() }?.coerceIn(.01f, .1f) ?: .03f
        set(v) { if (v.isFinite()) prefs.edit().putFloat("aod_brightness", v.coerceIn(.01f, .1f)).apply() }

    /** Activated only after separate Android Notification Access consent. */
    var watchNotificationTakeoverEnabled: Boolean
        get() = prefs.getBoolean("watch_notification_takeover", true)
        set(v) { prefs.edit().putBoolean("watch_notification_takeover", v).apply() }

    /** Popups respect Android's launch restrictions, lock screen and Do Not Disturb. */
    var watchNotificationPopupEnabled: Boolean
        get() = prefs.getBoolean("watch_notification_popup", true)
        set(v) { prefs.edit().putBoolean("watch_notification_popup", v).apply() }

    /** Briefly illuminate compatible watches for new alerts; never for a replay. */
    var watchNotificationWakeEnabled: Boolean
        get() = prefs.getBoolean("watch_notification_wake", true)
        set(v) { prefs.edit().putBoolean("watch_notification_wake", v).apply() }

    /** Draw-over-apps permission is checked separately on every service start. */
    var bezelEnabled: Boolean
        get() = prefs.getBoolean("bezel_enabled", false)
        set(v) { prefs.edit().putBoolean("bezel_enabled", v).apply() }

    var bezelDiameterPercent: Int
        get() = prefs.getInt("bezel_diameter", 100).coerceIn(85, 100)
        set(v) { prefs.edit().putInt("bezel_diameter", v.coerceIn(85, 100)).apply() }

    /** Optional foreground gesture; never wakes a screen that firmware has powered off. */
    var wristWakeEnabled: Boolean
        get() = prefs.getBoolean("wrist_wake", false)
        set(v) { prefs.edit().putBoolean("wrist_wake", v).apply() }

    /** Show Android's selected wallpaper behind the interactive Circular or Modern face. */
    var wallpaperBackdropEnabled: Boolean
        get() = prefs.getBoolean("wallpaper_backdrop", false)
        set(v) { prefs.edit().putBoolean("wallpaper_backdrop", v).apply() }

    /** Whether the Classic Casio watchface is active in interactive mode */
    var useCasioFace: Boolean
        get() = prefs.getBoolean("use_casio_face", true)
        set(v) { prefs.edit().putBoolean("use_casio_face", v).apply() }

    /** Faces rendered in the unified watch shell. */
    var watchFaceStyle: String
        get() = prefs.getString("watch_face_style", "orbit")
            ?.takeIf { it in FACE_STYLES } ?: "orbit"
        set(v) { if (v in FACE_STYLES) prefs.edit().putString("watch_face_style", v).apply() }

    fun upgradeToWatchShell() {
        if (!prefs.getBoolean("watch_shell_v2", false)) {
            if (watchFaceStyle == "circular") watchFaceStyle = "orbit"
            prefs.edit().putBoolean("watch_shell_v2", true).apply()
        }
    }

    private fun validInterval(value: Long) = if (value == -1L) value else value.coerceIn(30_000L, 3_600_000L)
}

package com.healthsync.watch.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.os.SystemClock

/**
 * Wraps the Android TYPE_HEART_RATE sensor.
 * Gracefully handles devices that don't have a heart rate sensor.
 */
class HeartRateSensor(
    private val sensorManager: SensorManager,
    private val onReading: (bpm: Int, accuracy: Int) -> Unit,
    private val onTimedReading: ((bpm: Int, accuracy: Int, capturedAt: Long) -> Unit)? = null
) : SensorEventListener {

    companion object {
        private const val TAG = "HRSensor"
        fun candidates(manager: SensorManager): List<Sensor> {
            // BODY_SENSORS may be granted after the service/wrapper was created. Ask the
            // manager again for each acquisition and keep the firmware's default first.
            val preferred = manager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            val exposed = manager.getSensorList(Sensor.TYPE_ALL)
                .filter { HeartRatePolicy.isHeartRateSensor(it.type, it.name, it.stringType) }
                .sortedBy { if (it.type == Sensor.TYPE_HEART_RATE) 0 else 1 }
            return (listOfNotNull(preferred) + exposed).distinct()
        }
    }
    private var sensors = emptyList<Sensor>()
    val isAvailable: Boolean get() = runCatching { candidates(sensorManager).isNotEmpty() }.getOrDefault(false)
    private var acquisitionStartedNanos = 0L
    private var lastAcceptedNanos = 0L

    fun start(): Boolean {
        stop()
        sensors = runCatching { candidates(sensorManager) }
            .onFailure { Log.w(TAG, "Heart rate discovery unavailable", it) }.getOrDefault(emptyList())
        acquisitionStartedNanos = SystemClock.elapsedRealtimeNanos()
        if (sensors.isEmpty()) {
            Log.w(TAG, "No heart rate sensor on this device")
            return false
        }
        val selected = sensors.firstOrNull { sensor ->
            try { sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
            catch (e: Exception) { Log.w(TAG, "Heart rate permission unavailable", e); false }
        }
        sensors = listOfNotNull(selected)
        if (selected == null) acquisitionStartedNanos = 0L
        return selected != null
    }

    fun stop() {
        acquisitionStartedNanos = 0L
        sensorManager.unregisterListener(this)
        sensors = emptyList()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor in sensors && event.values.isNotEmpty()) {
            val bpm = event.values[0].toInt()
            if (HeartRatePolicy.validReading(event.values[0], event.accuracy)) {
                val capturedAt = HeartRatePolicy.capturedWallTime(event.timestamp, SystemClock.elapsedRealtimeNanos(),
                    System.currentTimeMillis(), acquisitionStartedNanos, lastAcceptedNanos) ?: return
                lastAcceptedNanos = event.timestamp
                Log.d(TAG, "HR: $bpm bpm (accuracy=${event.accuracy})")
                onReading(bpm, event.accuracy)
                onTimedReading?.invoke(bpm, event.accuracy, capturedAt)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "HR sensor accuracy changed: $accuracy")
    }
}

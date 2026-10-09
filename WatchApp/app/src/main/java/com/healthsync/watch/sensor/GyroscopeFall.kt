package com.healthsync.watch.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.healthsync.watch.algorithm.FallDetection

/** Accelerometer free-fall and impact screening with an alert cooldown. */
class GyroscopeFall(
    private val sensorManager: SensorManager,
    private val onFallDetected: (severity: String) -> Unit
) : SensorEventListener {
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = FallDetection(onFallDetected)
    val isAvailable: Boolean get() = sensor != null

    fun start(): Boolean {
        if (sensor == null) return false
        detector.reset()
        return try { sensorManager.registerListener(this, sensor, 20_000) }
        catch (e: Exception) { Log.w("FallDetector", "Sensor unavailable", e); false }
    }
    fun stop() { sensorManager.unregisterListener(this); detector.reset() }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
        detector.process(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000L)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

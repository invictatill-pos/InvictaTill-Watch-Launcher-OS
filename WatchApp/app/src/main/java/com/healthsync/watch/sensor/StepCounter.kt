package com.healthsync.watch.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.healthsync.watch.algorithm.SignalProcessing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hardware counter with buffered motion estimates when hardware is unavailable. */
class StepCounter(
    private val sensorManager: SensorManager,
    private val onStepsUpdate: (steps: Int, calories: Double) -> Unit
) : SensorEventListener {
    private val hardware = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val isAvailable: Boolean get() = hardware != null || accelerometer != null
    var isHardware: Boolean = false
        private set
    private var lastRaw = -1
    private var steps = 0
    private var day = ""
    private val software = SignalProcessing.AntiFalsePositiveStepTracker { delta ->
        resetDay()
        steps += delta
        emit()
    }

    fun start(): Boolean {
        resetDay()
        isHardware = register(hardware, SensorManager.SENSOR_DELAY_NORMAL)
        return isHardware || register(accelerometer, 20_000)
    }
    private fun register(sensor: Sensor?, delay: Int): Boolean {
        if (sensor == null) return false
        return try { sensorManager.registerListener(this, sensor, delay) }
        catch (e: Exception) { Log.w("StepCounter", "Sensor unavailable", e); false }
    }
    fun stop() { sensorManager.unregisterListener(this); software.reset() }

    override fun onSensorChanged(event: SensorEvent) {
        resetDay()
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
            val raw = event.values[0]
            if (!raw.isFinite() || raw < 0f) return
            val value = raw.toInt()
            val delta = if (lastRaw >= 0 && value >= lastRaw) value - lastRaw else 0
            lastRaw = value
            if (delta > 0) { steps += delta; emit() }
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && !isHardware && event.values.size >= 3) {
            software.processAccel(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000L)
        }
    }
    private fun resetDay() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        if (day != today) { day = today; steps = 0; lastRaw = -1; software.reset(); emit() }
    }
    private fun emit() = onStepsUpdate(steps, steps * 0.04)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

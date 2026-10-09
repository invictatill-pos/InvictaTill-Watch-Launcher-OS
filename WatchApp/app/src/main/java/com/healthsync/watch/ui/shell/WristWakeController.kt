package com.healthsync.watch.ui.shell

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle

/**
 * Brightens a foreground dim clock after a deliberate wrist turn. The owner starts this only
 * for its visible dim face and stops it for interactive mode, panels, screen-off and onPause.
 * Uses non-wakeup sensors and never holds a wake lock or changes screen/window flags.
 */
class WristWakeController(private val activity: AppCompatActivity, private val onWake: () -> Unit) {
    private val manager = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val power = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val sensor: Sensor? = runCatching {
        manager?.getDefaultSensor(Sensor.TYPE_GRAVITY, false)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false)
    }.getOrNull()
    /** Whether Android exposes a usable non-wakeup gravity or accelerometer sensor. */
    val available: Boolean get() = sensor != null && manager != null
    private val detector = WristRaiseDetector(sensor?.type == Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            if (!running || event.values.size < 3 || event.sensor.type != sensor?.type) return
            if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus() ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || power?.isInteractive != true) return
            if (detector.onSample(event.timestamp, event.values[0], event.values[1], event.values[2])) {
                // Unregister first; queued samples cannot trigger callbacks after the clock wakes.
                stop()
                onWake()
            }
        }
    }

    fun start() {
        if (running || !available) return
        detector.reset()
        running = runCatching {
            // NORMAL asks for ~200 ms samples; no high-frequency sensor permission is needed.
            manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler) == true
        }.getOrDefault(false)
        if (!running) {
            runCatching { manager?.unregisterListener(listener) }
            detector.reset()
        }
    }

    fun stop() {
        val wasRunning = running
        running = false
        if (wasRunning) runCatching { manager?.unregisterListener(listener) }
        detector.reset()
    }
}

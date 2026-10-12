package com.healthsync.watch.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Magnetic compass and fresh device location altitude; both stay empty until captured. */
class CompassActivity : LauncherPanelActivity(), SensorEventListener, LocationListener {
    private lateinit var sensors: SensorManager
    private lateinit var dial: CompassDialView
    private lateinit var heading: TextView
    private lateinit var compassStatus: TextView
    private lateinit var elevation: TextView
    private lateinit var locationStatus: TextView
    private var rotation: Sensor? = null
    private var magnetic: Sensor? = null
    private var accelerometer: Sensor? = null
    private val acceleration = FloatArray(3)
    private val magnetism = FloatArray(3)
    private val matrix = FloatArray(9)
    private val screenMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var hasAcceleration = false
    private var hasMagnetism = false
    private var azimuth: Float? = null
    private var latestHeadingTime = 0L
    private var lastAltitude: Location? = null
    private var north = false
    private var resumed = false
    private val handler = Handler(Looper.getMainLooper())
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (resumed) startLocation()
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (latestHeadingTime > 0 && System.currentTimeMillis() - latestHeadingTime > 10_000L) {
                heading.text = "—°"
                dial.setHeading(null)
                azimuth = null
                compassStatus.text = "Waiting for a new compass sample"
            }
            refreshAltitude()
            handler.postDelayed(this, 2_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        magnetic = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val content = roundContent()
        navigationHeader(content, "Compass")
        val headingCard = card(content)
        label(headingCard, "MAGNETIC HEADING", 10f, accent, bold = true)
        dial = CompassDialView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val diameter = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val size = minOf(dp(170), (diameter * .66f).toInt())
        headingCard.addView(dial, LinearLayout.LayoutParams(-1, size))
        heading = label(headingCard, "—°", 30f, Color.WHITE, bold = true)
        compassStatus = label(headingCard, "Waiting for a compass sample", 11f)
        val altitudeCard = card(content)
        label(altitudeCard, "LOCATION ALTITUDE", 10f, accent, bold = true)
        elevation = label(altitudeCard, "Elevation — m", 20f, Color.WHITE, bold = true)
        locationStatus = label(altitudeCard, "", 11f)
        primaryAction(content, "Refresh elevation") {
            if (!fineLocationAllowed()) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION)) else startLocation()
        }
        action(content, "Location settings") { launchFirst(listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)), "Location settings could not open.") }
        action(content, "App permissions") { openAppPermissions() }
        val help = card(content)
        label(help, "Better signal", 13f, Color.WHITE, bold = true)
        label(help, "Keep away from metal and magnets. Move the watch in a figure eight if calibration is needed.", 11f)
        label(help, "Go outdoors for a recent location fix with altitude.", 11f)
        action(content, "Back to clock") { backToClock() }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        hasAcceleration = false; hasMagnetism = false; azimuth = null; latestHeadingTime = 0
        heading.text = "—°"; dial.setHeading(null)
        val registered = if (rotation != null) sensors.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        else if (accelerometer != null && magnetic != null) {
            val acc = sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            val mag = sensors.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_UI)
            acc && mag
        } else false
        compassStatus.text = if (registered) "Waiting for a compass sample" else "Compatible compass sensors are unavailable"
        startLocation()
        handler.removeCallbacks(tick); handler.post(tick)
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        sensors.unregisterListener(this)
        runCatching { locationManager()?.removeUpdates(this) }
        super.onPause()
    }

    private fun locationManager() = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private fun fineLocationAllowed() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            lastAltitude = null; refreshAltitude()
            locationStatus.text = "Allow precise location to capture elevation"
            return
        }
        val manager = locationManager() ?: run { locationStatus.text = "Location is unavailable on this watch"; return }
        try {
            manager.removeUpdates(this)
            val provider = when {
                manager.allProviders.contains(LocationManager.GPS_PROVIDER) && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.allProviders.contains(LocationManager.NETWORK_PROVIDER) && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                lastAltitude = null; refreshAltitude()
                locationStatus.text = "Location is off or no provider is available"
                return
            }
            manager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
            manager.requestLocationUpdates(provider, 5_000L, 0f, this)
            if (lastAltitude == null) locationStatus.text = "Waiting for a location fix with altitude"
        } catch (_: SecurityException) {
            // Access may be revoked between the foreground check and the provider call.
            lastAltitude = null; refreshAltitude()
            locationStatus.text = "Location access was revoked. Allow precise location in App permissions."
        } catch (_: RuntimeException) {
            lastAltitude = null; refreshAltitude()
            locationStatus.text = "Location could not start. Check app permissions and location settings."
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!resumed || event == null || event.values.size < 3 || event.values.any { !it.isFinite() }) return
        val ready = when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> { SensorManager.getRotationMatrixFromVector(matrix, event.values); true }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, acceleration, 0, 3); hasAcceleration = true
                hasMagnetism && SensorManager.getRotationMatrix(matrix, null, acceleration, magnetism)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetism, 0, 3); hasMagnetism = true
                hasAcceleration && SensorManager.getRotationMatrix(matrix, null, acceleration, magnetism)
            }
            else -> false
        }
        if (!ready) return
        @Suppress("DEPRECATION")
        val axes = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (!SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, screenMatrix)) return
        SensorManager.getOrientation(screenMatrix, orientation)
        val measured = Math.toDegrees(orientation[0].toDouble()).toFloat()
        azimuth = CompassDisplayPolicy.smooth(azimuth, measured)
        val value = azimuth ?: return
        latestHeadingTime = System.currentTimeMillis()
        val degrees = value.roundToInt() % 360
        heading.text = "$degrees° ${CompassDisplayPolicy.cardinal(value)}"
        compassStatus.text = if (event.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            "Magnetic north · calibration needed" else "Magnetic north · live sensor"
        dial.setHeading(value)
        val pointingNorth = degrees <= 2 || degrees >= 358
        if (pointingNorth && !north) heading.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        north = pointingNorth
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (::compassStatus.isInitialized && accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            compassStatus.text = "Calibration needed · keep away from magnets"
    }

    override fun onLocationChanged(location: Location) {
        @Suppress("DEPRECATION")
        val recent = CompassDisplayPolicy.locationIsRecent(location.time, System.currentTimeMillis(), location.isFromMockProvider)
        if (!recent || !location.hasAltitude() || !location.altitude.isFinite()) {
            if (lastAltitude == null) locationStatus.text = "No recent location altitude captured"
            return
        }
        lastAltitude = Location(location)
        refreshAltitude()
    }

    private fun refreshAltitude() {
        if (!::elevation.isInitialized) return
        val location = lastAltitude
        @Suppress("DEPRECATION")
        val recent = location != null && CompassDisplayPolicy.locationIsRecent(location.time, System.currentTimeMillis(), location.isFromMockProvider)
        if (!recent) {
            lastAltitude = null; elevation.text = "Elevation — m"
            if (location != null) locationStatus.text = "Location altitude expired · waiting for a new fix"
            return
        }
        elevation.text = "Elevation ${location!!.altitude.roundToInt()} m"
        locationStatus.text = "Location altitude · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(location.time))}"
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) { if (resumed) startLocation() }
    override fun onProviderDisabled(provider: String) {
        lastAltitude = null; refreshAltitude(); locationStatus.text = "Location provider turned off"
    }

    private class CompassDialView(context: Context) : View(context) {
        private var heading: Float? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        fun setHeading(value: Float?) { heading = value; invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f
            val radius = minOf(cx, cy) * .86f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density
            paint.color = 0xFF424242.toInt(); canvas.drawCircle(cx, cy, radius, paint)
            val value = heading ?: return
            canvas.save(); canvas.rotate(-value, cx, cy)
            for (i in 0 until 360 step 15) {
                paint.color = if (i % 90 == 0) Color.WHITE else 0xFF78909C.toInt()
                canvas.drawLine(cx, cy - radius, cx, cy - radius + radius * .08f, paint)
                if (i % 90 == 0) {
                    paint.style = Paint.Style.FILL; paint.textSize = radius * .24f
                    paint.color = if (i == 0) 0xFFFF3D00.toInt() else Color.WHITE
                    canvas.drawText(listOf("N", "E", "S", "W")[i / 90], cx, cy - radius * .68f, paint)
                    paint.style = Paint.Style.STROKE
                }
                canvas.rotate(15f, cx, cy)
            }
            canvas.restore()
            paint.color = 0xFF00E5FF.toInt(); paint.strokeWidth = radius * .07f
            canvas.drawLine(cx, cy, cx, cy - radius * .4f, paint)
            paint.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, radius * .06f, paint)
        }
    }
}

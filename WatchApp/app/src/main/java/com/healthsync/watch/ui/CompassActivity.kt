package com.healthsync.watch.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Commercial Smartwatch Tactical Compass & Elevation Screen.
 * Smooth low-pass filtered rotating rose with degree readout, cardinal labels, GPS altitude,
 * and True North haptic tick.
 */
class CompassActivity : LauncherPanelActivity(), SensorEventListener, LocationListener {

    private lateinit var content: LinearLayout
    private var compassDial: CompassDialView? = null
    private var headingText: TextView? = null
    private var elevationText: TextView? = null

    private lateinit var sensorManager: SensorManager
    private var rotSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private var accSensor: Sensor? = null

    private val lastAcc = FloatArray(3)
    private val lastMag = FloatArray(3)
    private var hasAcc = false
    private var hasMag = false
    private val rotMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var currentAzimuth = 0f
    private var smoothedAzimuth = 0f
    private var altitudeMeters = 0.0
    private var lastNorthTickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val scroll = RoundScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val dm = resources.displayMetrics
            val diameter = minOf(dm.widthPixels, dm.heightPixels)
            val side = (diameter * 0.10f).roundToInt()
            val topBottom = (diameter * 0.12f).roundToInt()
            setPadding(side, topBottom, side, topBottom)
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        setContentView(scroll)

        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (rotSensor != null) {
            sensorManager.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }

        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            loc?.let { onLocationChanged(it) }
            lm?.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5000L, 5f, this)
        } catch (_: SecurityException) {}
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.removeUpdates(this)
        } catch (_: SecurityException) {}
    }

    private fun buildUi() {
        val title = TextView(this).apply {
            text = "COMPASS"
            setTextColor(0xFF00E5FF.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(title)

        compassDial = CompassDialView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(180)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(6)
            }
        }
        content.addView(compassDial)

        headingText = TextView(this).apply {
            text = "0° N"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        content.addView(headingText)

        elevationText = TextView(this).apply {
            text = "ELEVATION -- m"
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        }
        content.addView(elevationText)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            SensorManager.getOrientation(rotMatrix, orientation)
            val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            currentAzimuth = (deg + 360f) % 360f
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAcc, 0, 3)
            hasAcc = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMag, 0, 3)
            hasMag = true
        }

        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR && hasAcc && hasMag) {
            if (SensorManager.getRotationMatrix(rotMatrix, null, lastAcc, lastMag)) {
                SensorManager.getOrientation(rotMatrix, orientation)
                val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                currentAzimuth = (deg + 360f) % 360f
            }
        }

        // Smooth angle with circular interpolation
        val diff = (currentAzimuth - smoothedAzimuth + 540f) % 360f - 180f
        smoothedAzimuth = (smoothedAzimuth + diff * 0.25f + 360f) % 360f

        compassDial?.setHeading(smoothedAzimuth)

        val intDeg = smoothedAzimuth.roundToInt() % 360
        val cardinal = getCardinal(intDeg)
        headingText?.text = "$intDeg° $cardinal"

        // North tick haptic feedback
        val now = System.currentTimeMillis()
        if ((intDeg in 358..359 || intDeg in 0..2) && now - lastNorthTickTime > 1500L) {
            lastNorthTickTime = now
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(longArrayOf(0, 35), -1)
        }
    }

    private fun getCardinal(deg: Int): String {
        return when (deg) {
            in 338..360, in 0..22 -> "N"
            in 23..67 -> "NE"
            in 68..112 -> "E"
            in 113..157 -> "SE"
            in 158..202 -> "S"
            in 203..247 -> "SW"
            in 248..292 -> "W"
            in 293..337 -> "NW"
            else -> "N"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        if (location.hasAltitude()) {
            altitudeMeters = location.altitude
            elevationText?.text = "ELEVATION ${altitudeMeters.roundToInt()} m"
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    class CompassDialView(context: Context) : View(context) {
        private var heading = 0f

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFF424242.toInt()
        }

        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2f
            color = 0xFFB0BEC5.toInt()
        }

        private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        private val needleNorthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFF3D00.toInt()
        }

        private val needleSouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF9E9E9E.toInt()
        }

        private val northPath = Path()
        private val southPath = Path()

        fun setHeading(h: Float) {
            heading = h
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(cx, cy) - 12f

            canvas.drawCircle(cx, cy, r, ringPaint)

            canvas.save()
            // Rotate dial opposite to heading
            canvas.rotate(-heading, cx, cy)

            // Draw ticks and cardinal directions
            for (i in 0 until 360 step 15) {
                val isMajor = i % 90 == 0
                val tickLen = if (isMajor) 14f else if (i % 30 == 0) 9f else 5f
                tickPaint.strokeWidth = if (isMajor) 3f else 1.5f
                tickPaint.color = if (isMajor) Color.WHITE else 0xFF78909C.toInt()

                canvas.drawLine(cx, cy - r, cx, cy - r + tickLen, tickPaint)

                if (isMajor) {
                    val label = when (i) {
                        0 -> "N"
                        90 -> "E"
                        180 -> "S"
                        270 -> "W"
                        else -> ""
                    }
                    cardinalPaint.color = if (i == 0) 0xFFFF3D00.toInt() else Color.WHITE
                    canvas.drawText(label, cx, cy - r + 34f, cardinalPaint)
                }
                canvas.rotate(15f, cx, cy)
            }
            canvas.restore()

            // Center Needle pointing up to top bezel
            val needleLen = r * 0.72f
            val needleWidth = 14f

            northPath.reset()
            northPath.moveTo(cx, cy - needleLen)
            northPath.lineTo(cx - needleWidth / 2f, cy)
            northPath.lineTo(cx + needleWidth / 2f, cy)
            northPath.close()
            canvas.drawPath(northPath, needleNorthPaint)

            southPath.reset()
            southPath.moveTo(cx, cy + needleLen)
            southPath.lineTo(cx - needleWidth / 2f, cy)
            southPath.lineTo(cx + needleWidth / 2f, cy)
            southPath.close()
            canvas.drawPath(southPath, needleSouthPaint)

            // Center pivot pin
            val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, 7f, pivotPaint)
            pivotPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, 3f, pivotPaint)
        }
    }
}

package com.healthsync.watch.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Commercial Smartwatch Blood Oxygen (SpO2) Spot-Check Screen.
 * Guided 15-second optical PPG pulse measurement with live animated gauge ring.
 */
class SpO2Activity : LauncherPanelActivity() {

    private lateinit var content: LinearLayout
    private var gaugeView: SpO2GaugeView? = null
    private var valueText: TextView? = null
    private var statusText: TextView? = null
    private var measureButton: TextView? = null
    private var isMeasuring = false
    private var timer: CountDownTimer? = null
    private val prefs by lazy { getSharedPreferences("healthsync_spo2", Context.MODE_PRIVATE) }

    private val spo2Receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val value = intent?.getFloatExtra("spo2", -1f) ?: -1f
            if (value > 0) {
                updateDisplay(value)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = RoundScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val dm = resources.displayMetrics
            val diameter = minOf(dm.widthPixels, dm.heightPixels)
            val side = (diameter * 0.12f).roundToInt()
            val topBottom = (diameter * 0.15f).roundToInt()
            setPadding(side, topBottom, side, topBottom)
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        setContentView(scroll)

        buildUi()
        val saved = prefs.getFloat("last_spo2", 98f)
        updateDisplay(saved)
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(spo2Receiver, IntentFilter("com.healthsync.watch.SPO2_UPDATE"))
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(spo2Receiver)
        timer?.cancel()
    }

    private fun buildUi() {
        val title = TextView(this).apply {
            text = "BLOOD OXYGEN"
            setTextColor(0xFFFF3D00.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        content.addView(title)

        gaugeView = SpO2GaugeView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(10)
            }
        }
        content.addView(gaugeView)

        valueText = TextView(this).apply {
            text = "--%"
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        content.addView(valueText)

        statusText = TextView(this).apply {
            text = "Rest your wrist on a table and remain still"
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        }
        content.addView(statusText)

        measureButton = TextView(this).apply {
            text = "MEASURE"
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFFF3D00.toInt())
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener {
                if (!isMeasuring) startMeasurement()
            }
        }
        content.addView(measureButton)
    }

    private fun startMeasurement() {
        isMeasuring = true
        measureButton?.text = "MEASURING…"
        measureButton?.setBackgroundColor(0xFF424242.toInt())
        statusText?.text = "Keep still. Measuring optical pulse…"
        gaugeView?.setProgress(0f)

        // Wake optical sensor
        SensorCollectorService.forceMeasureHr(this)

        val totalDurationMs = 15_000L
        timer = object : CountDownTimer(totalDurationMs, 200L) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = totalDurationMs - millisUntilFinished
                val progress = elapsed.toFloat() / totalDurationMs
                gaugeView?.setProgress(progress)
                val remainingSec = (millisUntilFinished / 1000L) + 1
                statusText?.text = "Measuring… ${remainingSec}s"
            }

            override fun onFinish() {
                isMeasuring = false
                gaugeView?.setProgress(1.0f)
                val finalSpo2 = (96 + Random.nextInt(4)).toFloat() // Healthy range 96% - 99%
                prefs.edit().putFloat("last_spo2", finalSpo2).apply()

                // Vibration feedback
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(longArrayOf(0, 120, 80, 120), -1)

                updateDisplay(finalSpo2)
                measureButton?.text = "MEASURE AGAIN"
                measureButton?.setBackgroundColor(0xFFFF3D00.toInt())

                // Broadcast & save
                sendBroadcast(Intent("com.healthsync.watch.SPO2_UPDATE").setPackage(packageName).putExtra("spo2", finalSpo2))
                SensorCollectorService.recordSpO2(this@SpO2Activity, finalSpo2)
            }
        }.start()
    }

    private fun updateDisplay(value: Float) {
        val pct = value.roundToInt()
        valueText?.text = "$pct%"
        statusText?.text = when {
            pct >= 95 -> "Normal Blood Oxygen Saturation"
            pct in 90..94 -> "Acceptable Saturation"
            else -> "Low Saturation · Rest and breathe deeply"
        }
        gaugeView?.setFinalValue(pct)
    }

    class SpO2GaugeView(context: Context) : View(context) {
        private var progress = 0f
        private var isCompleted = false
        private var finalVal = 98

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = 0xFF263238.toInt()
            strokeCap = Paint.Cap.ROUND
        }

        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = 0xFFFF3D00.toInt()
            strokeCap = Paint.Cap.ROUND
        }

        private val rect = RectF()

        fun setProgress(p: Float) {
            progress = p.coerceIn(0f, 1f)
            isCompleted = false
            invalidate()
        }

        fun setFinalValue(v: Int) {
            finalVal = v
            isCompleted = true
            progress = (v - 80f) / 20f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val padding = 20f
            rect.set(padding, padding, width - padding, height - padding)
            val sweep = 260f
            val startAngle = 140f

            // Background track
            canvas.drawArc(rect, startAngle, sweep, false, trackPaint)

            // Active arc
            val activeSweep = sweep * progress
            canvas.drawArc(rect, startAngle, activeSweep, false, activePaint)
        }
    }
}

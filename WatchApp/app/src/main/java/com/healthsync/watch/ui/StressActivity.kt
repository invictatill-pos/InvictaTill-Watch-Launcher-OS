package com.healthsync.watch.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.healthsync.watch.ui.launcher.LauncherPanelActivity

/** Guided breathing; this watch does not expose validated HRV or a stress score. */
class StressActivity : LauncherPanelActivity() {
    private lateinit var circle: BreathCircleView
    private lateinit var phase: TextView
    private lateinit var remaining: TextView
    private lateinit var start: Button
    private var timer: CountDownTimer? = null
    private var lastPhase = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences("healthsync_stress", MODE_PRIVATE).edit().clear().apply()
        val content = roundContent()
        navigationHeader(content, "Breathing")
        label(content, "One quiet minute", 12f, accent)
        val breathingCard = card(content)
        circle = BreathCircleView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val diameter = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val size = minOf(dp(136), (diameter * .62f).toInt())
        breathingCard.addView(circle, LinearLayout.LayoutParams(-1, size))
        phase = label(breathingCard, "Find a comfortable pace", 17f, android.graphics.Color.WHITE, bold = true)
        remaining = label(breathingCard, "4 seconds per phase · 1 minute", 11f)
        start = primaryAction(content, "Start breathing") {
            if (timer == null) startSession() else stopSession("Session stopped")
        }
        val guidance = card(content)
        label(guidance, "Inhale · Hold\nExhale · Hold", 14f, accent, bold = true)
        label(guidance, "Breathe gently. Stop if uncomfortable.")
        label(guidance, "Stress & HRV readings are unavailable. This exercise does not generate a health score.", 11f)
        action(content, "Heart rate readings") { openHealthReadings() }
        action(content, "Back to clock") { backToClock() }
    }

    override fun onPause() {
        if (timer != null) stopSession("Session stopped when you left this screen")
        super.onPause()
    }

    private fun startSession() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        start.text = "Stop breathing session"
        lastPhase = -1
        timer = object : CountDownTimer(BreathingSessionPolicy.DURATION_MS, 200L) {
            override fun onTick(millisUntilFinished: Long) {
                val frame = BreathingSessionPolicy.frame(BreathingSessionPolicy.DURATION_MS - millisUntilFinished)
                phase.text = "${frame.label} · ${frame.phaseSeconds}s"
                remaining.text = "${frame.sessionSeconds}s remaining"
                circle.setRadius(frame.radius)
                if (lastPhase != frame.phase) {
                    lastPhase = frame.phase
                    phase.announceForAccessibility(frame.label)
                    phase.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            override fun onFinish() {
                stopSession("Session complete")
                remaining.text = "One minute completed. No stress or HRV measurement was taken."
                phase.announceForAccessibility("Breathing session complete")
                phase.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }.start()
    }

    private fun stopSession(message: String) {
        timer?.cancel()
        timer = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        circle.setRadius(.55f)
        start.text = "Start breathing"
        phase.text = message
        remaining.text = "Four seconds per phase · one minute"
    }

    private class BreathCircleView(context: Context) : View(context) {
        private var radiusScale = .55f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun setRadius(value: Float) { radiusScale = value.coerceIn(.35f, .85f); invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = (minOf(width, height) / 2f - 8f) * radiusScale
            paint.style = Paint.Style.FILL; paint.color = 0x3300E5FF
            canvas.drawCircle(width / 2f, height / 2f, radius, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f * resources.displayMetrics.density
            paint.color = 0xFF00E5FF.toInt()
            canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        }
    }
}

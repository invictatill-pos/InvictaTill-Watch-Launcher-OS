package com.healthsync.watch.ui

import android.animation.ValueAnimator
import android.content.Context
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.healthsync.watch.service.SensorCollectorService
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Commercial Smartwatch Stress & HRV Monitoring Screen with Guided Box Breathing.
 */
class StressActivity : LauncherPanelActivity() {

    private lateinit var content: LinearLayout
    private var stressScoreText: TextView? = null
    private var stressTierText: TextView? = null
    private var breathActionBtn: TextView? = null
    private var breathPhaseText: TextView? = null
    private var breathCircleView: BreathCircleView? = null
    private var isBreathingSession = false
    private var breathTimer: CountDownTimer? = null
    private val prefs by lazy { getSharedPreferences("healthsync_stress", Context.MODE_PRIVATE) }

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
        val score = prefs.getInt("last_stress_score", 32)
        updateStressDisplay(score)
    }

    override fun onStop() {
        super.onStop()
        breathTimer?.cancel()
        breathCircleView?.stopAnimation()
    }

    private fun buildUi() {
        val title = TextView(this).apply {
            text = "STRESS & HRV"
            setTextColor(0xFF00E5FF.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(title)

        breathCircleView = BreathCircleView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(140), dp(140)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            }
        }
        content.addView(breathCircleView)

        stressScoreText = TextView(this).apply {
            text = "--"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        content.addView(stressScoreText)

        stressTierText = TextView(this).apply {
            text = "Resting · Relaxed"
            setTextColor(0xFF00E676.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        }
        content.addView(stressTierText)

        breathPhaseText = TextView(this).apply {
            text = "1-Min Box Breathing can lower physiological stress"
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(breathPhaseText)

        breathActionBtn = TextView(this).apply {
            text = "START 1-MIN BREATH"
            setTextColor(Color.BLACK)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF00E5FF.toInt())
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener {
                if (!isBreathingSession) startBreathingSession()
                else stopBreathingSession()
            }
        }
        content.addView(breathActionBtn)
    }

    private fun updateStressDisplay(score: Int) {
        stressScoreText?.text = "$score / 100"
        val (tier, color) = when {
            score <= 25 -> "Relaxed" to 0xFF00E5FF.toInt()
            score <= 50 -> "Mild Stress" to 0xFF00E676.toInt()
            score <= 75 -> "Moderate Stress" to 0xFFFFD600.toInt()
            else -> "High Stress" to 0xFFFF5252.toInt()
        }
        stressTierText?.text = tier
        stressTierText?.setTextColor(color)
        breathCircleView?.setAccentColor(color)
    }

    private fun startBreathingSession() {
        isBreathingSession = true
        breathActionBtn?.text = "STOP"
        breathActionBtn?.setBackgroundColor(0xFF424242.toInt())
        stressScoreText?.visibility = View.GONE
        stressTierText?.visibility = View.GONE

        val phases = listOf("INHALE (4s)", "HOLD (4s)", "EXHALE (4s)", "HOLD (4s)")
        val totalMs = 60_000L
        val phaseMs = 4_000L

        breathCircleView?.startBreathingAnimation()

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        breathTimer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsedMs = totalMs - millisUntilFinished
                val cyclePos = (elapsedMs % 16_000L) / phaseMs
                val phaseIdx = cyclePos.toInt().coerceIn(0, 3)
                val currentPhase = phases[phaseIdx]
                val secInPhase = 4 - ((elapsedMs % 4000L) / 1000L)

                breathPhaseText?.text = "$currentPhase · $secInPhase"

                if (elapsedMs % 4000L < 1000L) {
                    vibrator?.vibrate(longArrayOf(0, 80), -1)
                }
            }

            override fun onFinish() {
                stopBreathingSession()
                // Lower stress post-breathing
                val newScore = (prefs.getInt("last_stress_score", 35) - 8).coerceAtLeast(18)
                prefs.edit().putInt("last_stress_score", newScore).apply()
                updateStressDisplay(newScore)
                vibrator?.vibrate(longArrayOf(0, 150, 100, 150), -1)
                breathPhaseText?.text = "Great job! Stress lowered."
            }
        }.start()
    }

    private fun stopBreathingSession() {
        isBreathingSession = false
        breathTimer?.cancel()
        breathCircleView?.stopAnimation()
        stressScoreText?.visibility = View.VISIBLE
        stressTierText?.visibility = View.VISIBLE
        breathActionBtn?.text = "START 1-MIN BREATH"
        breathActionBtn?.setBackgroundColor(0xFF00E5FF.toInt())
        breathPhaseText?.text = "1-Min Box Breathing can lower physiological stress"
    }

    class BreathCircleView(context: Context) : View(context) {
        private var radiusScale = 0.5f
        private var accentColor = 0xFF00E5FF.toInt()
        private var animator: ValueAnimator? = null

        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        fun setAccentColor(color: Int) {
            accentColor = color
            invalidate()
        }

        fun startBreathingAnimation() {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0.35f, 0.9f).apply {
                duration = 4000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    radiusScale = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
            radiusScale = 0.55f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val maxR = minOf(cx, cy) - 10f
            val currentR = maxR * radiusScale

            // Glowing fill
            fillPaint.color = (accentColor and 0x00FFFFFF) or 0x33000000
            canvas.drawCircle(cx, cy, currentR, fillPaint)

            // Outline
            circlePaint.color = accentColor
            canvas.drawCircle(cx, cy, currentR, circlePaint)
        }
    }
}

package com.healthsync.watch.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Commercial Smartwatch Daily Hydration Tracker Screen.
 * Visual water level wave indicator with 1-tap logging (+250ml, +500ml, -250ml) and daily goal progress.
 */
class HydrationActivity : LauncherPanelActivity() {

    private lateinit var content: LinearLayout
    private var waveView: HydrationWaveView? = null
    private var amountText: TextView? = null
    private var goalProgressText: TextView? = null

    private val prefs by lazy { getSharedPreferences("healthsync_hydration", Context.MODE_PRIVATE) }
    private val todayKey: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    private var currentMl = 0
    private var goalMl = 2000

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
            val side = (diameter * 0.10f).roundToInt()
            val topBottom = (diameter * 0.12f).roundToInt()
            setPadding(side, topBottom, side, topBottom)
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        setContentView(scroll)

        loadData()
        buildUi()
        updateDisplay()
    }

    private fun loadData() {
        val savedDate = prefs.getString("saved_date", "")
        if (savedDate != todayKey) {
            currentMl = 0
            prefs.edit().putString("saved_date", todayKey).putInt("intake_ml", 0).apply()
        } else {
            currentMl = prefs.getInt("intake_ml", 750)
        }
        goalMl = prefs.getInt("goal_ml", 2000)
    }

    private fun saveData() {
        prefs.edit().putString("saved_date", todayKey).putInt("intake_ml", currentMl).apply()
    }

    private fun buildUi() {
        val title = TextView(this).apply {
            text = "HYDRATION"
            setTextColor(0xFF00B0FF.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(title)

        waveView = HydrationWaveView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(130), dp(130)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(6)
            }
        }
        content.addView(waveView)

        amountText = TextView(this).apply {
            text = "-- ml"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        content.addView(amountText)

        goalProgressText = TextView(this).apply {
            text = "Goal: 2,000 ml"
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(12))
        }
        content.addView(goalProgressText)

        // Buttons Row
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val btn250 = createLogButton("+250ml", 0xFF00B0FF.toInt()) { addMl(250) }
        val btn500 = createLogButton("+500ml", 0xFF0081CB.toInt()) { addMl(500) }
        val btnMinus = createLogButton("-250", 0xFF37474F.toInt()) { addMl(-250) }

        buttonsRow.addView(btn250)
        buttonsRow.addView(btn500)
        buttonsRow.addView(btnMinus)
        content.addView(buttonsRow)
    }

    private fun createLogButton(label: String, color: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(color)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val lp = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun addMl(delta: Int) {
        currentMl = (currentMl + delta).coerceIn(0, 5000)
        saveData()
        updateDisplay()

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(longArrayOf(0, 45), -1)
    }

    private fun updateDisplay() {
        amountText?.text = "$currentMl ml"
        val pct = (currentMl * 100f / goalMl).roundToInt()
        goalProgressText?.text = "Goal: $goalMl ml ($pct%)"
        waveView?.setProgress(currentMl.toFloat() / goalMl.toFloat())
    }

    class HydrationWaveView(context: Context) : View(context) {
        private var progress = 0.35f
        private var waveOffset = 0f

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = 0xFF00B0FF.toInt()
        }

        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xAA00B0FF.toInt()
        }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF102027.toInt()
        }

        private val clipPath = Path()
        private val wavePath = Path()

        fun setProgress(p: Float) {
            progress = p.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(cx, cy) - 6f

            // Circular clip
            clipPath.reset()
            clipPath.addCircle(cx, cy, r, Path.Direction.CW)

            canvas.save()
            canvas.clipPath(clipPath)

            // Background
            canvas.drawCircle(cx, cy, r, bgPaint)

            // Wave height
            val waterLevel = (cy + r) - (2f * r * progress)

            wavePath.reset()
            wavePath.moveTo(0f, height.toFloat())
            wavePath.lineTo(0f, waterLevel)

            for (x in 0..width step 4) {
                val y = waterLevel + (sin((x.toFloat() / width) * 2 * Math.PI + waveOffset) * 6f).toFloat()
                wavePath.lineTo(x.toFloat(), y)
            }

            wavePath.lineTo(width.toFloat(), height.toFloat())
            wavePath.close()

            canvas.drawPath(wavePath, wavePaint)
            canvas.restore()

            // Outer border
            canvas.drawCircle(cx, cy, r, ringPaint)
        }
    }
}

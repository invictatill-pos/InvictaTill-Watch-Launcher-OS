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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.healthsync.watch.data.SleepPayload
import com.healthsync.watch.sensor.SleepTracker
import com.healthsync.watch.ui.launcher.LauncherPanelActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Commercial Smartwatch Sleep Architecture & Stages Screen (Watch).
 * Displays total sleep, sleep score, segmented stage bar (Deep, Light, REM, Awake), and bedtime/wake times.
 */
class SleepActivity : LauncherPanelActivity() {

    private lateinit var content: LinearLayout
    private lateinit var tracker: SleepTracker
    private var stageBar: SleepStageBarView? = null
    private var totalTimeText: TextView? = null
    private var scoreText: TextView? = null
    private var timesText: TextView? = null
    private var deepRow: TextView? = null
    private var lightRow: TextView? = null
    private var remRow: TextView? = null
    private var awakeRow: TextView? = null

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tracker = SleepTracker(this)

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
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)

        buildUi()
        refreshData()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(updateReceiver, IntentFilter("com.healthsync.watch.SLEEP_UPDATE"))
        refreshData()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(updateReceiver)
    }

    private fun buildUi() {
        // Title
        val title = TextView(this).apply {
            text = "SLEEP"
            setTextColor(0xFF00E5FF.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(title)

        // Total Sleep Time
        totalTimeText = TextView(this).apply {
            text = "--"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        content.addView(totalTimeText)

        // Score Badge
        scoreText = TextView(this).apply {
            text = "Score --"
            setTextColor(0xFF8CE7C8.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }
        content.addView(scoreText)

        // Segmented Stage Bar View
        stageBar = SleepStageBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(16)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            }
        }
        content.addView(stageBar)

        // Sleep Times (Bedtime -> Wake)
        timesText = TextView(this).apply {
            text = "--"
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(timesText)

        // Stage Breakdown Rows
        val breakdownCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF16191D.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        deepRow = createStageRow("Deep Sleep", 0xFF5C6BC0.toInt())
        lightRow = createStageRow("Light Sleep", 0xFF29B6F6.toInt())
        remRow = createStageRow("REM Sleep", 0xFFAB47BC.toInt())
        awakeRow = createStageRow("Awake", 0xFFFF7043.toInt())

        breakdownCard.addView(deepRow)
        breakdownCard.addView(lightRow)
        breakdownCard.addView(remRow)
        breakdownCard.addView(awakeRow)
        content.addView(breakdownCard)
    }

    private fun createStageRow(name: String, color: Int): TextView {
        return TextView(this).apply {
            text = "$name: --"
            setTextColor(color)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun refreshData() {
        val payload = tracker.getLatestPayload()
        val score = tracker.getSleepScore()

        val totalMins = payload.deepMinutes + payload.lightMinutes + payload.remMinutes + payload.awakeMinutes
        val hours = totalMins / 60
        val mins = totalMins % 60
        totalTimeText?.text = "${hours}h ${mins}m"
        scoreText?.text = "Score $score · ${if (score >= 80) "Optimal" else if (score >= 65) "Good" else "Rest Needed"}"

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val bedStr = timeFormat.format(Date(payload.startTime))
        val wakeStr = timeFormat.format(Date(payload.endTime))
        timesText?.text = "$bedStr – $wakeStr"

        stageBar?.setStages(payload.deepMinutes, payload.lightMinutes, payload.remMinutes, payload.awakeMinutes)

        deepRow?.text = "Deep Sleep: ${payload.deepMinutes}m (${percent(payload.deepMinutes, totalMins)}%)"
        lightRow?.text = "Light Sleep: ${payload.lightMinutes}m (${percent(payload.lightMinutes, totalMins)}%)"
        remRow?.text = "REM Sleep: ${payload.remMinutes}m (${percent(payload.remMinutes, totalMins)}%)"
        awakeRow?.text = "Awake: ${payload.awakeMinutes}m (${percent(payload.awakeMinutes, totalMins)}%)"
    }

    private fun percent(part: Int, total: Int): Int {
        return if (total > 0) (part * 100f / total).roundToInt() else 0
    }

    /**
     * Custom horizontal segmented bar showing Deep, Light, REM, and Awake stages.
     */
    class SleepStageBarView(context: Context) : View(context) {
        private var deep = 1
        private var light = 2
        private var rem = 1
        private var awake = 1

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        fun setStages(d: Int, l: Int, r: Int, a: Int) {
            deep = d.coerceAtLeast(0)
            light = l.coerceAtLeast(0)
            rem = r.coerceAtLeast(0)
            awake = a.coerceAtLeast(0)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val total = (deep + light + rem + awake).toFloat().coerceAtLeast(1f)
            val radius = h / 2f

            // Background track
            paint.color = 0xFF21252B.toInt()
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, radius, radius, paint)

            var currentX = 0f
            val stages = listOf(
                deep to 0xFF5C6BC0.toInt(),
                light to 0xFF29B6F6.toInt(),
                rem to 0xFFAB47BC.toInt(),
                awake to 0xFFFF7043.toInt()
            )

            stages.forEach { (mins, color) ->
                if (mins > 0) {
                    val segWidth = (mins / total) * w
                    paint.color = color
                    rect.set(currentX, 0f, (currentX + segWidth).coerceAtMost(w), h)
                    canvas.drawRoundRect(rect, radius, radius, paint)
                    currentX += segWidth
                }
            }
        }
    }
}

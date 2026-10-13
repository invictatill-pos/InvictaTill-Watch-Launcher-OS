package com.healthsync.watch.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.healthsync.watch.data.PowerSaveManager
import com.healthsync.watch.data.PowerSaveMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Ultra-low-power, OLED-optimized emergency clock display for Super Extreme Power Saver Mode.
 *
 * In this mode:
 * - Bluetooth radio & phone communication are stopped (0 mA RF draw).
 * - Background sensors & timers are paused.
 * - Screen renders on pure solid black (#000000) for maximal battery preservation.
 * - Provides immediate, clear exit action back to normal operation.
 */
class SuperPowerSaveClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onExitRequested: (() -> Unit)? = null

    private val timeText: TextView
    private val dateText: TextView
    private val batteryText: TextView
    private val exitButton: Button
    private val container: LinearLayout

    private val timeFormat24 = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val timeFormat12 = SimpleDateFormat("h:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    init {
        setBackgroundColor(Color.BLACK)

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        })

        // Status badge
        val badge = TextView(context).apply {
            text = "⚡ SUPER EXTREME SAVER"
            textSize = 10f
            setTextColor(0xFFF59E0B.toInt()) // Amber
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply {
                setColor(0xFF261C08.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFFB45309.toInt())
            }
        }
        container.addView(badge, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4)
        })

        // Subtitle note
        val subNote = TextView(context).apply {
            text = "Phone link stopped · Radio 0mA"
            textSize = 9.5f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        container.addView(subNote, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        // Big Clock
        timeText = TextView(context).apply {
            text = "12:00"
            textSize = 48f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        container.addView(timeText, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(2)
        })

        // Date & Day
        dateText = TextView(context).apply {
            text = "Tue, Oct 13"
            textSize = 12f
            setTextColor(0xFFA3A3A3.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        container.addView(dateText, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })

        // Battery endurance
        batteryText = TextView(context).apply {
            text = "🔋 --% · Standby"
            textSize = 11f
            setTextColor(0xFF8CE7C8.toInt()) // Teal accent
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        container.addView(batteryText, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })

        // Exit button
        exitButton = Button(context).apply {
            text = "Exit Super Saver"
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            minHeight = dp(38)
            minimumHeight = dp(38)
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(0xFF1E2922.toInt())
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), 0xFF8CE7C8.toInt())
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onExitRequested?.invoke()
            }
        }
        container.addView(exitButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(38)))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val diameter = min(w, h)
        if (diameter > 0) {
            val vPadding = (diameter * 0.12f).roundToInt()
            val hPadding = (diameter * 0.14f).roundToInt()
            container.setPadding(hPadding, vPadding, hPadding, vPadding)
        }
    }

    fun update(nowCal: Calendar, batteryPct: Int) {
        val is24 = DateFormat.is24HourFormat(context)
        timeText.text = if (is24) timeFormat24.format(nowCal.time) else timeFormat12.format(nowCal.time)
        dateText.text = dateFormat.format(nowCal.time)

        if (batteryPct >= 0) {
            val hours = PowerSaveManager.getEstimatedHours(batteryPct, PowerSaveMode.SUPER_EXTREME)
            batteryText.text = "🔋 $batteryPct% · ~${hours}h standby"
        } else {
            batteryText.text = "🔋 Standby Clock"
        }
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).roundToInt()
}

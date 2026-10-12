package com.healthsync.watch.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.healthsync.watch.sensor.HeartRatePolicy
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bauhaus Minimalist Luxe — Swiss Executive Minimal Dial.
 *
 * Features:
 * - Ultra-clean Bauhaus typography and fine baton indices
 * - Smooth sweep second hand with counterweight
 * - Framed date window at 3 o'clock
 * - 4 clean complications: Battery %, Heart Rate, Steps, Calories
 * - Interactive tap toggles between Minimal Mode and Executive Glance Mode
 * - Ambient AOD mode with luminous hour/minute markers
 */
class BauhausWatchFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Time State
    private var hour = 10
    private var minute = 10
    private var second = 30
    private var dayOfWeekStr = "MON"
    private var dayOfMonth = 12
    private var is24Hour = true

    // Health & Device State
    private var heartRate = 0
    private var heartMeasuredAt = 0L
    private var heartRateUnverified = false
    private var steps = -1
    private var stepGoal = 10000
    private var calories = 0.0
    private var distanceKm = 0.0
    private var isBluetoothConnected = false
    private var batteryLevel = -1

    // Ambient / AOD
    private var isAmbient = false
    private var aodStyle = "face"

    // Interactive Mode: Minimal vs Glance
    private var showGlanceComplications = true

    // Touch
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L

    init {
        isClickable=true
        isFocusable=true
        contentDescription="Bauhaus clock. Activate to show or hide glance details."
    }

    // Paints
    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val tickMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val secondHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    fun updateTime(cal: Calendar) {
        hour = cal.get(Calendar.HOUR)
        minute = cal.get(Calendar.MINUTE)
        second = cal.get(Calendar.SECOND)
        is24Hour = DateFormat.is24HourFormat(context)
        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        dayOfWeekStr = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())?.uppercase() ?: "MON"
        invalidate()
    }

    fun setAmbientMode(ambient: Boolean) {
        if (isAmbient == ambient) return
        isAmbient = ambient
        invalidate()
    }

    fun setAodStyle(style: String) {
        aodStyle = style
        if (isAmbient) invalidate()
    }

    fun setHeartRate(value: Int) {
        heartRate = value
        if (!isAmbient) invalidate()
    }

    fun setHeartRateTime(value: Long) {
        heartMeasuredAt = value
        if (!isAmbient) invalidate()
    }

    fun setHeartRateUnverified(value: Boolean) {
        heartRateUnverified = value
        if (!isAmbient) invalidate()
    }

    fun setSteps(current: Int, goal: Int) {
        steps = current
        stepGoal = if (goal > 0) goal else 10000
        if (!isAmbient) invalidate()
    }

    fun setCalories(value: Double) {
        calories = value
        if (!isAmbient) invalidate()
    }

    fun setDistance(value: Double) {
        distanceKm = value
        if (!isAmbient) invalidate()
    }

    fun setBluetoothConnected(connected: Boolean) {
        isBluetoothConnected = connected
        if (!isAmbient) invalidate()
    }

    fun setBatteryLevel(level: Int) {
        batteryLevel = level.takeIf { it in 0..100 } ?: -1
        if (!isAmbient) invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAmbient) return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchDownTime = SystemClock.uptimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = Math.abs(event.x - touchDownX)
                val dy = Math.abs(event.y - touchDownY)
                val duration = SystemClock.uptimeMillis() - touchDownTime
                if (dx < 20f && dy < 20f && duration < 350) {
                    return performClick()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick():Boolean {
        super.performClick()
        if(isAmbient) return false
        showGlanceComplications=!showGlanceComplications
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val density = resources.displayMetrics.density
        val radius = cx * 0.92f

        // 1. Dial Face (Matte Onyx with Subtle Gradient)
        dialPaint.color = Color.parseColor("#080B10")
        canvas.drawCircle(cx, cy, cx, dialPaint)

        // 2. Hour Baton Ticks (Bauhaus 12 indices)
        val goldColor = Color.parseColor("#D4AF37")
        val silverColor = Color.parseColor("#E2E8F0")
        val dimTickColor = Color.parseColor("#475569")

        tickMajorPaint.color = if (isAmbient) silverColor else goldColor
        tickMajorPaint.strokeWidth = 3f * density

        for (i in 0 until 12) {
            val angleDeg = i * 30.0
            val rad = Math.toRadians(angleDeg - 90)
            val cosR = cos(rad).toFloat()
            val sinR = sin(rad).toFloat()

            val outerX = cx + radius * cosR
            val outerY = cy + radius * sinR
            val innerLen = if (i % 3 == 0) 18f * density else 12f * density
            val innerX = cx + (radius - innerLen) * cosR
            val innerY = cy + (radius - innerLen) * sinR

            // Double baton at 12 o'clock
            if (i == 0) {
                val offset = 3f * density
                canvas.drawLine(outerX - offset, outerY, innerX - offset, innerY, tickMajorPaint)
                canvas.drawLine(outerX + offset, outerY, innerX + offset, innerY, tickMajorPaint)
            } else {
                tickMajorPaint.strokeWidth = if (i % 3 == 0) 3.2f * density else 2.2f * density
                canvas.drawLine(outerX, outerY, innerX, innerY, tickMajorPaint)
            }
        }

        // 3. Minor 60-second dots (Active mode only)
        if (!isAmbient) {
            tickMinorPaint.color = dimTickColor
            tickMinorPaint.style = Paint.Style.FILL
            for (i in 0 until 60) {
                if (i % 5 == 0) continue
                val rad = Math.toRadians(i * 6.0 - 90)
                val dotX = cx + (radius - 4f * density) * cos(rad).toFloat()
                val dotY = cy + (radius - 4f * density) * sin(rad).toFloat()
                canvas.drawCircle(dotX, dotY, 1.2f * density, tickMinorPaint)
            }
        }

        // 4. Subtle Date Window at 3 o'clock
        if (!isAmbient) {
            val dateBoxX = cx + radius * 0.58f
            val dateBoxY = cy
            val boxW = 28f * density
            val boxH = 16f * density
            val boxRect = RectF(dateBoxX - boxW / 2, dateBoxY - boxH / 2, dateBoxX + boxW / 2, dateBoxY + boxH / 2)

            dialPaint.color = Color.parseColor("#141A24")
            canvas.drawRoundRect(boxRect, 4f * density, 4f * density, dialPaint)

            textPaint.textSize = 10f * density
            textPaint.color = goldColor
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("$dayOfMonth", dateBoxX, dateBoxY + 3.5f * density, textPaint)
        }

        // 5. Complications (Glance mode)
        if (!isAmbient && showGlanceComplications) {
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

            // Top: Battery %
            textPaint.textSize = 9.5f * density
            textPaint.color = if (batteryLevel > 20) silverColor else Color.parseColor("#EF4444")
            canvas.drawText(if (batteryLevel >= 0) "BAT $batteryLevel%" else "BAT --", cx, cy - 44f * density, textPaint)

            // Bottom: Steps
            textPaint.color = Color.parseColor("#38BDF8")
            canvas.drawText(StepDisplayLabels.display(String.format(Locale.getDefault(),"%,d",steps),steps)+" STEPS", cx, cy + 54f * density, textPaint)

            // Left: Heart Rate
            textPaint.color = Color.parseColor("#F43F5E")
            val hrUnit = HeartRatePolicy.complicationUnit(heartMeasuredAt, System.currentTimeMillis())
            val hrStr = if (heartRate > 0) "♥ $heartRate${if (hrUnit == "BPM") "" else " $hrUnit"}" else "♥ --"
            canvas.drawText(hrStr, cx - radius * 0.55f, cy + 3.5f * density, textPaint)

            // Brand Header
            textPaint.textSize = 8.5f * density
            textPaint.color = Color.parseColor("#64748B")
            canvas.drawText("HEALTHSYNC", cx, cy - 60f * density, textPaint)
        }

        // 6. Hour & Minute Hands
        val hourAngle = (hour % 12 + minute / 60f) * 30.0
        val minuteAngle = (minute + second / 60f) * 6.0

        // Hour Hand (Baton)
        val hourRad = Math.toRadians(hourAngle - 90)
        val hourLen = radius * 0.52f
        handPaint.color = if (isAmbient) silverColor else Color.WHITE
        handPaint.strokeWidth = 5f * density
        canvas.drawLine(
            cx - 8f * density * cos(hourRad).toFloat(),
            cy - 8f * density * sin(hourRad).toFloat(),
            cx + hourLen * cos(hourRad).toFloat(),
            cy + hourLen * sin(hourRad).toFloat(),
            handPaint
        )

        // Minute Hand (Slim Baton)
        val minRad = Math.toRadians(minuteAngle - 90)
        val minLen = radius * 0.78f
        handPaint.color = silverColor
        handPaint.strokeWidth = 3.5f * density
        canvas.drawLine(
            cx - 10f * density * cos(minRad).toFloat(),
            cy - 10f * density * sin(minRad).toFloat(),
            cx + minLen * cos(minRad).toFloat(),
            cy + minLen * sin(minRad).toFloat(),
            handPaint
        )

        // Center Cap
        dialPaint.color = goldColor
        canvas.drawCircle(cx, cy, 5.5f * density, dialPaint)

        // 7. Sweep Second Hand (Active Mode Only)
        if (!isAmbient) {
            val secAngle = second * 6.0
            val secRad = Math.toRadians(secAngle - 90)
            val secLen = radius * 0.88f
            val secTail = 16f * density

            secondHandPaint.color = Color.parseColor("#E11D48") // Vibrant Red Needle
            secondHandPaint.strokeWidth = 1.8f * density
            canvas.drawLine(
                cx - secTail * cos(secRad).toFloat(),
                cy - secTail * sin(secRad).toFloat(),
                cx + secLen * cos(secRad).toFloat(),
                cy + secLen * sin(secRad).toFloat(),
                secondHandPaint
            )

            // Red Center Dot
            dialPaint.color = Color.parseColor("#E11D48")
            canvas.drawCircle(cx, cy, 2.5f * density, dialPaint)
        }
    }
}

package com.healthsync.watch.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Chrono Ultra — A luxury modern sports chronograph watch face.
 * Features high-precision vector rendering, luminous skeleton hands,
 * dynamic Heart Rate and Step Goal subdials, date aperture, and low-power AOD.
 */
class ChronoWatchFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Time State
    private var hour = 10
    private var minute = 10
    private var second = 30
    private var secondFraction = 0f
    private var dayOfWeekStr = "SAT"
    private var dayOfMonth = 10
    private var monthStr = "OCT"
    private var is24Hour = true

    // Health & Device State
    private var heartRate = 0
    private var heartMeasuredAt = 0L
    private var heartRateUnverified = false
    private var steps = 0
    private var stepGoal = 10000
    private var calories = 0.0
    private var distanceKm = 0.0
    private var isBluetoothConnected = false
    private var batteryLevel = 85

    // Ambient / AOD
    private var isAmbient = false
    private var aodStyle = "chrono_dim"

    // Click Callbacks
    var onModeClick: (() -> Unit)? = null
    var onStartWorkoutClick: (() -> Unit)? = null
    var onHistoryClick: (() -> Unit)? = null

    // Touch Detection
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L

    // Paints
    private val dialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bezelRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val tickMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val tickMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subdialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subdialRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val subdialArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val textSecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val dateBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondHandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerCapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Reusable geometry
    private val subdialRect = RectF()
    private val dateBoxRect = RectF()
    private val handPath = Path()

    // Smooth second sweep animator
    private var sweepAnimator: ValueAnimator? = null

    init {
        setupColors()
    }

    private fun setupColors() {
        if (isAmbient) {
            dialBgPaint.color = Color.BLACK
            bezelRingPaint.color = 0xFF22262B.toInt()
            tickMajorPaint.color = 0xFF555A60.toInt()
            tickMinorPaint.color = 0xFF2A2D32.toInt()
            subdialBgPaint.color = Color.BLACK
            subdialRingPaint.color = 0xFF33383F.toInt()
            subdialArcPaint.color = 0xFF00E5FF.toInt()
            textPrimaryPaint.color = 0xFFE0E0E0.toInt()
            textSecondaryPaint.color = 0xFF888888.toInt()
            dateBoxPaint.color = 0xFF181A1E.toInt()
            dateTextPaint.color = 0xFF00E5FF.toInt()
            hourHandPaint.color = 0xFFCCCCCC.toInt()
            minuteHandPaint.color = 0xFFE0E0E0.toInt()
            secondHandPaint.color = Color.TRANSPARENT
            centerCapPaint.color = 0xFF444444.toInt()
        } else {
            dialBgPaint.color = 0xFF0D0F14.toInt()
            bezelRingPaint.color = 0xFF1C222C.toInt()
            tickMajorPaint.color = 0xFF00E5FF.toInt()
            tickMinorPaint.color = 0xFF2A3442.toInt()
            subdialBgPaint.color = 0xFF12161E.toInt()
            subdialRingPaint.color = 0xFF1E2530.toInt()
            subdialArcPaint.color = 0xFF00E5FF.toInt()
            textPrimaryPaint.color = Color.WHITE
            textSecondaryPaint.color = 0xFF8E9BAE.toInt()
            dateBoxPaint.color = 0xFF161B24.toInt()
            dateTextPaint.color = 0xFFFF9800.toInt()
            hourHandPaint.color = 0xFFF0F4F8.toInt()
            minuteHandPaint.color = 0xFFFFFFFF.toInt()
            secondHandPaint.color = 0xFFFF5722.toInt() // Racing Orange
            centerCapPaint.color = 0xFF263238.toInt()
        }
    }

    fun updateTime(cal: Calendar) {
        val oldSec = second
        hour = cal.get(Calendar.HOUR_OF_DAY)
        minute = cal.get(Calendar.MINUTE)
        second = cal.get(Calendar.SECOND)
        is24Hour = DateFormat.is24HourFormat(context)

        val days = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val dIdx = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
        dayOfWeekStr = days[dIdx]
        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

        val months = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        monthStr = months[cal.get(Calendar.MONTH).coerceIn(0, 11)]

        if (!isAmbient) {
            startSecondSweep(oldSec, second)
        } else {
            secondFraction = 0f
            invalidate()
        }
    }

    private fun startSecondSweep(fromSec: Int, toSec: Int) {
        sweepAnimator?.cancel()
        val startVal = if (toSec == 0 && fromSec == 59) 0f else fromSec.toFloat()
        sweepAnimator = ValueAnimator.ofFloat(startVal, toSec.toFloat()).apply {
            duration = 950L
            interpolator = LinearInterpolator()
            addUpdateListener {
                secondFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setAmbientMode(ambient: Boolean) {
        if (isAmbient == ambient) return
        isAmbient = ambient
        if (ambient) {
            sweepAnimator?.cancel()
            sweepAnimator = null
        }
        setupColors()
        invalidate()
    }

    fun setAodStyle(style: String) {
        aodStyle = style
        if (isAmbient) invalidate()
    }

    fun setHeartRateTime(value: Long) {
        heartMeasuredAt = value
        if (!isAmbient) invalidate()
    }

    fun setHeartRateUnverified(value: Boolean) {
        heartRateUnverified = value
        if (!isAmbient) invalidate()
    }

    fun setHealthData(bpm: Int, stepCount: Int, goal: Int, cals: Number, dist: Number) {
        heartRate = bpm
        steps = stepCount
        if (goal > 0) stepGoal = goal
        calories = cals.toDouble()
        distanceKm = dist.toDouble()
        if (!isAmbient) invalidate()
    }

    fun setBluetoothConnected(connected: Boolean) {
        isBluetoothConnected = connected
        if (!isAmbient) invalidate()
    }

    fun setBatteryLevel(level: Int) {
        batteryLevel = level.coerceIn(0, 100)
        if (!isAmbient) invalidate()
    }

    // Compatibility methods for interactive transitions
    fun stopIlluminator() {}
    fun triggerIlluminator() {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(cx, cy)

        // 1. Dial Background
        canvas.drawCircle(cx, cy, radius, dialBgPaint)

        // 2. Bezel Chapter Ring & Ticks
        drawBezelAndTicks(canvas, cx, cy, radius)

        // 3. Subdials (Heart Rate, Steps, Battery)
        drawSubdials(canvas, cx, cy, radius)

        // 4. Date Aperture (3 o'clock)
        drawDateWindow(canvas, cx, cy, radius)

        // 5. Watch Hands (Hour, Minute, Sweeping Second)
        drawWatchHands(canvas, cx, cy, radius)
    }

    private fun drawBezelAndTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val bezelR = radius * 0.96f
        bezelRingPaint.strokeWidth = radius * 0.02f
        canvas.drawCircle(cx, cy, bezelR, bezelRingPaint)

        // Draw 60 precision tick marks
        val tickOuter = radius * 0.94f
        val tickMajorInner = radius * 0.85f
        val tickMinorInner = radius * 0.89f

        tickMajorPaint.strokeWidth = radius * 0.018f
        tickMinorPaint.strokeWidth = radius * 0.008f

        for (i in 0 until 60) {
            val angleDeg = i * 6f
            val rad = Math.toRadians(angleDeg.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()

            val isHour = i % 5 == 0
            val innerR = if (isHour) tickMajorInner else tickMinorInner
            val p = if (isHour) tickMajorPaint else tickMinorPaint

            if (isAmbient && !isHour) continue // hide minor ticks in AOD to save energy

            canvas.drawLine(
                cx + sinA * innerR,
                cy - cosA * innerR,
                cx + sinA * tickOuter,
                cy - cosA * tickOuter,
                p
            )
        }

        // Brand Text (Above center)
        if (!isAmbient) {
            textSecondaryPaint.textSize = radius * 0.08f
            textSecondaryPaint.letterSpacing = 0.2f
            canvas.drawText("CHRONO", cx, cy - radius * 0.46f, textSecondaryPaint)
            textSecondaryPaint.letterSpacing = 0f
        }
    }

    private fun drawSubdials(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val subR = radius * 0.24f

        // ── Subdial 1: Heart Rate (12 o'clock) ──
        val hrCy = cy - radius * 0.26f
        drawSubdialCircle(canvas, cx, hrCy, subR)

        val hrRatio = (heartRate.coerceIn(40, 180) - 40f) / 140f
        drawSubdialArc(canvas, cx, hrCy, subR, hrRatio, 0xFFFF3B30.toInt())

        textPrimaryPaint.textSize = subR * 0.62f
        val hrText = if (heartRate > 0) "$heartRate" else "--"
        canvas.drawText(hrText, cx, hrCy + subR * 0.18f, textPrimaryPaint)

        textSecondaryPaint.textSize = subR * 0.32f
        canvas.drawText("BPM", cx, hrCy + subR * 0.58f, textSecondaryPaint)

        // ── Subdial 2: Steps Goal (6 o'clock) ──
        val stepCy = cy + radius * 0.36f
        drawSubdialCircle(canvas, cx, stepCy, subR)

        val stepRatio = (steps.toFloat() / stepGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        drawSubdialArc(canvas, cx, stepCy, subR, stepRatio, 0xFF00E5FF.toInt())

        textPrimaryPaint.textSize = subR * 0.55f
        val stepDisplay = if (steps >= 10000) String.format(Locale.US, "%.1fK", steps / 1000f) else "$steps"
        canvas.drawText(stepDisplay, cx, stepCy + subR * 0.16f, textPrimaryPaint)

        textSecondaryPaint.textSize = subR * 0.32f
        canvas.drawText("STEPS", cx, stepCy + subR * 0.58f, textSecondaryPaint)

        // ── Subdial 3: Battery Level (9 o'clock) ──
        if (!isAmbient) {
            val batCx = cx - radius * 0.42f
            val batR = radius * 0.18f
            drawSubdialCircle(canvas, batCx, cy, batR)

            val batRatio = batteryLevel / 100f
            val batColor = if (batteryLevel > 20) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt()
            drawSubdialArc(canvas, batCx, cy, batR, batRatio, batColor)

            textPrimaryPaint.textSize = batR * 0.65f
            canvas.drawText("$batteryLevel%", batCx, cy + batR * 0.22f, textPrimaryPaint)
            textSecondaryPaint.textSize = batR * 0.38f
            canvas.drawText("BAT", batCx, cy + batR * 0.68f, textSecondaryPaint)
        }
    }

    private fun drawSubdialCircle(canvas: Canvas, x: Float, y: Float, r: Float) {
        canvas.drawCircle(x, y, r, subdialBgPaint)
        subdialRingPaint.strokeWidth = r * 0.08f
        canvas.drawCircle(x, y, r, subdialRingPaint)
    }

    private fun drawSubdialArc(canvas: Canvas, x: Float, y: Float, r: Float, progress: Float, color: Int) {
        val arcR = r * 0.88f
        subdialRect.set(x - arcR, y - arcR, x + arcR, y + arcR)
        subdialArcPaint.strokeWidth = r * 0.14f
        subdialArcPaint.color = color

        val startAngle = 135f
        val sweepAngle = 270f * progress
        canvas.drawArc(subdialRect, startAngle, sweepAngle, false, subdialArcPaint)
    }

    private fun drawDateWindow(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val dateW = radius * 0.36f
        val dateH = radius * 0.16f
        val dateX = cx + radius * 0.38f - dateW / 2f
        val dateY = cy - dateH / 2f

        dateBoxRect.set(dateX, dateY, dateX + dateW, dateY + dateH)
        val rx = dateH * 0.25f
        canvas.drawRoundRect(dateBoxRect, rx, rx, dateBoxPaint)

        dateTextPaint.textSize = dateH * 0.65f
        val textY = dateY + dateH * 0.72f
        canvas.drawText("$dayOfWeekStr $dayOfMonth", dateBoxRect.centerX(), textY, dateTextPaint)
    }

    private fun drawWatchHands(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Calculate angles
        val hourVal = (hour % 12) + (minute / 60f)
        val hourDeg = hourVal * 30f

        val minVal = minute + (second / 60f)
        val minDeg = minVal * 6f

        val secDeg = (secondFraction.takeIf { it > 0f } ?: second.toFloat()) * 6f

        // ── 1. Hour Hand ──
        val hourLen = radius * 0.52f
        val hourWidth = radius * 0.07f
        canvas.save()
        canvas.rotate(hourDeg, cx, cy)
        drawSkeletonHand(canvas, cx, cy, hourLen, hourWidth, hourHandPaint)
        canvas.restore()

        // ── 2. Minute Hand ──
        val minLen = radius * 0.74f
        val minWidth = radius * 0.055f
        canvas.save()
        canvas.rotate(minDeg, cx, cy)
        drawSkeletonHand(canvas, cx, cy, minLen, minWidth, minuteHandPaint)
        canvas.restore()

        // ── 3. Sweeping Second Hand (Hidden in AOD) ──
        if (!isAmbient) {
            val secLen = radius * 0.88f
            val tailLen = radius * 0.22f
            canvas.save()
            canvas.rotate(secDeg, cx, cy)

            secondHandPaint.strokeWidth = radius * 0.016f
            secondHandPaint.style = Paint.Style.STROKE
            // Main needle
            canvas.drawLine(cx, cy + tailLen, cx, cy - secLen, secondHandPaint)
            // Counterweight circle
            secondHandPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy + tailLen * 0.6f, radius * 0.035f, secondHandPaint)
            // Tip accent
            secondHandPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawLine(cx, cy - secLen + radius * 0.12f, cx, cy - secLen, secondHandPaint)
            secondHandPaint.color = 0xFFFF5722.toInt() // restore orange

            canvas.restore()
        }

        // ── 4. Center Cap ──
        centerCapPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius * 0.045f, centerCapPaint)
        centerCapPaint.color = if (isAmbient) 0xFF00E5FF.toInt() else 0xFFFF5722.toInt()
        canvas.drawCircle(cx, cy, radius * 0.018f, centerCapPaint)
        centerCapPaint.color = 0xFF263238.toInt()
    }

    private fun drawSkeletonHand(canvas: Canvas, cx: Float, cy: Float, length: Float, width: Float, paint: Paint) {
        val hw = width / 2f
        val tail = width * 1.2f

        handPath.reset()
        handPath.moveTo(cx - hw * 0.6f, cy + tail)
        handPath.lineTo(cx + hw * 0.6f, cy + tail)
        handPath.lineTo(cx + hw, cy - length * 0.7f)
        handPath.lineTo(cx, cy - length)
        handPath.lineTo(cx - hw, cy - length * 0.7f)
        handPath.close()

        paint.style = Paint.Style.FILL
        canvas.drawPath(handPath, paint)

        // Dark hollow center slit
        val slitPath = Path()
        val slitW = hw * 0.45f
        slitPath.moveTo(cx - slitW, cy - length * 0.18f)
        slitPath.lineTo(cx + slitW, cy - length * 0.18f)
        slitPath.lineTo(cx + slitW * 0.8f, cy - length * 0.65f)
        slitPath.lineTo(cx - slitW * 0.8f, cy - length * 0.65f)
        slitPath.close()

        val slitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isAmbient) Color.BLACK else 0xFF141922.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawPath(slitPath, slitPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchDownTime = SystemClock.elapsedRealtime()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                val dt = SystemClock.elapsedRealtime() - touchDownTime
                if (dx * dx + dy * dy < 400 && dt < 400) {
                    handleTap(event.x, event.y)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val topSubdialY = cy - radius * 0.26f
        val bottomSubdialY = cy + radius * 0.36f
        val subR = radius * 0.26f

        // Check if top subdial (Heart rate) tapped
        if ((x - cx) * (x - cx) + (y - topSubdialY) * (y - topSubdialY) < subR * subR) {
            onHistoryClick?.invoke()
            return
        }

        // Check if bottom subdial (Steps/Workout) tapped
        if ((x - cx) * (x - cx) + (y - bottomSubdialY) * (y - bottomSubdialY) < subR * subR) {
            onStartWorkoutClick?.invoke()
            return
        }

        // General dial tap
        onModeClick?.invoke()
    }
}

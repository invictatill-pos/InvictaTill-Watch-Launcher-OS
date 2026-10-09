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
import android.view.animation.AccelerateDecelerateInterpolator
import com.healthsync.watch.sensor.HeartRatePolicy
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

/**
 * Classic Casio Vintage Digital Watch Face & Always-On Display (AOD).
 *
 * Faithfully reproduces the iconic Casio F-91W / A168 / Illuminator digital LCD aesthetic:
 * - Geometric 7-segment digital LCD clock with authentic slant and unlit ghost segments
 * - Retro Casio branding, dual-stripe bezel frame, water resistance badges
 * - Day-of-week LCD matrix selector, date, and 24H indicator
 * - Real-time health metrics (Heart Rate, Steps odometer, Step goal bar, Calories, Distance)
 * - Authentic "ILLUMINATOR" electro-luminescent cyan backlight glow on tap with auto-fade
 * - Foreground dim clock with black background, minute updates, and moving digits.
 */
class CasioWatchFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Time state
    private var hour = 10
    private var minute = 58
    private var second = 30
    private var dayOfWeek = Calendar.MONDAY
    private var month = 10
    private var dayOfMonth = 6
    private var is24Hour = true

    // Health & status state
    private var heartRate = 0
    private var heartMeasuredAt = 0L
    private var heartRateUnverified = false

    fun setHeartRateTime(value: Long) {
        if (heartMeasuredAt == value) return
        heartMeasuredAt = value
        if (!isAmbient) invalidate()
    }

    fun setHeartRateUnverified(value: Boolean) {
        if (heartRateUnverified == value) return
        heartRateUnverified = value
        updateContentDescription()
        if (!isAmbient) invalidate()
    }
    private var steps = 0
    private var stepGoal = 10000
    private var activeCalories = 0
    private var activeDistanceKm = 0f
    private var isBtConnected = false
    private var batteryPct = -1

    // Display & AOD modes
    private var isAmbient = false
    private var aodStyle = "face"
    private var currentTime = Calendar.getInstance()
    private val ambientRenderer = WatchAmbientRenderer()
    private var isLowBitAmbient = false
    private var doBurnInProtect = false
    private var burnInOffsetX = 0f
    private var burnInOffsetY = 0f

    // Illuminator backlight effect
    private var illuminatorAlpha = 0f
    private var illuminatorAnimator: ValueAnimator? = null
    private var lastTapTime = 0L

    // Callbacks for interactive elements
    var onModeClick: (() -> Unit)? = null
    var onStartWorkoutClick: (() -> Unit)? = null
    var onHistoryClick: (() -> Unit)? = null

    // Touch regions
    private val rectModeButton = RectF()
    private val rectLightButton = RectF()
    private val rectStartButton = RectF()
    private val rectHistoryButton = RectF()

    // Reusable Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val lcdPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bezelLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val digitActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val digitGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Segment bitmask for digits 0-9 and blank
    // Segments: 0=A(top), 1=B(top-right), 2=C(bottom-right), 3=D(bottom), 4=E(bottom-left), 5=F(top-left), 6=G(middle)
    private val digitMasks = intArrayOf(
        0b0111111, // 0
        0b0000110, // 1
        0b1011011, // 2
        0b1001111, // 3
        0b1100110, // 4
        0b1101101, // 5
        0b1111101, // 6
        0b0000111, // 7
        0b1111111, // 8
        0b1101111  // 9
    )

    private val segmentPath = Path()
    private val tempRect = RectF()

    init {
        isClickable = true
        isFocusable = true
        updateTime(Calendar.getInstance())
    }

    fun updateTime(cal: Calendar) {
        currentTime = cal.clone() as Calendar
        hour = cal.get(Calendar.HOUR_OF_DAY)
        minute = cal.get(Calendar.MINUTE)
        second = cal.get(Calendar.SECOND)
        dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        month = cal.get(Calendar.MONTH) + 1
        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        is24Hour = DateFormat.is24HourFormat(context)
        if (isAmbient && doBurnInProtect) updateBurnInOffset(cal.timeInMillis)
        updateContentDescription()
        invalidate()
    }

    fun setHealthData(hr: Int, stepCount: Int, goal: Int, kcal: Int, distKm: Float) {
        heartRate = hr
        steps = stepCount
        stepGoal = goal.coerceAtLeast(1)
        activeCalories = kcal
        activeDistanceKm = distKm
        updateContentDescription()
        if (!isAmbient) invalidate()
    }

    fun setBluetoothConnected(connected: Boolean) {
        if (isBtConnected != connected) {
            isBtConnected = connected
            if (!isAmbient) invalidate()
        }
    }

    fun setBatteryLevel(pct: Int) {
        if (batteryPct == pct) return
        batteryPct = if (pct in 0..100) pct else -1
        if (!isAmbient) invalidate()
    }

    fun setAmbientMode(ambient: Boolean, lowBit: Boolean = false, burnInProtect: Boolean = false) {
        if (isAmbient != ambient || isLowBitAmbient != lowBit || doBurnInProtect != burnInProtect) {
            isAmbient = ambient
            isLowBitAmbient = lowBit
            doBurnInProtect = burnInProtect
            val antiAlias = !(ambient && lowBit)
            digitActivePaint.isAntiAlias = antiAlias
            digitGhostPaint.isAntiAlias = antiAlias
            textPaint.isAntiAlias = antiAlias
            ambientRenderer.setLowBitAmbient(ambient && lowBit)
            if (ambient) {
                // Cancel backlight immediately in ambient AOD mode
                illuminatorAnimator?.cancel()
                illuminatorAlpha = 0f
                if (burnInProtect) {
                    updateBurnInOffset(System.currentTimeMillis())
                } else {
                    burnInOffsetX = 0f
                    burnInOffsetY = 0f
                }
            } else {
                burnInOffsetX = 0f
                burnInOffsetY = 0f
            }
            updateContentDescription()
            invalidate()
        }
    }

    fun setAodStyle(value: String) {
        aodStyle = WatchFaceCatalog.normalizeAmbientStyle(value)
        if (isAmbient) invalidate()
    }

    private fun updateBurnInOffset(nowMillis: Long) {
        val (x, y) = WatchDisplayPolicy.burnInOffset(nowMillis)
        val density = resources.displayMetrics.density
        burnInOffsetX = x * density
        burnInOffsetY = y * density
    }

    fun stopIlluminator() {
        illuminatorAnimator?.cancel()
        illuminatorAnimator = null
        illuminatorAlpha = 0f
    }

    override fun onDetachedFromWindow() {
        stopIlluminator()
        super.onDetachedFromWindow()
    }

    fun triggerIlluminator() {
        if (isAmbient) return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        illuminatorAnimator?.cancel()
        illuminatorAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 3500L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                illuminatorAlpha = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAmbient) return false
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            val now = SystemClock.uptimeMillis()
            if (now - lastTapTime < 250) return true
            lastTapTime = now
            performClick()

            when {
                rectModeButton.contains(x, y) -> {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onModeClick?.invoke()
                    return true
                }
                rectStartButton.contains(x, y) -> {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onStartWorkoutClick?.invoke()
                    return true
                }
                rectHistoryButton.contains(x, y) -> {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onHistoryClick?.invoke()
                    return true
                }
                else -> {
                    // Tap anywhere activates Casio "ILLUMINATOR" backlight glow!
                    triggerIlluminator()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f + burnInOffsetX
        val cy = h / 2f + burnInOffsetY
        val size = min(w, h)
        val density = resources.displayMetrics.density

        // 1. Black OLED base
        canvas.drawColor(Color.BLACK)

        if (isAmbient) {
            ambientRenderer.draw(canvas, cx, cy, size / 2, currentTime, is24Hour, aodStyle, "classic")
        } else {
            drawInteractiveCasio(canvas, cx, cy, size, density, w, h)
        }
    }

    /** Minimal minute-only clock. The activity owns dimming and wake interactions. */
    private fun drawAmbientCasio(canvas: Canvas, cx: Float, cy: Float, size: Float, density: Float) {
        // Main Digits (HH:MM in AOD)
        val digitColor = Color.rgb(160, 160, 160)
        val ghostColor = Color.TRANSPARENT

        digitActivePaint.color = digitColor
        digitGhostPaint.color = ghostColor

        val digitW = size * 0.12f
        val digitH = size * 0.24f
        val colonW = size * 0.04f
        val spacing = size * 0.02f

        val totalClockW = (digitW * 4) + (spacing * 2) + colonW
        var digitX = cx - totalClockW / 2f
        val digitY = cy - digitH / 2f

        // Hour digits
        val displayedHour = if (is24Hour) hour else (hour % 12).let { if (it == 0) 12 else it }
        val hTens = if (!is24Hour && displayedHour < 10) -1 else displayedHour / 10
        val hOnes = displayedHour % 10
        draw7Segment(canvas, hTens, digitX, digitY, digitW, digitH, digitActivePaint, digitGhostPaint)
        digitX += digitW + spacing

        draw7Segment(canvas, hOnes, digitX, digitY, digitW, digitH, digitActivePaint, digitGhostPaint)
        digitX += digitW

        // Colon
        digitActivePaint.color = digitColor
        val colonR = 2.8f * density
        canvas.drawCircle(digitX + colonW / 2f, digitY + digitH * 0.32f, colonR, digitActivePaint)
        canvas.drawCircle(digitX + colonW / 2f, digitY + digitH * 0.68f, colonR, digitActivePaint)
        digitX += colonW

        // Minute digits
        val mTens = minute / 10
        val mOnes = minute % 10
        draw7Segment(canvas, mTens, digitX, digitY, digitW, digitH, digitActivePaint, digitGhostPaint)
        digitX += digitW + spacing

        draw7Segment(canvas, mOnes, digitX, digitY, digitW, digitH, digitActivePaint, digitGhostPaint)

        if (!is24Hour) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 9f * density
            textPaint.color = Color.rgb(128, 128, 128)
            canvas.drawText(if (hour < 12) "AM" else "PM", cx, digitY + digitH + 18f * density, textPaint)
        }
    }

    /**
     * Interactive Mode:
     * - Rich authentic Casio watch styling with bezel framing, color stripes, unlit segment ghosting
     * - Real-time seconds counter
     * - "ILLUMINATOR" Electro-Luminescent Cyan glow on tap
     * - Full health metrics complications
     * - Interactive touch buttons (MODE, START, HISTORY)
     */
    private fun drawInteractiveCasio(canvas: Canvas, cx: Float, cy: Float, size: Float, density: Float, w: Float, h: Float) {
        val lcdWidth = size * 0.86f
        val lcdHeight = size * 0.66f
        val lcdLeft = cx - lcdWidth / 2f
        val lcdTop = cy - lcdHeight / 2f
        val lcdRect = RectF(lcdLeft, lcdTop, lcdLeft + lcdWidth, lcdTop + lcdHeight)

        // 1. Bezel Styling (Classic Casio dual border & color accents)
        // Outer bezel ring
        bezelLinePaint.color = Color.parseColor("#1A2B23")
        bezelLinePaint.strokeWidth = 2.5f * density
        val outerBezel = RectF(lcdLeft - 10f * density, lcdTop - 26f * density,
            lcdLeft + lcdWidth + 10f * density, lcdTop + lcdHeight + 26f * density)
        canvas.drawRoundRect(outerBezel, 18f * density, 18f * density, bezelLinePaint)

        // Classic Gold / Red / Blue Casio stripe lines
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.8f * density; style = Paint.Style.STROKE }

        // Top Blue stripe
        stripePaint.color = Color.parseColor("#1B6CA8") // Classic Casio Blue
        canvas.drawLine(lcdLeft + 8f * density, lcdTop - 14f * density, lcdLeft + lcdWidth - 8f * density, lcdTop - 14f * density, stripePaint)

        // Red "WATER RESIST" accent line
        stripePaint.color = Color.parseColor("#D32F2F") // Classic Casio Red
        canvas.drawLine(lcdLeft + lcdWidth * 0.32f, lcdTop - 5f * density, lcdLeft + lcdWidth * 0.68f, lcdTop - 5f * density, stripePaint)

        // 2. Vintage Casio Badges
        brandPaint.textSize = 15f * density
        brandPaint.color = Color.parseColor("#F5F5F5")
        brandPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("CASIO", cx, lcdTop - 18f * density, brandPaint)

        // Subheader: "WATER 50M RESIST"
        textPaint.textSize = 7.5f * density
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#FF5252")
        canvas.drawText("WATER 50M RESIST", cx, lcdTop - 7f * density, textPaint)

        // Illuminator banner on bottom
        textPaint.textSize = 8.5f * density
        textPaint.color = Color.parseColor("#00E5FF")
        canvas.drawText("ILLUMINATOR", cx, lcdTop + lcdHeight + 14f * density, textPaint)

        // 3. LCD Screen Panel Background
        // Base retro LCD color: dark vintage greenish-grey
        val baseLcdColor = Color.parseColor("#0A1711")
        // Illuminator glow color: authentic Casio teal electro-luminescence
        val elGlowColor = Color.parseColor("#14E5D4")

        val currentLcdBg = if (illuminatorAlpha > 0f) {
            blendColors(baseLcdColor, elGlowColor, illuminatorAlpha * 0.88f)
        } else {
            baseLcdColor
        }
        lcdPanelPaint.color = currentLcdBg
        canvas.drawRoundRect(lcdRect, 8f * density, 8f * density, lcdPanelPaint)

        // Inner beveled LCD border
        bezelLinePaint.color = if (illuminatorAlpha > 0.4f) Color.parseColor("#40FFE8") else Color.parseColor("#264234")
        bezelLinePaint.strokeWidth = 1.5f * density
        canvas.drawRoundRect(lcdRect, 8f * density, 8f * density, bezelLinePaint)

        // 4. Color Palette for LCD Digits & Details
        val digitColor: Int
        val ghostColor: Int
        val secondaryTextColor: Int

        if (illuminatorAlpha > 0.3f) {
            // Under Illuminator backlight, active segments turn deep black-green!
            digitColor = Color.parseColor("#041E19")
            ghostColor = Color.parseColor("#258579")
            secondaryTextColor = Color.parseColor("#082B24")
        } else {
            // Normal retro LCD: crisp pale green/white digits with subtle ghost unlit segments
            digitColor = Color.parseColor("#B4F0C4")
            ghostColor = Color.parseColor("#11241A")
            secondaryTextColor = Color.parseColor("#80B892")
        }

        digitActivePaint.color = digitColor
        digitGhostPaint.color = ghostColor

        // 5. Top Status Bar: Days of week box + Date + Status indicators
        val topBarY = lcdTop + 18f * density

        // Day of week selector
        val days = arrayOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
        val currentDayIdx = (dayOfWeek - 1).coerceIn(0, 6)
        val dayX = lcdLeft + 12f * density

        textPaint.textSize = 10f * density
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = secondaryTextColor

        // Draw day of week inside classic Casio box
        val activeDayStr = days[currentDayIdx]
        val dayBoxWidth = 22f * density
        val dayBoxRect = RectF(dayX, topBarY - 10f * density, dayX + dayBoxWidth, topBarY + 3f * density)
        val dayBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = digitColor
        }
        canvas.drawRoundRect(dayBoxRect, 2f * density, 2f * density, dayBoxPaint)
        textPaint.color = digitColor
        canvas.drawText(activeDayStr, dayX + 3.5f * density, topBarY, textPaint)

        // Middle indicators: [24H] [SIG] [BT]
        textPaint.textSize = 8f * density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = secondaryTextColor
        val btIcon = if (isBtConnected) "BT✓" else "BT×"
        val clockMode = if (is24Hour) "24H" else if (hour < 12) "AM" else "PM"
        canvas.drawText("$clockMode  $btIcon", cx, topBarY - 1f * density, textPaint)

        // Date on the right (e.g. 10-06)
        textPaint.textSize = 11f * density
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = digitColor
        val dateStr = String.format(Locale.getDefault(), "%02d-%02d", month, dayOfMonth)
        canvas.drawText(dateStr, lcdLeft + lcdWidth - 12f * density, topBarY, textPaint)

        // 6. Main 7-Segment Time Display (Hours : Minutes : Seconds)
        val digitW = size * 0.135f
        val digitH = size * 0.26f
        val colonW = size * 0.038f
        val spacing = size * 0.016f

        // Small seconds digit dimensions
        val secW = digitW * 0.62f
        val secH = digitH * 0.58f

        val mainDigitsW = (digitW * 4) + (spacing * 2) + colonW
        val totalClockW = mainDigitsW + spacing + (secW * 2) + spacing

        var digitX = cx - totalClockW / 2f
        val digitY = cy - digitH * 0.46f

        // Hour Tens & Ones
        val displayedHour = if (is24Hour) hour else (hour % 12).let { if (it == 0) 12 else it }
        draw7Segment(canvas, if (!is24Hour && displayedHour < 10) -1 else displayedHour / 10,
            digitX, digitY, digitW, digitH, digitActivePaint, digitGhostPaint)
        digitX += digitW + spacing

        draw7Segment(canvas, displayedHour % 10, digitX, digitY, digitW, digitH, digitActivePaint, digitGhostPaint)
        digitX += digitW

        // Colon dots
        digitActivePaint.color = digitColor
        val colonR = 2.6f * density
        canvas.drawCircle(digitX + colonW / 2f, digitY + digitH * 0.32f, colonR, digitActivePaint)
        canvas.drawCircle(digitX + colonW / 2f, digitY + digitH * 0.68f, colonR, digitActivePaint)
        digitX += colonW

        // Minute Tens & Ones
        draw7Segment(canvas, minute / 10, digitX, digitY, digitW, digitH, digitActivePaint, digitGhostPaint)
        digitX += digitW + spacing

        draw7Segment(canvas, minute % 10, digitX, digitY, digitW, digitH, digitActivePaint, digitGhostPaint)
        digitX += digitW + spacing * 1.5f

        // Small Seconds Tens & Ones (positioned slightly aligned towards bottom)
        val secY = digitY + digitH - secH
        draw7Segment(canvas, second / 10, digitX, secY, secW, secH, digitActivePaint, digitGhostPaint)
        digitX += secW + (spacing * 0.8f)

        draw7Segment(canvas, second % 10, digitX, secY, secW, secH, digitActivePaint, digitGhostPaint)

        // 7. Middle Divider line in LCD
        val dividerY = digitY + digitH + 8f * density
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (illuminatorAlpha > 0.3f) Color.parseColor("#287569") else Color.parseColor("#1B3629")
            strokeWidth = 1f * density
        }
        canvas.drawLine(lcdLeft + 12f * density, dividerY, lcdLeft + lcdWidth - 12f * density, dividerY, dividerPaint)

        // 8. Health Complications Panel (Casio-Style Odometer & Gauges)
        val metricsY1 = dividerY + 14f * density

        // Left complication: Heart Rate
        textPaint.textSize = 10f * density
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = digitColor
        val hrDisplay = if (heartRate > 0) "♥ $heartRate ${HeartRatePolicy.complicationUnit(heartMeasuredAt, currentTime.timeInMillis)}" else "♥ -- BPM"
        canvas.drawText(hrDisplay, lcdLeft + 12f * density, metricsY1, textPaint)

        // Right complication: Steps odometer
        textPaint.textAlign = Paint.Align.RIGHT
        val stepDisplay = String.format(Locale.getDefault(), "STP %05d", steps.coerceAtMost(99999))
        canvas.drawText(stepDisplay, lcdLeft + lcdWidth - 12f * density, metricsY1, textPaint)

        // Mini 10-Segment LCD Step Goal Progress Bar
        val barY = metricsY1 + 6f * density
        val barWidth = lcdWidth - 24f * density
        val segmentW = (barWidth - 9 * (2f * density)) / 10f
        val pct = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
        val filledSegments = (pct * 10).toInt()

        val barActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = digitColor; style = Paint.Style.FILL }
        val barGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ghostColor; style = Paint.Style.FILL }

        var barSegX = lcdLeft + 12f * density
        for (i in 0 until 10) {
            val paint = if (i < filledSegments) barActivePaint else barGhostPaint
            canvas.drawRect(barSegX, barY, barSegX + segmentW, barY + 3f * density, paint)
            barSegX += segmentW + 2f * density
        }

        // Daily Calories & Distance row
        val metricsY2 = barY + 12f * density
        textPaint.textSize = 8.5f * density
        textPaint.color = secondaryTextColor
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${activeCalories}KCAL", lcdLeft + 12f * density, metricsY2, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(String.format(Locale.getDefault(), "%.1fKM", activeDistanceKm), lcdLeft + lcdWidth - 12f * density, metricsY2, textPaint)

        // 9. Corner Legend Labels (Bezel buttons)
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f * density
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.parseColor("#90B8A2")
        }

        // Top-left: LIGHT (activates Illuminator)
        legendPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("◀ LIGHT", lcdLeft - 6f * density, lcdTop - 13f * density, legendPaint)
        rectLightButton.set(0f, 0f, cx, cy * 0.4f)

        // Bottom-left: MODE (toggles watch face)
        legendPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("◀ MODE", lcdLeft - 6f * density, lcdTop + lcdHeight + 13f * density, legendPaint)
        rectModeButton.set(0f, h - 50f * density, cx, h)

        // Top-right: START (starts workout)
        legendPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("START ▶", lcdLeft + lcdWidth + 6f * density, lcdTop - 13f * density, legendPaint)
        rectStartButton.set(cx, 0f, w, cy * 0.4f)

        // Bottom-right: HISTORY (workout history)
        legendPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("HIST ▶", lcdLeft + lcdWidth + 6f * density, lcdTop + lcdHeight + 13f * density, legendPaint)
        rectHistoryButton.set(cx, h - 50f * density, w, h)
    }

    /**
     * Draws an authentic 7-segment LCD digit with mathematically beveled corners and slight italic slant.
     */
    private fun draw7Segment(
        canvas: Canvas,
        digit: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        activePaint: Paint,
        ghostPaint: Paint
    ) {
        val mask = if (digit in 0..9) digitMasks[digit] else 0
        val t = w * 0.17f // Segment thickness
        val halfT = t / 2f
        val gap = w * 0.045f // Bevel spacing between segments

        val midY = y + h / 2f
        val slantShear = -0.06f // Classic ~4-degree Casio LCD slant

        canvas.save()
        canvas.skew(slantShear, 0f)

        // Recompute x coordinate compensation for skew at vertical center
        val adjustedX = x - (y + h / 2f) * slantShear

        // Draw all 7 segments: A, B, C, D, E, F, G
        // Segment A: Top horizontal
        drawHorizontalSegment(canvas, adjustedX + gap, y, w - 2 * gap, t,
            if ((mask and (1 shl 0)) != 0) activePaint else ghostPaint)

        // Segment B: Top-right vertical
        drawVerticalSegment(canvas, adjustedX + w - t, y + gap, t, (h / 2f) - gap * 1.5f,
            if ((mask and (1 shl 1)) != 0) activePaint else ghostPaint)

        // Segment C: Bottom-right vertical
        drawVerticalSegment(canvas, adjustedX + w - t, midY + gap * 0.5f, t, (h / 2f) - gap * 1.5f,
            if ((mask and (1 shl 2)) != 0) activePaint else ghostPaint)

        // Segment D: Bottom horizontal
        drawHorizontalSegment(canvas, adjustedX + gap, y + h - t, w - 2 * gap, t,
            if ((mask and (1 shl 3)) != 0) activePaint else ghostPaint)

        // Segment E: Bottom-left vertical
        drawVerticalSegment(canvas, adjustedX, midY + gap * 0.5f, t, (h / 2f) - gap * 1.5f,
            if ((mask and (1 shl 4)) != 0) activePaint else ghostPaint)

        // Segment F: Top-left vertical
        drawVerticalSegment(canvas, adjustedX, y + gap, t, (h / 2f) - gap * 1.5f,
            if ((mask and (1 shl 5)) != 0) activePaint else ghostPaint)

        // Segment G: Middle horizontal
        drawHorizontalSegment(canvas, adjustedX + gap, midY - halfT, w - 2 * gap, t,
            if ((mask and (1 shl 6)) != 0) activePaint else ghostPaint)

        canvas.restore()
    }

    private fun drawHorizontalSegment(canvas: Canvas, x: Float, y: Float, length: Float, thickness: Float, paint: Paint) {
        if (paint.color == Color.TRANSPARENT) return
        segmentPath.reset()
        val halfT = thickness / 2f
        segmentPath.moveTo(x + halfT, y)
        segmentPath.lineTo(x + length - halfT, y)
        segmentPath.lineTo(x + length, y + halfT)
        segmentPath.lineTo(x + length - halfT, y + thickness)
        segmentPath.lineTo(x + halfT, y + thickness)
        segmentPath.lineTo(x, y + halfT)
        segmentPath.close()
        canvas.drawPath(segmentPath, paint)
    }

    private fun drawVerticalSegment(canvas: Canvas, x: Float, y: Float, thickness: Float, length: Float, paint: Paint) {
        if (paint.color == Color.TRANSPARENT) return
        segmentPath.reset()
        val halfT = thickness / 2f
        segmentPath.moveTo(x + halfT, y)
        segmentPath.lineTo(x + thickness, y + halfT)
        segmentPath.lineTo(x + thickness, y + length - halfT)
        segmentPath.lineTo(x + halfT, y + length)
        segmentPath.lineTo(x, y + length - halfT)
        segmentPath.lineTo(x, y + halfT)
        segmentPath.close()
        canvas.drawPath(segmentPath, paint)
    }

    private fun updateContentDescription() {
        val spokenTime = DateFormat.getTimeFormat(context).format(currentTime.time)
        contentDescription = if (isAmbient) "$spokenTime, always-on clock" else
            "$spokenTime, " + (if (heartRate > 0) "$heartRate beats per minute" +
                if (heartRateUnverified) ", sensor reading" else ""
             else "heart rate unavailable")
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio).toInt()
        val r = (Color.red(from) * inverseRatio + Color.red(to) * ratio).toInt()
        val g = (Color.green(from) * inverseRatio + Color.green(to) * ratio).toInt()
        val b = (Color.blue(from) * inverseRatio + Color.blue(to) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }
}

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
import com.healthsync.watch.sensor.HeartRatePolicy
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cyberpunk Neon HUD — Futuristic Sci-Fi Digital Smartwatch Face.
 *
 * Features:
 * - Holographic HUD dial with glowing battery and step arcs
 * - Animated ECG pulse wave heart-rate complication
 * - Large digital font with seconds gauge
 * - Interactive tap to cycle through 4 Cyberpunk neon color palettes:
 *   Cyber Cyan, Neon Magenta, Matrix Green, Solar Amber
 * - Ultra-low power AOD burn-in protected dim mode
 */
class NeonHudWatchFaceView @JvmOverloads constructor(
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
    private var monthStr = "OCT"
    private var is24Hour = true

    // Health & Device State
    private var heartRate = 72
    private var heartMeasuredAt = 0L
    private var heartRateUnverified = false
    private var steps = 5420
    private var stepGoal = 10000
    private var calories = 312.0
    private var distanceKm = 3.8
    private var isBluetoothConnected = true
    private var batteryLevel = 84

    // Ambient / AOD
    private var isAmbient = false
    private var aodStyle = "digital"

    // Cyberpunk Color Themes
    private val neonThemes = listOf(
        NeonTheme("CYAN", Color.parseColor("#00E5FF"), Color.parseColor("#005B66"), Color.parseColor("#00F0FF")),
        NeonTheme("MAGENTA", Color.parseColor("#FF007F"), Color.parseColor("#660033"), Color.parseColor("#FF3399")),
        NeonTheme("MATRIX", Color.parseColor("#00FF66"), Color.parseColor("#004D1A"), Color.parseColor("#33FF88")),
        NeonTheme("AMBER", Color.parseColor("#FFB300"), Color.parseColor("#664700"), Color.parseColor("#FFC107"))
    )
    private var currentThemeIndex = 0
    private val currentTheme: NeonTheme get() = neonThemes[currentThemeIndex]

    data class NeonTheme(val name: String, val primary: Int, val dim: Int, val glow: Int)

    // Paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ecgPath = Path()
    private val ecgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arcBounds = RectF()

    // Touch
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L

    fun updateTime(cal: Calendar) {
        hour = if (is24Hour) cal.get(Calendar.HOUR_OF_DAY) else {
            val h = cal.get(Calendar.HOUR)
            if (h == 0) 12 else h
        }
        minute = cal.get(Calendar.MINUTE)
        second = cal.get(Calendar.SECOND)
        is24Hour = DateFormat.is24HourFormat(context)
        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        dayOfWeekStr = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())?.uppercase() ?: "MON"
        monthStr = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())?.uppercase() ?: "OCT"
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
        if (heartRate == value) return
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
        batteryLevel = level.coerceIn(0, 100)
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
                    // Tap to cycle neon palette
                    currentThemeIndex = (currentThemeIndex + 1) % neonThemes.size
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val density = resources.displayMetrics.density

        // AMOLED Black Base
        canvas.drawColor(Color.BLACK)

        if (isAmbient) {
            drawAodMode(canvas, cx, cy, density)
            return
        }

        val theme = currentTheme
        val primaryColor = theme.primary
        val dimColor = theme.dim
        val glowColor = theme.glow

        // 1. Futuristic Outer Cyber-Ring
        val outerRadius = cx * 0.94f
        glowPaint.color = dimColor
        glowPaint.strokeWidth = 1.5f * density
        canvas.drawCircle(cx, cy, outerRadius, glowPaint)

        // 2. Battery Arc (Top-Left 135° to 225°)
        val arcRadius = cx * 0.86f
        arcBounds.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)

        arcPaint.strokeWidth = 5f * density
        arcPaint.color = dimColor
        canvas.drawArc(arcBounds, 140f, 80f, false, arcPaint)

        val batFrac = (batteryLevel / 100f).coerceIn(0f, 1f)
        arcPaint.color = if (batteryLevel > 20) primaryColor else Color.parseColor("#EF4444")
        canvas.drawArc(arcBounds, 140f, 80f * batFrac, false, arcPaint)

        // Battery Label
        textPaint.textSize = 9f * density
        textPaint.color = primaryColor
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("BAT $batteryLevel%", cx - arcRadius * 0.92f, cy - arcRadius * 0.35f, textPaint)

        // 3. Step Goal Arc (Top-Right 315° to 45°)
        arcPaint.color = dimColor
        canvas.drawArc(arcBounds, -40f, 80f, false, arcPaint)

        val stepFrac = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
        arcPaint.color = primaryColor
        canvas.drawArc(arcBounds, -40f, 80f * stepFrac, false, arcPaint)

        // Steps Label
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(String.format(Locale.getDefault(), "STP %05d", steps), cx + arcRadius * 0.92f, cy - arcRadius * 0.35f, textPaint)

        // 4. Central Cyber Grid Header
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10f * density
        textPaint.color = dimColor
        canvas.drawText("◆ SYSTEM HUD [${theme.name}] ◆", cx, cy - 65f * density, textPaint)

        textPaint.textSize = 11f * density
        textPaint.color = primaryColor
        canvas.drawText("$dayOfWeekStr  $dayOfMonth $monthStr", cx, cy - 48f * density, textPaint)

        // 5. Hero Digital Time (HH:mm)
        textPaint.textSize = 52f * density
        textPaint.color = Color.WHITE
        textPaint.setShadowLayer(12f * density, 0f, 0f, primaryColor)
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        canvas.drawText(timeStr, cx, cy + 12f * density, textPaint)
        textPaint.clearShadowLayer()

        // Seconds Gauge
        textPaint.textSize = 13f * density
        textPaint.color = primaryColor
        canvas.drawText(String.format(Locale.getDefault(), ":%02d SEC", second), cx, cy + 30f * density, textPaint)

        // 6. ECG Heart Rate Pulse Wave
        val ecgY = cy + 54f * density
        val ecgW = cx * 0.75f
        ecgPath.reset()
        ecgPath.moveTo(cx - ecgW, ecgY)
        ecgPath.lineTo(cx - ecgW * 0.45f, ecgY)
        ecgPath.lineTo(cx - ecgW * 0.35f, ecgY - 6f * density)
        ecgPath.lineTo(cx - ecgW * 0.25f, ecgY + 8f * density)
        ecgPath.lineTo(cx - ecgW * 0.15f, ecgY - 14f * density) // QRS peak
        ecgPath.lineTo(cx - ecgW * 0.05f, ecgY + 12f * density)
        ecgPath.lineTo(cx + ecgW * 0.05f, ecgY)
        ecgPath.lineTo(cx + ecgW, ecgY)

        ecgPaint.color = if (heartRate > 0) Color.parseColor("#FF0055") else dimColor
        ecgPaint.strokeWidth = 2f * density
        canvas.drawPath(ecgPath, ecgPaint)

        // Heart Rate Readout
        textPaint.textSize = 11f * density
        textPaint.color = if (heartRate > 0) Color.parseColor("#FF3366") else dimColor
        val hrText = if (heartRate > 0) "♥ $heartRate BPM" else "♥ -- BPM"
        canvas.drawText(hrText, cx, cy + 74f * density, textPaint)

        // 7. Bottom Health Metric Strip (Calories & Distance)
        textPaint.textSize = 9.5f * density
        textPaint.color = primaryColor
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.getDefault(), "%.0f KCAL  |  %.1f KM", calories, distanceKm), cx, cy + 92f * density, textPaint)
    }

    private fun drawAodMode(canvas: Canvas, cx: Float, cy: Float, density: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#94A3B8")
        textPaint.textSize = 40f * density
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        canvas.drawText(timeStr, cx, cy + 8f * density, textPaint)

        textPaint.textSize = 11f * density
        textPaint.color = Color.parseColor("#475569")
        canvas.drawText("$dayOfWeekStr $dayOfMonth · BAT $batteryLevel%", cx, cy + 28f * density, textPaint)
    }
}

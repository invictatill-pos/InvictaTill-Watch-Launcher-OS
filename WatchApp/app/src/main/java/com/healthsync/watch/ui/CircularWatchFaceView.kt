package com.healthsync.watch.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import com.healthsync.watch.sensor.HeartRatePolicy
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A circle-first health face for standard Android watches, including Android 8.
 * The interactive face leaves the bottom chord free for native exercise/menu buttons.
 * Ambient renders only a sparse clock on black; it has no animator or second hand.
 */
class CircularWatchFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentTime = Calendar.getInstance()
    private var is24Hour = DateFormat.is24HourFormat(context)
    private var ambient = false
    private var aodStyle = "face"
    private val ambientRenderer = WatchAmbientRenderer()
    private var wallpaperBackdrop = false
    private var lowBitAmbient = false
    private var burnInProtection = true
    private var heartRate = 0
    private var heartMeasuredAt = 0L
    private var heartRateUnverified = false

    fun setHeartRateTime(value: Long) {
        if (heartMeasuredAt == value) return
        heartMeasuredAt = value
        if (!ambient) invalidate()
    }

    fun setHeartRateUnverified(value: Boolean) {
        if (heartRateUnverified == value) return
        heartRateUnverified = value
        updateContentDescription()
        if (!ambient) invalidate()
    }
    private var steps = 0
    private var stepGoal = 10_000
    private var calories = 0
    private var distanceKm = 0f
    private var bluetoothConnected = false
    private var batteryLevel = -1

    private val mint = Color.rgb(115, 229, 193)
    private val muted = Color.rgb(142, 174, 167)
    private val white = Color.rgb(244, 255, 249)
    private val heart = Color.rgb(255, 140, 160)
    private val ambientWhite = Color.rgb(131, 143, 139)
    private val ambientDim = Color.rgb(77, 89, 85)
    private val normalTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val boldTypeface = Typeface.create("sans-serif", Typeface.BOLD)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = normalTypeface
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcBounds = RectF()
    private val handPath = Path()
    private val symbolPath = Path()
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

    init {
        setBackgroundColor(Color.BLACK)
        updateContentDescription()
    }

    fun updateTime(calendar: Calendar) {
        val previousMinute = currentTime.timeInMillis / 60_000L
        val next24Hour = DateFormat.is24HourFormat(context)
        val changed = previousMinute != calendar.timeInMillis / 60_000L ||
            is24Hour != next24Hour || currentTime.timeZone.id != calendar.timeZone.id
        currentTime = calendar.clone() as Calendar
        is24Hour = next24Hour
        // Sensor callbacks and second ticks must not wake a dim display repeatedly.
        if (changed) {
            updateContentDescription()
            invalidate()
        }
    }

    fun setHealthData(hr: Int, stepCount: Int, goal: Int, kcal: Int, distKm: Float) {
        val nextHeartRate = hr.coerceAtLeast(0)
        val nextSteps = stepCount.coerceAtLeast(0)
        val nextGoal = goal.coerceAtLeast(1)
        val nextCalories = kcal.coerceAtLeast(0)
        val nextDistance = if (distKm.isFinite()) distKm.coerceAtLeast(0f) else 0f
        if (heartRate == nextHeartRate && steps == nextSteps && stepGoal == nextGoal &&
            calories == nextCalories && distanceKm == nextDistance) return
        heartRate = nextHeartRate
        steps = nextSteps
        stepGoal = nextGoal
        calories = nextCalories
        distanceKm = nextDistance
        updateContentDescription()
        if (!ambient) invalidate()
    }

    fun setBluetoothConnected(connected: Boolean) {
        if (bluetoothConnected == connected) return
        bluetoothConnected = connected
        updateContentDescription()
        if (!ambient) invalidate()
    }

    fun setBatteryLevel(pct: Int) {
        val next = pct.takeIf { it in 0..100 } ?: -1
        if (batteryLevel == next) return
        batteryLevel = next
        updateContentDescription()
        if (!ambient) invalidate()
    }

    fun setAmbientMode(ambient: Boolean, lowBit: Boolean = false, burnInProtect: Boolean = true) {
        if (this.ambient == ambient && lowBitAmbient == lowBit && burnInProtection == burnInProtect) return
        this.ambient = ambient
        lowBitAmbient = lowBit
        burnInProtection = burnInProtect
        val antiAlias = !(ambient && lowBit)
        linePaint.isAntiAlias = antiAlias
        textPaint.isAntiAlias = antiAlias
        fillPaint.isAntiAlias = antiAlias
        ambientRenderer.setLowBitAmbient(ambient && lowBit)
        updateContentDescription()
        invalidate()
    }

    fun setAodStyle(value: String) {
        aodStyle = WatchFaceCatalog.normalizeAmbientStyle(value)
        if (ambient) invalidate()
    }

    fun setWallpaperBackdrop(enabled: Boolean) {
        if (wallpaperBackdrop != enabled) { wallpaperBackdrop = enabled; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(if (ambient || !wallpaperBackdrop) Color.BLACK else 0x99000000.toInt())
        val radius = CircularWatchFaceGeometry.radius(width.toFloat(), height.toFloat())
        if (radius <= 0f) return
        var cx = width / 2f
        var cy = height / 2f
        if (ambient && burnInProtection) {
            val (dx, dy) = WatchDisplayPolicy.burnInOffset(currentTime.timeInMillis)
            cx += dx * resources.displayMetrics.density
            cy += dy * resources.displayMetrics.density
        }
        if (ambient) ambientRenderer.draw(canvas, cx, cy, radius, currentTime, is24Hour, aodStyle, "circular")
        else drawInteractive(canvas, cx, cy, radius)
    }

    private fun drawInteractive(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // The gap at six o'clock provides room for the native exercise/menu actions.
        val ringRadius = r * 0.924f
        linePaint.strokeWidth = r * 0.038f
        linePaint.color = Color.rgb(19, 49, 40)
        arcBounds.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        canvas.drawArc(arcBounds, 130f, 280f, false, linePaint)
        val progress = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
        linePaint.color = mint
        if (progress > 0f) canvas.drawArc(arcBounds, 130f, 280f * progress, false, linePaint)

        for (index in 0 until 12) {
            val degrees = index * 30f - 90f
            if (degrees in 60f..120f) continue
            val angle = Math.toRadians(degrees.toDouble())
            val outer = r * 0.846f
            val inner = r * if (index % 3 == 0) 0.786f else 0.818f
            linePaint.color = if (index % 3 == 0) muted else Color.rgb(45, 70, 61)
            linePaint.strokeWidth = r * if (index % 3 == 0) 0.012f else 0.007f
            canvas.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, linePaint)
        }

        drawStatus(canvas, cx, cy - r * 0.695f, r)
        val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(currentTime.time) +
            if (is24Hour) "" else " · " + SimpleDateFormat("a", Locale.getDefault()).format(currentTime.time)
        drawText(canvas, date, cx, cy - r * 0.521f, r * 0.096f, muted, r, cy)
        drawText(canvas, timeText(), cx, cy - r * 0.235f, r * 0.375f, white, r, cy,
            bold = true, maxWidth = r * 1.58f)

        drawText(canvas, if (heartRate > 0) heartRate.toString() else "--",
            cx - r * 0.36f, cy + r * 0.095f, r * 0.175f, heart, r, cy,
            bold = true, maxWidth = r * 0.68f)
        drawText(canvas, numberFormat.format(steps), cx + r * 0.36f, cy + r * 0.095f,
            r * 0.175f, mint, r, cy, bold = true, maxWidth = r * 0.68f)
        drawText(canvas, if (heartRate > 0) HeartRatePolicy.complicationUnit(heartMeasuredAt, currentTime.timeInMillis) else "BPM", cx - r * 0.36f, cy + r * 0.235f, r * 0.075f, muted, r, cy,
            maxWidth = r * 0.68f)
        drawText(canvas, "STEPS", cx + r * 0.36f, cy + r * 0.235f, r * 0.075f, muted, r, cy,
            maxWidth = r * 0.68f)
    }

    private fun drawStatus(canvas: Canvas, cx: Float, y: Float, r: Float) {
        linePaint.strokeWidth = r * 0.012f
        linePaint.color = if (bluetoothConnected) mint else muted
        val x = cx - r * 0.45f
        val size = r * 0.093f
        symbolPath.reset()
        symbolPath.moveTo(x, y - size / 2)
        symbolPath.lineTo(x, y + size / 2)
        symbolPath.lineTo(x + size * 0.37f, y + size * 0.24f)
        symbolPath.lineTo(x - size * 0.27f, y - size * 0.29f)
        symbolPath.moveTo(x, y - size / 2)
        symbolPath.lineTo(x + size * 0.37f, y - size * 0.24f)
        symbolPath.lineTo(x - size * 0.27f, y + size * 0.29f)
        canvas.drawPath(symbolPath, linePaint)
        drawText(canvas, if (bluetoothConnected) "LINKED" else "OFFLINE", cx - r * 0.19f,
            y, r * 0.065f, if (bluetoothConnected) mint else muted, r, height / 2f,
            maxWidth = r * 0.4f)

        linePaint.color = if (batteryLevel in 0..15) heart else muted
        linePaint.strokeWidth = r * 0.009f
        val batteryX = cx + r * 0.195f
        arcBounds.set(batteryX, y - r * 0.033f, batteryX + r * 0.12f, y + r * 0.033f)
        canvas.drawRoundRect(arcBounds, r * 0.009f, r * 0.009f, linePaint)
        canvas.drawLine(batteryX + r * 0.13f, y - r * 0.013f,
            batteryX + r * 0.13f, y + r * 0.013f, linePaint)
        if (batteryLevel >= 0) {
            fillPaint.color = linePaint.color
            val fillWidth = r * 0.093f * batteryLevel / 100f
            canvas.drawRect(batteryX + r * 0.014f, y - r * 0.020f,
                batteryX + r * 0.014f + fillWidth, y + r * 0.020f, fillPaint)
        }
        drawText(canvas, if (batteryLevel >= 0) "$batteryLevel%" else "--%",
            cx + r * 0.462f, y, r * 0.072f, linePaint.color, r, height / 2f,
            maxWidth = r * 0.26f)
    }

    private fun drawAmbient(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        linePaint.color = ambientDim
        linePaint.strokeWidth = max(1f, r * 0.006f)
        for (index in 0 until 12) {
            val angle = Math.toRadians(index * 30.0 - 90.0)
            val outer = r * 0.808f
            val inner = r * if (index % 3 == 0) 0.750f else 0.783f
            linePaint.color = if (index % 3 == 0) ambientWhite else ambientDim
            canvas.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, linePaint)
        }

        drawText(canvas, "12", cx, cy - r * 0.676f, r * 0.087f, ambientDim, r, cy)
        drawText(canvas, "3", cx + r * 0.686f, cy, r * 0.087f, ambientDim, r, cy)
        drawText(canvas, "6", cx, cy + r * 0.680f, r * 0.087f, ambientDim, r, cy)
        drawText(canvas, "9", cx - r * 0.686f, cy, r * 0.087f, ambientDim, r, cy)

        // Ambient hand positions use minutes only, so no pixels change every second.
        val minute = currentTime.get(Calendar.MINUTE)
        val hour = currentTime.get(Calendar.HOUR)
        drawHand(canvas, cx, cy, r, (hour + minute / 60f) * 30f - 90f, 0.47f, 0.039f)
        drawHand(canvas, cx, cy, r, minute * 6f - 90f, 0.675f, 0.027f)
        linePaint.color = ambientWhite
        canvas.drawCircle(cx, cy, r * 0.025f, linePaint)

        // A black backing keeps the hands from crossing the small digital complication.
        fillPaint.color = Color.BLACK
        arcBounds.set(cx - r * 0.405f, cy + r * 0.332f, cx + r * 0.405f, cy + r * 0.507f)
        canvas.drawRect(arcBounds, fillPaint)
        drawText(canvas, timeText(), cx, cy + r * 0.416f, r * 0.23f, ambientWhite, r, cy,
            maxWidth = r * 0.78f)
        if (!is24Hour) {
            drawText(canvas, SimpleDateFormat("a", Locale.getDefault()).format(currentTime.time),
                cx, cy + r * 0.556f, r * 0.070f, ambientDim, r, cy)
        }
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, r: Float,
                         degrees: Float, length: Float, halfWidth: Float) {
        val saved = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(degrees)
        handPath.reset()
        handPath.moveTo(r * 0.07f, -r * halfWidth)
        handPath.lineTo(r * (length - 0.055f), -r * halfWidth)
        handPath.lineTo(r * length, 0f)
        handPath.lineTo(r * (length - 0.055f), r * halfWidth)
        handPath.lineTo(r * 0.07f, r * halfWidth)
        handPath.close()
        linePaint.color = ambientWhite
        linePaint.strokeWidth = max(1f, r * 0.009f)
        canvas.drawPath(handPath, linePaint)
        canvas.restoreToCount(saved)
    }

    /** Center each text row vertically and shrink it to the safe chord, including its whole font band. */
    private fun drawText(canvas: Canvas, text: String, x: Float, centerY: Float, size: Float,
                         color: Int, r: Float, cy: Float, bold: Boolean = false,
                         maxWidth: Float = Float.MAX_VALUE) {
        textPaint.color = color
        textPaint.typeface = if (bold) boldTypeface else normalTypeface
        textPaint.textSize = size
        val metrics = textPaint.fontMetrics
        val halfHeight = (metrics.descent - metrics.ascent) / 2f
        val halfChord = CircularWatchFaceGeometry.halfWidthForBand(r,
            centerY - cy - halfHeight, centerY - cy + halfHeight, r * 0.075f)
        val xFromCenter = x - width / 2f
        val chordAvailable = max(0f, (halfChord - kotlin.math.abs(xFromCenter)) * 2f)
        val available = min(maxWidth, chordAvailable)
        val measured = textPaint.measureText(text)
        if (measured > available && measured > 0f) textPaint.textSize *= available / measured
        val baseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, x, baseline, textPaint)
    }

    private fun timeText(): String =
        SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault()).format(currentTime.time)

    private fun updateContentDescription() {
        val spokenTime = DateFormat.getTimeFormat(context).format(currentTime.time)
        contentDescription = if (ambient) "$spokenTime, always-on clock" else
            "$spokenTime, ${numberFormat.format(steps)} steps of ${numberFormat.format(stepGoal)}, " +
                (if (heartRate > 0) "$heartRate beats per minute" +
                    if (heartRateUnverified) ", sensor reading" else ""
                 else "heart rate unavailable") +
                ", ${numberFormat.format(calories)} estimated kilocalories, " +
                "${String.format(Locale.getDefault(), "%.1f", distanceKm)} kilometers" +
                (if (batteryLevel >= 0) ", battery $batteryLevel percent" else "") +
                (if (bluetoothConnected) ", phone connected" else ", phone offline")
    }
}

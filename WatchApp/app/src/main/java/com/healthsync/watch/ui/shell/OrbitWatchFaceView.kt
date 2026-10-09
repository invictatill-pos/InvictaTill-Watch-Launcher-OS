package com.healthsync.watch.ui.shell

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.healthsync.watch.ui.WatchDisplayPolicy
import com.healthsync.watch.ui.WatchFaceCatalog
import com.healthsync.watch.ui.WatchAmbientRenderer
import com.healthsync.watch.ui.CircularWatchFaceGeometry
import com.healthsync.watch.sensor.HeartRatePolicy
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs

/** Lightweight Orbit watch face with mint goal arc, live complications and AOD burn-in protection. */
class OrbitWatchFaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var onFitness: () -> Unit = {}
    var onNotifications: () -> Unit = {}
    var onControls: () -> Unit = {}
    var onWorkout: () -> Unit = {}
    var onTimer: () -> Unit = {}
    private var now = Calendar.getInstance()
    private var style = "orbit"
    private var ambient = false
    private var aodStyle = "face"
    private val ambientRenderer = WatchAmbientRenderer()
    private var wallpaper = false
    private var steps = 0
    private var goal = 10_000
    private var heart = -1
    private var heartMeasuredAt = 0L
    private var heartUnverified = false
    private var battery = -1
    private var linked = false
    private var unread = 0
    private var workout = false
    private var timer: String? = null
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = RectF()
    private val mint = 0xFFB8F7D4.toInt()
    private val white = 0xFFF2F8F5.toInt()
    private val muted = 0xFF99ADA3.toInt()
    private val numberFormat = NumberFormat.getIntegerInstance()
    private val normalFont = Typeface.create("sans-serif", Typeface.NORMAL)
    private val mediumFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val clockFont = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    init {
        isClickable = true
        isFocusable = true
    }

    fun updateTime(time: Calendar) {
        now = time.clone() as Calendar
        invalidate()
    }

    fun setStyle(value: String) {
        style = "orbit"
        invalidate()
    }

    fun setAodStyle(value: String) {
        aodStyle = WatchFaceCatalog.normalizeAmbientStyle(value)
        if (ambient) invalidate()
    }

    fun setAmbient(value: Boolean) {
        ambient = value
        invalidate()
    }

    fun setWallpaper(value: Boolean) {
        wallpaper = value
        invalidate()
    }

    fun setTimer(value: String?) {
        if (timer != value) {
            timer = value
            if (!ambient) invalidate()
        }
    }

    fun setHeartRateTime(value: Long) {
        heartMeasuredAt = value
    }

    fun setHeartRateUnverified(value: Boolean) {
        if (heartUnverified == value) return
        heartUnverified = value
        updateContentDescription()
        if (!ambient) invalidate()
    }

    fun setData(steps: Int, goal: Int, heart: Int, battery: Int, linked: Boolean, unread: Int, workout: Boolean) {
        this.steps = steps.coerceAtLeast(0)
        this.goal = goal.coerceAtLeast(1)
        this.heart = heart
        this.battery = battery
        this.linked = linked
        this.unread = unread
        this.workout = workout
        updateContentDescription()
        if (!ambient) invalidate()
    }

    private fun updateContentDescription() {
        contentDescription = "${DateFormat.getTimeFormat(context).format(now.time)}. $steps steps. " +
            (when {
                heart <= 0 -> "Heart rate unavailable. "
                heartUnverified -> "$heart beats per minute, sensor reading. "
                else -> "$heart beats per minute. "
            }) +
            (if (heart > 0) "${HeartRatePolicy.ageLabel(heartMeasuredAt, now.timeInMillis)}. " else "") +
            "$unread unread notifications. Tap complications for details."
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(if (wallpaper && !ambient) 0xBB000000.toInt() else Color.BLACK)
        val r = min(width, height) * 0.5f
        if (r <= 0f) return
        var cx = width * 0.5f
        var cy = height * 0.5f
        if (ambient) {
            val (dx, dy) = WatchDisplayPolicy.burnInOffset(now.timeInMillis)
            cx += dx * resources.displayMetrics.density
            cy += dy * resources.displayMetrics.density
            ambientRenderer.draw(canvas, cx, cy, r, now, DateFormat.is24HourFormat(context), aodStyle, style)
            return
        }

        // 1. Goal progress arc
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = r * 0.032f
        ink.strokeCap = Paint.Cap.ROUND
        ring.set(cx - r * 0.91f, cy - r * 0.91f, cx + r * 0.91f, cy + r * 0.91f)
        ink.color = 0xFF17271F.toInt()
        canvas.drawArc(ring, 134f, 272f, false, ink)
        ink.color = mint
        canvas.drawArc(ring, 134f, 272f * (steps.toFloat() / goal).coerceIn(0f, 1f), false, ink)
        ink.style = Paint.Style.FILL

        // 2. Status line: BT link, Battery, unread, AM/PM
        val status = (if (linked) "●" else "○") + "   " + (if (battery in 0..100) "$battery%" else "—%") +
            (if (unread > 0) "   • $unread" else "") +
            (if (DateFormat.is24HourFormat(context)) "" else "   " + SimpleDateFormat("a", Locale.getDefault()).format(now.time))
        text(canvas, status, cx, cy - r * 0.76f, r * 0.083f, muted)

        // 3. Date & Digital Time
        text(canvas, date(), cx, cy - r * 0.40f, r * 0.095f, muted)
        text(canvas, time(), cx, cy - r * 0.07f, r * 0.46f, white, clockFont)

        // 4. Complications: Heart rate pill (left) & Steps pill (right)
        pill(canvas, cx - r * 0.34f, cy + r * 0.29f, r * 0.56f, r * 0.26f,
            if (heart > 0) "$heart" else "—", heartUnit(), r)
        pill(canvas, cx + r * 0.34f, cy + r * 0.29f, r * 0.56f, r * 0.26f,
            compactSteps(), "STEPS", r)

        // 5. Active Glance line (Timer or Workout status)
        val glance = timer ?: if (workout) "WORKOUT ACTIVE" else null
        if (glance != null) {
            text(canvas, glance, cx, cy + r * 0.53f, r * 0.062f, mint)
        }
    }

    private fun heartUnit() = if (heart > 0) HeartRatePolicy.complicationUnit(heartMeasuredAt, now.timeInMillis) else "BPM"

    private fun pill(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, value: String, label: String, r: Float) {
        ink.style = Paint.Style.FILL
        ink.color = 0xFF111E17.toInt()
        ring.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        canvas.drawRoundRect(ring, h / 2, h / 2, ink)
        text(canvas, value, x, y - r * 0.018f, r * 0.115f, mint, mediumFont)
        text(canvas, label, x, y + r * 0.082f, r * 0.055f, muted)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, font: Typeface = normalFont) {
        ink.style = Paint.Style.FILL
        ink.color = color
        ink.textSize = size
        ink.typeface = font
        ink.textAlign = Paint.Align.CENTER
        val r = min(width, height) / 2f
        val halfHeight = (ink.descent() - ink.ascent()) / 2f
        val halfChord = CircularWatchFaceGeometry.halfWidthForBand(r, y - height / 2f - halfHeight, y - height / 2f + halfHeight, r * 0.075f)
        val available = max(0f, (halfChord - abs(x - width / 2f)) * 2)
        val measured = ink.measureText(value)
        if (measured > available && measured > 0) ink.textSize *= available / measured
        canvas.drawText(value, x, y - (ink.ascent() + ink.descent()) / 2, ink)
    }

    private fun time() = SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.getDefault()).format(now.time)
    private fun date() = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now.time).uppercase(Locale.getDefault())
    private fun compactSteps() = if (steps >= 10_000) String.format(Locale.getDefault(), "%.1fk", steps / 1000f) else numberFormat.format(steps)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (ambient) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            val cy = height / 2f
            val r = min(width, height) / 2f
            when {
                event.y < cy - r * 0.54f -> if (event.x > width * 0.62f && unread > 0) onNotifications() else onControls()
                timer != null && event.y > cy + r * 0.46f -> onTimer()
                workout && event.y > cy + r * 0.46f -> onWorkout()
                event.y > cy + r * 0.16f && event.y < cy + r * 0.51f -> onFitness()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

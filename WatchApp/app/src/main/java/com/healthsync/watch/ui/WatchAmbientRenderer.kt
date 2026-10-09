package com.healthsync.watch.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.min

/** No timers, sensor subscriptions or animation: the activity supplies minute-only ambient ticks. */
internal interface AmbientFaceRenderer {
    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: Calendar,
             is24Hour: Boolean, style: String, face: String)
}

internal class WatchAmbientRenderer : AmbientFaceRenderer {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val clock = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val lit = Color.rgb(139, 151, 145)
    private val dim = Color.rgb(78, 88, 82)
    private var safeRadius = 0f
    private var originX = 0f
    private var originY = 0f
    fun setLowBitAmbient(enabled: Boolean) { ink.isAntiAlias = !enabled }

    override fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: Calendar,
                      is24Hour: Boolean, style: String, face: String) {
        val selected = WatchFaceCatalog.normalizeAmbientStyle(style).let {
            if (it == "face") WatchFaceCatalog.ambientStyleForFace(face) else it
        }
        val textTime = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault()).format(time.time)
        val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(time.time)
        val r = radius * .86f // Leaves space for the bounded burn-in shift on small round screens.
        safeRadius = r; originX = cx; originY = cy
        when (selected) {
            "analog" -> {
                for (i in 0 until 12) line(canvas, cx, cy, i * 30.0, r * .78f, r * .85f, r * .011f, dim)
                val minute = time.get(Calendar.MINUTE)
                line(canvas, cx, cy, time.get(Calendar.HOUR) * 30.0 + minute * .5, 0f, r * .48f, r * .030f, lit)
                line(canvas, cx, cy, minute * 6.0, 0f, r * .68f, r * .022f, lit)
                ink.style = Paint.Style.FILL; ink.color = lit
                canvas.drawCircle(cx, cy, r * .023f, ink)
                text(canvas, date, cx, cy + r * .38f, r * .085f, dim)
            }
            "outline" -> {
                text(canvas, textTime, cx, cy, r * .39f, lit, outlined = true)
                text(canvas, date, cx, cy + r * .29f, r * .085f, dim)
            }
            "stacked" -> {
                val hour = if (is24Hour) time.get(Calendar.HOUR_OF_DAY) else time.get(Calendar.HOUR).let { if (it == 0) 12 else it }
                text(canvas, String.format(Locale.getDefault(), "%02d", hour), cx, cy - r * .24f, r * .43f, lit)
                text(canvas, String.format(Locale.getDefault(), "%02d", time.get(Calendar.MINUTE)), cx, cy + r * .22f, r * .43f, lit)
            }
            "retro" -> {
                val hour = if (is24Hour) time.get(Calendar.HOUR_OF_DAY) else time.get(Calendar.HOUR).let { if (it == 0) 12 else it }
                SevenSegmentClock.draw(canvas, ink, cx, cy, r * .20f, r * .43f, hour, time.get(Calendar.MINUTE), lit, Color.TRANSPARENT)
                text(canvas, date, cx, cy + r * .37f, r * .085f, dim)
            }
            "rings" -> {
                ink.style = Paint.Style.STROKE; ink.strokeWidth = r * .007f; ink.color = dim
                canvas.drawCircle(cx, cy, r * .69f, ink)
                for (i in 0 until 4) line(canvas, cx, cy, i * 90.0, r * .71f, r * .78f, r * .013f, lit)
                text(canvas, textTime, cx, cy, r * .29f, lit)
                text(canvas, date, cx, cy + r * .25f, r * .078f, dim)
            }
            "date" -> {
                text(canvas, textTime, cx, cy - r * .25f, r * .33f, lit)
                text(canvas, SimpleDateFormat("EEEE", Locale.getDefault()).format(time.time), cx, cy + r * .05f, r * .13f, dim)
                text(canvas, SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(time.time), cx, cy + r * .27f, r * .105f, lit)
            }
            else -> {
                text(canvas, textTime, cx, cy, r * .39f, lit)
                text(canvas, date, cx, cy + r * .29f, r * .085f, dim)
            }
        }
        if (!is24Hour && selected != "date") {
            text(canvas, SimpleDateFormat("a", Locale.getDefault()).format(time.time), cx, cy + r * .58f, r * .065f, dim)
        }
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, outlined: Boolean = false) {
        ink.style = if (outlined) Paint.Style.STROKE else Paint.Style.FILL
        ink.strokeWidth = size * .025f; ink.color = color; ink.textSize = size
        ink.typeface = if (size > 30f) clock else normal; ink.textAlign = Paint.Align.CENTER
        // This renderer only draws centered text, confined to the inner safe dial.
        val maxWidth = size * if (size > 30f) 4.3f else 15f
        val halfHeight = (ink.descent() - ink.ascent()) / 2
        val halfChord = CircularWatchFaceGeometry.halfWidthForBand(safeRadius,
            y - originY - halfHeight, y - originY + halfHeight, safeRadius * .025f)
        val available = min(maxWidth, ((halfChord - abs(x - originX)) * 2).coerceAtLeast(0f))
        val measured = ink.measureText(value)
        if (measured > available && measured > 0f) ink.textSize *= available / measured
        canvas.drawText(value, x, y - (ink.ascent() + ink.descent()) / 2, ink)
    }

    private fun line(canvas: Canvas, cx: Float, cy: Float, degrees: Double, start: Float, end: Float, width: Float, color: Int) {
        val a = Math.toRadians(degrees - 90)
        ink.style = Paint.Style.STROKE; ink.strokeWidth = width; ink.strokeCap = Paint.Cap.ROUND; ink.color = color
        canvas.drawLine(cx + cos(a).toFloat() * start, cy + sin(a).toFloat() * start,
            cx + cos(a).toFloat() * end, cy + sin(a).toFloat() * end, ink)
    }
}

/** Shared geometric LCD digits, with optional ghost segments only in the interactive display. */
internal object SevenSegmentClock {
    private val masks = intArrayOf(63, 6, 91, 79, 102, 109, 125, 7, 127, 111)
    fun draw(canvas: Canvas, paint: Paint, cx: Float, cy: Float, digitWidth: Float, digitHeight: Float,
             hour: Int, minute: Int, active: Int, ghost: Int) {
        val gap = digitWidth * .15f
        val colon = digitWidth * .30f
        val width = digitWidth * 4 + gap * 2 + colon
        var x = cx - width / 2
        val y = cy - digitHeight / 2
        digit(canvas, paint, hour / 10, x, y, digitWidth, digitHeight, active, ghost); x += digitWidth + gap
        digit(canvas, paint, hour % 10, x, y, digitWidth, digitHeight, active, ghost); x += digitWidth
        paint.style = Paint.Style.FILL; paint.color = active
        canvas.drawCircle(x + colon / 2, y + digitHeight * .31f, digitWidth * .055f, paint)
        canvas.drawCircle(x + colon / 2, y + digitHeight * .69f, digitWidth * .055f, paint)
        x += colon
        digit(canvas, paint, minute / 10, x, y, digitWidth, digitHeight, active, ghost); x += digitWidth + gap
        digit(canvas, paint, minute % 10, x, y, digitWidth, digitHeight, active, ghost)
    }

    private fun digit(canvas: Canvas, paint: Paint, digit: Int, x: Float, y: Float, w: Float, h: Float, active: Int, ghost: Int) {
        val t = w * .15f; val inset = t * .7f; val mask = masks[digit.coerceIn(0, 9)]
        paint.style = Paint.Style.FILL
        fun segment(index: Int, left: Float, top: Float, right: Float, bottom: Float) {
            paint.color = if (mask and (1 shl index) != 0) active else ghost
            if (paint.color != Color.TRANSPARENT) canvas.drawRect(left, top, right, bottom, paint)
        }
        segment(0, x + inset, y, x + w - inset, y + t)
        segment(1, x + w - t, y + inset, x + w, y + h / 2 - inset / 2)
        segment(2, x + w - t, y + h / 2 + inset / 2, x + w, y + h - inset)
        segment(3, x + inset, y + h - t, x + w - inset, y + h)
        segment(4, x, y + h / 2 + inset / 2, x + t, y + h - inset)
        segment(5, x, y + inset, x + t, y + h / 2 - inset / 2)
        segment(6, x + inset, y + h / 2 - t / 2, x + w - inset, y + h / 2 + t / 2)
    }
}

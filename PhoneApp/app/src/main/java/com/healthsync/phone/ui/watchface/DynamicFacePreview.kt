package com.healthsync.phone.ui.watchface

import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.healthsync.phone.data.watchface.*
import kotlin.math.*

/**
 * 1:1 Jetpack Compose Canvas previewer for declarative .hswf dynamic watch faces.
 * Renders on Android phone screens with pixel-accurate parity to the native watch Canvas engine.
 */
@Composable
fun DynamicFacePreview(
    pkg: HswfPackage,
    modifier: Modifier = Modifier,
    hours: Int = 10,
    minutes: Int = 10,
    seconds: Int = 30,
    heartRate: Int = 72,
    steps: Int = 4520,
    stepGoal: Int = 10000,
    battery: Int = 85
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .border(2.dp, Color(0xFF1E293B), CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val cx = w / 2f
            val cy = h / 2f
            val radius = min(cx, cy)
            val density = radius / 150f // normalize to watch scale

            // 1. Draw Background
            drawBackgroundLayer(pkg.background, cx, cy, radius, w, h)

            // 2. Draw Dial Ticks & Numbers
            drawDialLayer(pkg.dial, cx, cy, radius, density)

            // 3. Draw Digital Clock (if enabled)
            pkg.digitalClock?.let { if (it.enabled) drawDigitalClock(it, w, h, hours, minutes, seconds, density) }

            // 4. Draw Date Window (if enabled)
            pkg.date?.let { if (it.enabled) drawDateWindow(it, w, h, density) }

            // 5. Draw Complications
            drawComplicationsLayer(pkg.complications, w, h, radius, heartRate, steps, stepGoal, battery, density)

            // 6. Draw Analog Hands (if configured)
            pkg.hands?.let { drawHandsLayer(it, cx, cy, radius, hours, minutes, seconds, density) }
        }
    }
}

private fun DrawScope.drawBackgroundLayer(
    bg: BackgroundConfig,
    cx: Float,
    cy: Float,
    radius: Float,
    w: Float,
    h: Float
) {
    when (bg.type) {
        "gradient_radial" -> {
            val start = parseColor(bg.gradientStartHex ?: bg.colorHex, Color(0xFF101622))
            val end = parseColor(bg.gradientEndHex ?: "#000000", Color.Black)
            drawCircle(
                brush = Brush.radialGradient(listOf(start, end), center = Offset(cx, cy), radius = radius),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
        "gradient_linear" -> {
            val start = parseColor(bg.gradientStartHex ?: bg.colorHex, Color(0xFF101622))
            val end = parseColor(bg.gradientEndHex ?: "#000000", Color.Black)
            drawRect(
                brush = Brush.linearGradient(listOf(start, end), start = Offset(0f, 0f), end = Offset(w, h)),
                size = size
            )
        }
        else -> {
            val color = parseColor(bg.colorHex, Color(0xFF0A0D12))
            drawRect(color = color, size = size)
        }
    }
}

private fun DrawScope.drawDialLayer(
    dial: DialConfig,
    cx: Float,
    cy: Float,
    radius: Float,
    density: Float
) {
    val dialR = radius * dial.dialRadiusRatio

    if (dial.showTicks) {
        val count = dial.tickCount
        val majorColor = parseColor(dial.majorTickColorHex, Color(0xFF00E5FF))
        val minorColor = parseColor(dial.minorTickColorHex, Color(0xFF334155))

        for (i in 0 until count) {
            val isMajor = (i % (count / 12)) == 0
            val angleDeg = (i * 360f / count) - 90f
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val tickLen = (if (isMajor) dial.majorTickLength else dial.minorTickLength) * density
            val color = if (isMajor) majorColor else minorColor
            val strokeW = dial.tickWidth * density

            val outer = Offset(cx + (cos(angleRad) * dialR).toFloat(), cy + (sin(angleRad) * dialR).toFloat())
            val inner = Offset(cx + (cos(angleRad) * (dialR - tickLen)).toFloat(), cy + (sin(angleRad) * (dialR - tickLen)).toFloat())

            drawLine(color = color, start = inner, end = outer, strokeWidth = strokeW, cap = StrokeCap.Round)
        }
    }

    if (dial.showNumbers) {
        val numRadius = dialR - (dial.majorTickLength + 12f) * density
        val numColor = parseColor(dial.numberColorHex, Color.White).toArgb()

        drawIntoCanvas { canvas ->
            val paint = AndroidPaint().apply {
                isAntiAlias = true
                textAlign = AndroidPaint.Align.CENTER
                textSize = dial.numberSizeSp * density
                color = numColor
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            when (dial.numberType) {
                "arabic" -> {
                    for (h in 1..12) {
                        val angleRad = Math.toRadians((h * 30.0) - 90.0)
                        val nx = cx + (cos(angleRad) * numRadius).toFloat()
                        val ny = cy + (sin(angleRad) * numRadius).toFloat() + (paint.textSize * 0.35f)
                        canvas.nativeCanvas.drawText(h.toString(), nx, ny, paint)
                    }
                }
                "minimal_cardinal" -> {
                    val cardinal = listOf(12 to "12", 3 to "3", 6 to "6", 9 to "9")
                    for ((h, txt) in cardinal) {
                        val angleRad = Math.toRadians((h * 30.0) - 90.0)
                        val nx = cx + (cos(angleRad) * numRadius).toFloat()
                        val ny = cy + (sin(angleRad) * numRadius).toFloat() + (paint.textSize * 0.35f)
                        canvas.nativeCanvas.drawText(txt, nx, ny, paint)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawDigitalClock(
    cfg: DigitalClockConfig,
    w: Float,
    h: Float,
    hours: Int,
    minutes: Int,
    seconds: Int,
    density: Float
) {
    val x = w * cfg.xRatio
    val y = h * cfg.yRatio
    val timeStr = String.format("%02d:%02d", (hours % 12).let { if (it == 0) 12 else it }, minutes)

    drawIntoCanvas { canvas ->
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            textSize = cfg.fontSizeSp * density
            color = parseColor(cfg.colorHex, Color.White).toArgb()
            typeface = Typeface.create(Typeface.MONOSPACE, if (cfg.isBold) Typeface.BOLD else Typeface.NORMAL)
        }
        canvas.nativeCanvas.drawText(timeStr, x, y, paint)
    }
}

private fun DrawScope.drawDateWindow(
    cfg: DateConfig,
    w: Float,
    h: Float,
    density: Float
) {
    val x = w * cfg.xRatio
    val y = h * cfg.yRatio
    val dateStr = "SAT 10"

    drawIntoCanvas { canvas ->
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            textSize = cfg.fontSizeSp * density
            color = parseColor(cfg.colorHex, Color(0xFF94A3B8)).toArgb()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        if (cfg.hasFrame) {
            val textWidth = paint.measureText(dateStr)
            val padX = 6f * density
            val padY = 3f * density
            val bgPaint = AndroidPaint().apply {
                isAntiAlias = true
                color = parseColor(cfg.frameColorHex, Color(0xFF1E293B)).toArgb()
            }
            canvas.nativeCanvas.drawRoundRect(
                x - textWidth / 2f - padX,
                y - paint.textSize - padY + (3f * density),
                x + textWidth / 2f + padX,
                y + padY,
                4f * density,
                4f * density,
                bgPaint
            )
        }
        canvas.nativeCanvas.drawText(dateStr, x, y, paint)
    }
}

private fun DrawScope.drawComplicationsLayer(
    cfg: ComplicationsConfig,
    w: Float,
    h: Float,
    radius: Float,
    bpm: Int,
    steps: Int,
    goal: Int,
    bat: Int,
    density: Float
) {
    if (cfg.heartRate.enabled) {
        drawGauge(
            cx = w * cfg.heartRate.xRatio,
            cy = h * cfg.heartRate.yRatio,
            r = radius * cfg.heartRate.radiusRatio,
            progress = (bpm / 180f).coerceIn(0f, 1f),
            label = cfg.heartRate.label ?: "BPM",
            valText = "$bpm",
            color = parseColor(cfg.heartRate.colorHex, Color(0xFFFF007F)),
            trackColor = parseColor(cfg.heartRate.accentColorHex, Color(0xFF1E293B)),
            density = density
        )
    }

    if (cfg.steps.enabled) {
        drawGauge(
            cx = w * cfg.steps.xRatio,
            cy = h * cfg.steps.yRatio,
            r = radius * cfg.steps.radiusRatio,
            progress = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f),
            label = cfg.steps.label ?: "STEPS",
            valText = if (steps >= 1000) String.format("%.1fk", steps / 1000f) else "$steps",
            color = parseColor(cfg.steps.colorHex, Color(0xFF00E5FF)),
            trackColor = parseColor(cfg.steps.accentColorHex, Color(0xFF1E293B)),
            density = density
        )
    }

    if (cfg.battery.enabled) {
        drawGauge(
            cx = w * cfg.battery.xRatio,
            cy = h * cfg.battery.yRatio,
            r = radius * cfg.battery.radiusRatio,
            progress = (bat / 100f).coerceIn(0f, 1f),
            label = "BAT",
            valText = "$bat%",
            color = parseColor(cfg.battery.colorHex, Color(0xFF10B981)),
            trackColor = parseColor(cfg.battery.accentColorHex, Color(0xFF1E293B)),
            density = density
        )
    }
}

private fun DrawScope.drawGauge(
    cx: Float,
    cy: Float,
    r: Float,
    progress: Float,
    label: String,
    valText: String,
    color: Color,
    trackColor: Color,
    density: Float
) {
    val stroke = Stroke(width = 3f * density, cap = StrokeCap.Round)
    val startAngle = 135f
    val sweepAngle = 270f

    drawArc(
        color = trackColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        style = stroke
    )

    if (progress > 0) {
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle * progress,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = stroke
        )
    }

    drawIntoCanvas { canvas ->
        val textPaint = AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            textSize = r * 0.6f
            setColor(AndroidColor.WHITE)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.nativeCanvas.drawText(valText, cx, cy + (textPaint.textSize * 0.35f), textPaint)
    }
}

private fun DrawScope.drawHandsLayer(
    hands: HandsConfig,
    cx: Float,
    cy: Float,
    radius: Float,
    hours: Int,
    minutes: Int,
    seconds: Int,
    density: Float
) {
    val hourAngle = ((hours % 12 + minutes / 60f) * 30f) - 90f
    val minAngle = ((minutes + seconds / 60f) * 6f) - 90f
    val secAngle = (seconds * 6f) - 90f

    // Hour hand
    drawHand(cx, cy, hourAngle, radius * hands.hourHand.lengthRatio, hands.hourHand.widthDp * density, parseColor(hands.hourHand.colorHex, Color.White))

    // Minute hand
    drawHand(cx, cy, minAngle, radius * hands.minuteHand.lengthRatio, hands.minuteHand.widthDp * density, parseColor(hands.minuteHand.colorHex, Color(0xFF38BDF8)))

    // Second hand
    hands.secondHand?.let { sec ->
        drawHand(cx, cy, secAngle, radius * sec.lengthRatio, sec.widthDp * density, parseColor(sec.colorHex, Color(0xFFFF3366)))
    }

    // Pin cap
    drawCircle(Color.White, radius = 5f * density, center = Offset(cx, cy))
}

private fun DrawScope.drawHand(
    cx: Float,
    cy: Float,
    angleDeg: Float,
    length: Float,
    width: Float,
    color: Color
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val end = Offset(cx + (cos(rad) * length).toFloat(), cy + (sin(rad) * length).toFloat())
    drawLine(color = color, start = Offset(cx, cy), end = end, strokeWidth = width, cap = StrokeCap.Round)
}

private fun parseColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(AndroidColor.parseColor(hex.trim()))
    } catch (_: Exception) {
        fallback
    }
}

package com.healthsync.phone.ui.watchface

import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.healthsync.phone.data.watchface.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * Design preview for declarative .hswf faces. Missing health values remain placeholders.
 */
@Composable
fun DynamicFacePreview(
    pkg: HswfPackage,
    modifier: Modifier = Modifier,
    hours: Int = 10,
    minutes: Int = 10,
    seconds: Int = 30,
    heartRate: Int = -1,
    steps: Int = -1,
    stepGoal: Int = 10000,
    battery: Int = -1,
    ambientLayout: Boolean = false
) {
    val cachedImage = remember(pkg.background.imageBase64) {
        val b64 = pkg.background.imageBase64
        if (!b64.isNullOrBlank()) {
            try {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) { null }
        } else null
    }

    val infiniteTransition = rememberInfiniteTransition(label = "face_anim")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

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
            if (ambientLayout && pkg.aod.hideBackground) drawRect(Color.Black)
            else drawBackgroundLayer(pkg.background, cachedImage, cx, cy, radius, w, h)

            // 2. Draw Animation Layer (Dynamic effects)
            pkg.animation?.let {
                if (!ambientLayout && it.type != "none") {
                    drawAnimationLayer(it, w, h, cx, cy, radius, animProgress, density)
                }
            }

            // 3. Draw Dial Ticks & Numbers
            val previewDial = if (ambientLayout && pkg.aod.hideMinorTicks) pkg.dial.copy(tickCount = 12) else pkg.dial
            drawDialLayer(previewDial, cx, cy, radius, density)

            // 3. Draw Digital Clock (if enabled)
            pkg.digitalClock?.let { if (it.enabled) drawDigitalClock(it, w, h, hours, minutes, seconds, density) }

            // 4. Draw Date Window (if enabled)
            pkg.date?.let { if (it.enabled) drawDateWindow(if (ambientLayout) it.copy(hasFrame = false) else it, w, h, density) }

            // 5. Draw Complications
            drawComplicationsLayer(pkg.complications, w, h, radius, heartRate, steps, stepGoal, battery, density)

            // 6. Draw Analog Hands (if configured)
            pkg.hands?.let {
                val previewHands = if (ambientLayout && pkg.aod.hideSecondsHand) it.copy(secondHand = null) else it
                drawHandsLayer(previewHands, cx, cy, radius, hours, minutes, seconds, density)
            }
        }
    }
}

private fun DrawScope.drawBackgroundLayer(
    bg: BackgroundConfig,
    cachedImage: ImageBitmap?,
    cx: Float,
    cy: Float,
    radius: Float,
    w: Float,
    h: Float
) {
    when (bg.type) {
        "image" -> {
            if (cachedImage != null) {
                drawImage(
                    image = cachedImage,
                    dstSize = IntSize(w.toInt(), h.toInt())
                )
            } else {
                val color = parseColor(bg.colorHex, Color(0xFF0A0D12))
                drawRect(color = color, size = size)
            }
        }
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
            val angle = Math.toRadians(bg.gradientAngle.toDouble())
            val delta = Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
            drawRect(
                brush = Brush.linearGradient(listOf(start, end), start = Offset(cx, cy) - delta, end = Offset(cx, cy) + delta),
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
        val count = dial.tickCount.coerceAtLeast(12)
        val majorColor = parseColor(dial.majorTickColorHex, Color(0xFF00E5FF))
        val minorColor = parseColor(dial.minorTickColorHex, Color(0xFF334155))

        for (i in 0 until count) {
            val isMajor = (i % (count / 12).coerceAtLeast(1)) == 0
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
                "arabic", "roman" -> {
                    val roman = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
                    for (h in 1..12) {
                        val angleRad = Math.toRadians((h * 30.0) - 90.0)
                        val nx = cx + (cos(angleRad) * numRadius).toFloat()
                        val ny = cy + (sin(angleRad) * numRadius).toFloat() + (paint.textSize * 0.35f)
                        canvas.nativeCanvas.drawText(if (dial.numberType == "roman") roman[h - 1] else h.toString(), nx, ny, paint)
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
    val timeStr = when (cfg.format) {
        "HH:mm:ss" -> String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        "hh:mm a" -> String.format(Locale.getDefault(), "%02d:%02d %s", (hours % 12).let { if (it == 0) 12 else it }, minutes, if (hours < 12) "AM" else "PM")
        else -> String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
    }

    drawIntoCanvas { canvas ->
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            textSize = cfg.fontSizeSp * density
            color = parseColor(cfg.colorHex, Color.White).toArgb()
            val family = when (cfg.fontFamily) { "serif" -> Typeface.SERIF; "sans" -> Typeface.SANS_SERIF; else -> Typeface.MONOSPACE }
            typeface = Typeface.create(family, if (cfg.isBold) Typeface.BOLD else Typeface.NORMAL)
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
    val dateFormat = cfg.format.takeIf { it in listOf("d", "dd/MM", "EEE, MMM d", "EEE d") } ?: "EEE d"
    val dateStr = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())

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
            valText = if (bpm > 0) "$bpm" else "—",
            color = parseColor(cfg.heartRate.colorHex, Color(0xFFFF007F)),
            trackColor = parseColor(cfg.heartRate.accentColorHex, Color(0xFF1E293B)),
            widgetStyle = cfg.heartRate.style,
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
            valText = if (steps < 0) "—" else if (steps >= 1000) String.format("%.1fk", steps / 1000f) else "$steps",
            color = parseColor(cfg.steps.colorHex, Color(0xFF00E5FF)),
            trackColor = parseColor(cfg.steps.accentColorHex, Color(0xFF1E293B)),
            widgetStyle = cfg.steps.style,
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
            valText = if (bat in 0..100) "$bat%" else "—",
            color = parseColor(cfg.battery.colorHex, Color(0xFF10B981)),
            trackColor = parseColor(cfg.battery.accentColorHex, Color(0xFF1E293B)),
            widgetStyle = cfg.battery.style,
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
    density: Float,
    widgetStyle: String
) {
    val stroke = Stroke(width = 3f * density, cap = StrokeCap.Round)
    val startAngle = 135f
    val sweepAngle = 270f

    if (widgetStyle == "arc_gauge" || widgetStyle == "subdial") drawArc(
        color = trackColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        style = stroke
    )

    if (progress > 0 && (widgetStyle == "arc_gauge" || widgetStyle == "subdial")) {
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
        val valueColor = if (widgetStyle == "text_only" || widgetStyle == "icon_text") color.toArgb() else AndroidColor.WHITE
        val textPaint = AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            textSize = if (widgetStyle == "text_only" || widgetStyle == "icon_text") 12f * density else r * 0.6f
            setColor(valueColor)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        if (widgetStyle == "text_only" || widgetStyle == "icon_text") {
            canvas.nativeCanvas.drawText("$valText $label", cx, cy, textPaint)
        } else {
            canvas.nativeCanvas.drawText(valText, cx, cy + (textPaint.textSize * 0.35f), textPaint)
            textPaint.textSize = r * .32f
            textPaint.color = AndroidColor.rgb(148, 163, 184)
            textPaint.typeface = Typeface.DEFAULT
            canvas.nativeCanvas.drawText(label, cx, cy + r - 2f * density, textPaint)
        }
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
    drawHand(cx, cy, hourAngle, radius, hands.hourHand, density)

    // Minute hand
    drawHand(cx, cy, minAngle, radius, hands.minuteHand, density)

    // Second hand
    hands.secondHand?.let { sec ->
        drawHand(cx, cy, secAngle, radius, sec, density)
    }

    // Pin cap
    drawCircle(parseColor(hands.minuteHand.colorHex, Color.White), radius = hands.hourHand.capRadius * density, center = Offset(cx, cy))
}

private fun DrawScope.drawHand(
    cx: Float,
    cy: Float,
    angleDeg: Float,
    radius: Float,
    style: HandStyle,
    density: Float
) {
    val length = radius * style.lengthRatio
    val width = style.widthDp * density
    val tail = length * style.tailRatio
    val color = parseColor(style.colorHex, Color.White)
    withTransform({ rotate(angleDeg + 90f, Offset(cx, cy)) }) {
        when (style.shape) {
            "needle" -> drawLine(color, Offset(cx, cy + tail), Offset(cx, cy - length), width, StrokeCap.Round)
            "baton" -> drawRoundRect(color, topLeft = Offset(cx - width / 2f, cy - length), size = Size(width, length + tail), cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f))
            else -> {
                val path = Path().apply {
                    moveTo(cx - width / 2f, cy + tail)
                    lineTo(cx + width / 2f, cy + tail)
                    lineTo(cx + width / 2f, cy - length * .72f)
                    lineTo(cx + width * (if (style.shape == "arrow") 1.6f else .7f), cy - length * .72f)
                    lineTo(cx, cy - length)
                    lineTo(cx - width * (if (style.shape == "arrow") 1.6f else .7f), cy - length * .72f)
                    lineTo(cx - width / 2f, cy - length * .72f)
                    close()
                }
                drawPath(path, color)
            }
        }
        if (style.hasCounterweight) drawCircle(color, width * 1.4f, Offset(cx, cy + tail * .65f))
    }
}

private fun parseColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(AndroidColor.parseColor(hex.trim()))
    } catch (_: Exception) {
        fallback
    }
}

private fun DrawScope.drawAnimationLayer(
    anim: AnimationConfig,
    w: Float,
    h: Float,
    cx: Float,
    cy: Float,
    radius: Float,
    progress: Float,
    density: Float
) {
    val color = parseColor(anim.colorHex, Color(0xFF00E5FF))
    val ax = w * anim.xRatio
    val ay = h * anim.yRatio
    val scale = anim.scaleRatio.coerceIn(0.2f, 2.5f)

    when (anim.type) {
        "pulse_ecg" -> {
            val waveWidth = radius * 0.95f * scale
            val halfW = waveWidth / 2f
            val startX = ax - halfW
            val endX = ax + halfW
            val waveLen = waveWidth * 0.45f
            val offset = (progress * (waveWidth + waveLen)) - waveLen

            val path = Path().apply {
                moveTo(startX, ay)
                var curX = startX
                while (curX <= endX) {
                    val relX = curX - (startX + offset)
                    val wy = if (relX in 0f..waveLen) {
                        val p = relX / waveLen
                        when {
                            p < 0.2f -> ay
                            p < 0.35f -> ay - (sin((p - 0.2f) / 0.15f * Math.PI) * 7f * density).toFloat()
                            p < 0.45f -> ay + (sin((p - 0.35f) / 0.10f * Math.PI) * 6f * density).toFloat()
                            p < 0.55f -> ay - (sin((p - 0.45f) / 0.10f * Math.PI) * 28f * density).toFloat()
                            p < 0.65f -> ay + (sin((p - 0.55f) / 0.10f * Math.PI) * 11f * density).toFloat()
                            p < 0.85f -> ay - (sin((p - 0.65f) / 0.20f * Math.PI) * 10f * density).toFloat()
                            else -> ay
                        }
                    } else ay
                    lineTo(curX, wy)
                    curX += 4f * density
                }
            }
            drawPath(path, color, style = Stroke(width = 2.5f * density, cap = StrokeCap.Round))
            val headX = (startX + offset + waveLen * 0.55f).coerceIn(startX, endX)
            drawCircle(Color.White, 3f * density, Offset(headX, ay))
            drawCircle(color.copy(alpha = 0.35f), 6.5f * density, Offset(headX, ay))
        }
        "radar_sweep" -> {
            val r = radius * 0.88f * scale
            drawCircle(color.copy(alpha = 0.12f), r * 0.33f, Offset(cx, cy), style = Stroke(width = 1.2f * density))
            drawCircle(color.copy(alpha = 0.12f), r * 0.66f, Offset(cx, cy), style = Stroke(width = 1.2f * density))
            drawCircle(color.copy(alpha = 0.15f), r, Offset(cx, cy), style = Stroke(width = 1.2f * density))

            val sweepAngle = (progress * 360f) % 360f
            val rad = Math.toRadians(sweepAngle.toDouble())
            val bx = cx + (cos(rad) * r).toFloat()
            val by = cy + (sin(rad) * r).toFloat()
            drawLine(color, Offset(cx, cy), Offset(bx, by), strokeWidth = 2.5f * density, cap = StrokeCap.Round)

            val blipAlpha = ((sin(progress * 20.0) + 1.0) * 0.5 * 0.8 + 0.2).toFloat()
            drawCircle(color.copy(alpha = blipAlpha), 3.5f * density, Offset(cx + r * 0.5f, cy - r * 0.35f))
            drawCircle(color.copy(alpha = blipAlpha), 2.8f * density, Offset(cx - r * 0.4f, cy + r * 0.45f))
        }
        "equalizer_bars" -> {
            val barCount = 10
            val barW = (radius * 0.75f * scale / barCount) * 0.65f
            val spacing = (radius * 0.75f * scale / barCount) * 0.35f
            val startX = ax - (radius * 0.75f * scale) / 2f
            val maxH = 26f * density

            for (i in 0 until barCount) {
                val bx = startX + i * (barW + spacing)
                val hFactor = ((sin(progress * 15.0 + i * 0.85) + 1.0) * 0.5 * 0.85 + 0.15).toFloat()
                val bh = maxH * hFactor
                drawRoundRect(
                    color = color,
                    topLeft = Offset(bx, ay - bh / 2f),
                    size = Size(barW, bh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
                )
            }
        }
        "tourbillon_spin" -> {
            val r = radius * 0.35f * scale
            drawCircle(Color(0xFFD4AF37), r, Offset(ax, ay), style = Stroke(width = 2.5f * density))
            val rotAngle = (progress * 360f * 1.5f) % 360f
            withTransform({ rotate(rotAngle, Offset(ax, ay)) }) {
                drawCircle(color, r * 0.8f, Offset(ax, ay), style = Stroke(width = 2f * density))
                for (i in 0..2) {
                    val ang = Math.toRadians((i * 120.0))
                    val ex = ax + (cos(ang) * r * 0.8f).toFloat()
                    val ey = ay + (sin(ang) * r * 0.8f).toFloat()
                    drawLine(color, Offset(ax, ay), Offset(ex, ey), strokeWidth = 1.8f * density)
                    drawCircle(Color(0xFFD4AF37), 2f * density, Offset(ex, ey))
                }
            }
            drawCircle(Color(0xFFE11D48), 4f * density, Offset(ax, ay))
        }
        "orbital_space" -> {
            val r = radius * 0.8f * scale
            drawCircle(color.copy(alpha = 0.15f), r * 0.75f, Offset(cx, cy), style = Stroke(width = 1.2f * density))
            val earthAng = Math.toRadians((progress * 360.0))
            val ex = cx + (cos(earthAng) * r * 0.75f).toFloat()
            val ey = cy + (sin(earthAng) * r * 0.75f).toFloat()
            drawCircle(Color(0xFF38BDF8), 6f * density, Offset(ex, ey))

            val moonAng = Math.toRadians((progress * 360.0 * 4.0))
            val mx = ex + (cos(moonAng) * 14f * density).toFloat()
            val my = ey + (sin(moonAng) * 14f * density).toFloat()
            drawCircle(Color(0xFFE2E8F0), 2.2f * density, Offset(mx, my))
        }
        "liquid_wave" -> {
            val fluidLevel = cy + radius * 0.25f
            val wavePath = Path().apply {
                moveTo(cx - radius, cy + radius)
                lineTo(cx - radius, fluidLevel)
                val steps = 24
                val stepW = (radius * 2f) / steps
                for (i in 0..steps) {
                    val px = cx - radius + i * stepW
                    val py = fluidLevel + (sin((i.toFloat() / steps * 2.5 * Math.PI) + progress * 2.0 * Math.PI) * 5f * density).toFloat()
                    lineTo(px, py)
                }
                lineTo(cx + radius, cy + radius)
                close()
            }
            drawPath(wavePath, color.copy(alpha = 0.35f))
        }
        "aurora_shimmer" -> {
            val auroraTop = cy - radius * 0.65f
            val auroraPath = Path().apply {
                moveTo(cx - radius * 0.85f, cy)
                for (i in 0..10) {
                    val px = cx - radius * 0.85f + i * (radius * 1.7f / 10f)
                    val py = auroraTop + (sin((i * 0.5) + progress * 2.0 * Math.PI) * 16f * density).toFloat()
                    lineTo(px, py)
                }
                lineTo(cx + radius * 0.85f, cy)
                close()
            }
            drawPath(auroraPath, color.copy(alpha = 0.25f))
        }
    }
}


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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.healthsync.phone.data.watchface.*
import java.text.SimpleDateFormat
import java.util.Calendar
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
    ambientLayout: Boolean = false,
    roundPreview: Boolean = true
) {
    val cachedImage = remember(pkg.background.imageBase64) {
        val b64 = pkg.background.imageBase64
        if (!b64.isNullOrBlank() && b64.length <= 16 * 1024 * 1024) {
            try {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    var sample = 1
                    while (max(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                } else null
            } catch (_: Exception) { null }
        } else null
    }

    val animProgress = if (!ambientLayout && pkg.animation?.type?.let { it != "none" } == true) {
        val infiniteTransition = rememberInfiniteTransition(label = "face_anim")
        val speed = pkg.animation?.speedRatio?.takeIf { it.isFinite() && it > 0f } ?: 1f
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween((2400f / speed.coerceIn(.1f, 5f)).toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "progress"
        )
        progress
    } else 0f

    val previewShape = if (roundPreview) CircleShape else RectangleShape
    val sampleDate = remember(hours, minutes, seconds) {
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 13, hours.coerceIn(0, 23), minutes.coerceIn(0, 59), seconds.coerceIn(0, 59))
        }.time
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(previewShape)
            .border(2.dp, Color(0xFF1E293B), previewShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val cx = w / 2f
            val cy = h / 2f
            val radius = min(cx, cy)
            val density = min(w, h) / 320f // shared 320-unit design space, independent of phone density

            // Background brightness follows AOD, while keeping photo/gradient contrast.
            if (ambientLayout) drawRect(Color.Black)
            if (!ambientLayout || !pkg.aod.hideBackground) {
                withAmbientStyle(ambientLayout, pkg.aod, monochrome = false) {
                    drawBackgroundLayer(pkg.background, cachedImage, cx, cy, radius, w, h)
                }
            }

            withAmbientStyle(ambientLayout, pkg.aod) {
                if (ambientLayout && pkg.aod.mode == "digital_minimal") {
                    val configuredClock = pkg.digitalClock?.takeIf { it.enabled }
                    val minimalClock = configuredClock?.copy(
                        format = if (configuredClock.format == "HH:mm:ss") "HH:mm" else configuredClock.format
                    ) ?: DigitalClockConfig(
                        xRatio = .5f,
                        yRatio = .5f,
                        fontSizeSp = 44f,
                        fontFamily = "sans",
                        isBold = false
                    )
                    drawDigitalClock(minimalClock, w, h, sampleDate, density)
                } else {
                    pkg.animation?.let {
                        if (!ambientLayout && it.type != "none") {
                            drawAnimationLayer(it, w, h, radius, animProgress, density)
                        }
                    }
                    val previewDial = if (ambientLayout && pkg.aod.hideMinorTicks) pkg.dial.copy(tickCount = 12) else pkg.dial
                    drawDialLayer(previewDial, cx, cy, radius, density)
                    pkg.digitalClock?.let { if (it.enabled) drawDigitalClock(it, w, h, sampleDate, density) }
                    pkg.date?.let { if (it.enabled) drawDateWindow(if (ambientLayout) it.copy(hasFrame = false) else it, w, h, sampleDate, density) }
                    if (!ambientLayout || !pkg.aod.hideComplications) {
                        drawComplicationsLayer(pkg.complications, w, h, radius, heartRate, steps, stepGoal, battery, density)
                    }
                    pkg.hands?.let {
                        val previewHands = if (ambientLayout && pkg.aod.hideSecondsHand) it.copy(secondHand = null) else it
                        drawHandsLayer(previewHands, cx, cy, radius, hours, minutes, seconds, density)
                    }
                }
            }

            // Custom design layers use a fixed 320-unit design space on every display.
            drawCustomFaceLayers(pkg.layers, w, h, ambientLayout, pkg.aod)
        }
    }
}

/** Apply ambient brightness/tint once to the completed standard-element layer. */
private fun DrawScope.withAmbientStyle(
    ambient: Boolean,
    aod: AodConfig,
    monochrome: Boolean = true,
    drawContent: DrawScope.() -> Unit
) {
    if (!ambient) {
        drawContent()
        return
    }
    val layerPaint = androidx.compose.ui.graphics.Paint().apply {
        alpha = aod.maxBrightnessFactor.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .6f
        if (monochrome && aod.mode == "monochrome") {
            colorFilter = ColorFilter.tint(parseColor(aod.monochromeHex, Color.White).copy(alpha = 1f), BlendMode.SrcIn)
        }
    }
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
    try {
        drawContent()
    } finally {
        drawContext.canvas.restore()
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
                drawRect(Color.Black.copy(alpha = bg.imageDimAmount.coerceIn(0f, 1f)))
            } else {
                val color = parseColor(bg.colorHex, Color(0xFF0A0D12))
                drawRect(color = color, size = size)
            }
        }
        "gradient_radial" -> {
            val start = parseColor(bg.gradientStartHex ?: bg.colorHex, Color(0xFF101622))
            val end = parseColor(bg.gradientEndHex ?: "#000000", Color.Black)
            drawRect(brush = Brush.radialGradient(listOf(start, end), center = Offset(cx, cy), radius = radius))
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
        val numRadius = dialR - (dial.majorTickLength + 14f) * density
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
    sampleDate: Date,
    density: Float
) {
    val x = w * cfg.xRatio
    val y = h * cfg.yRatio
    val timeStr = formatPreviewDate(cfg.format, sampleDate, "HH:mm")

    drawIntoCanvas { canvas ->
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            textSize = cfg.fontSizeSp * density
            color = parseColor(cfg.colorHex, Color.White).toArgb()
            val family = when (cfg.fontFamily) { "serif" -> Typeface.SERIF; "monospace" -> Typeface.MONOSPACE; else -> Typeface.SANS_SERIF }
            typeface = Typeface.create(family, if (cfg.isBold) Typeface.BOLD else Typeface.NORMAL)
        }
        canvas.nativeCanvas.drawText(timeStr, x, y, paint)
    }
}

private fun DrawScope.drawDateWindow(
    cfg: DateConfig,
    w: Float,
    h: Float,
    sampleDate: Date,
    density: Float
) {
    val x = w * cfg.xRatio
    val y = h * cfg.yRatio
    val dateStr = formatPreviewDate(cfg.format, sampleDate, "EEE d").uppercase(Locale.getDefault())

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
            val padX = 8f * density
            val padY = 4f * density
            val bgPaint = AndroidPaint().apply {
                isAntiAlias = true
                color = parseColor(cfg.frameColorHex, Color(0xFF1E293B)).toArgb()
            }
            canvas.nativeCanvas.drawRoundRect(
                x - textWidth / 2f - padX,
                y - paint.textSize - padY + (4f * density),
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
    val stroke = Stroke(width = 3.5f * density, cap = StrokeCap.Round)
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
            textSize = if (widgetStyle == "text_only" || widgetStyle == "icon_text") 12f * density else r * 0.58f
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

private fun formatPreviewDate(pattern: String, sampleDate: Date, fallback: String): String = try {
    SimpleDateFormat(pattern, Locale.getDefault()).format(sampleDate)
} catch (_: IllegalArgumentException) {
    SimpleDateFormat(fallback, Locale.getDefault()).format(sampleDate)
}

private fun DrawScope.drawCustomFaceLayers(
    layers: List<FaceLayer>,
    w: Float,
    h: Float,
    ambient: Boolean,
    aod: AodConfig
) {
    val designScale = min(w, h) / 320f
    layers.forEach { source ->
        val layer = source.normalized()
        if (!layer.visible || (ambient && !layer.showInAmbient)) return@forEach
        val center = Offset(w * layer.xRatio, h * layer.yRatio)
        val width = w * layer.widthRatio
        val height = h * layer.heightRatio
        val sourceColor = parseColor(layer.colorHex, Color.White)
        val layerColor = if (ambient && aod.mode == "monochrome") parseColor(aod.monochromeHex, Color.White)
            else sourceColor
        val brightness = if (ambient) aod.maxBrightnessFactor.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .6f else 1f
        val color = layerColor.copy(alpha = sourceColor.alpha * layer.opacity * brightness)
        withTransform({ rotate(layer.rotationDegrees, center) }) {
            when (layer.type) {
                "rectangle" -> drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
                    size = Size(width, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(layer.cornerRadiusDp * designScale)
                )
                "circle" -> drawOval(
                    color = color,
                    topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
                    size = Size(width, height)
                )
                "line" -> drawLine(
                    color = color,
                    start = Offset(center.x - width / 2f, center.y),
                    end = Offset(center.x + width / 2f, center.y),
                    strokeWidth = height,
                    cap = StrokeCap.Round
                )
                else -> drawIntoCanvas { canvas ->
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        textAlign = AndroidPaint.Align.CENTER
                        textSize = layer.fontSizeSp * designScale
                        this.color = color.toArgb()
                        typeface = Typeface.create(
                            when (layer.fontFamily) {
                                "serif" -> Typeface.SERIF
                                "monospace" -> Typeface.MONOSPACE
                                else -> Typeface.SANS_SERIF
                            },
                            if (layer.isBold) Typeface.BOLD else Typeface.NORMAL
                        )
                    }
                    val baseline = center.y - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
                    canvas.nativeCanvas.drawText(layer.text, center.x, baseline, paint)
                }
            }
        }
    }
}

private fun DrawScope.drawAnimationLayer(
    anim: AnimationConfig,
    w: Float,
    h: Float,
    radius: Float,
    progress: Float,
    density: Float
) {
    val color = parseColor(anim.colorHex, Color(0xFF00E5FF))
    val ax = w * anim.xRatio
    val ay = h * anim.yRatio
    val scale = anim.scaleRatio.coerceIn(0.1f, 3f)

    when (anim.type) {
        "zen_ripple" -> {
            val rippleCenter = Offset(ax, ay)
            val maxRadius = radius * .85f * scale
            repeat(3) { index ->
                val phase = (progress + index / 3f) % 1f
                val fade = (1f - phase).pow(2)
                drawCircle(
                    color = color.copy(alpha = color.alpha * .65f * fade),
                    radius = maxRadius * (.08f + .92f * phase),
                    center = rippleCenter,
                    style = Stroke(width = 1.8f * density)
                )
            }
            drawCircle(color.copy(alpha = color.alpha * .55f), 2f * density, rippleCenter)
        }
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
            drawCircle(color.copy(alpha = 0.12f), r * 0.33f, Offset(ax, ay), style = Stroke(width = 1.2f * density))
            drawCircle(color.copy(alpha = 0.12f), r * 0.66f, Offset(ax, ay), style = Stroke(width = 1.2f * density))
            drawCircle(color.copy(alpha = 0.15f), r, Offset(ax, ay), style = Stroke(width = 1.2f * density))

            val sweepAngle = (progress * 360f) % 360f
            val rad = Math.toRadians(sweepAngle.toDouble())
            val bx = ax + (cos(rad) * r).toFloat()
            val by = ay + (sin(rad) * r).toFloat()
            drawLine(color, Offset(ax, ay), Offset(bx, by), strokeWidth = 2.5f * density, cap = StrokeCap.Round)

            val blipAlpha = ((sin(progress * 20.0) + 1.0) * 0.5 * 0.8 + 0.2).toFloat()
            drawCircle(color.copy(alpha = blipAlpha), 3.5f * density, Offset(ax + r * 0.5f, ay - r * 0.35f))
            drawCircle(color.copy(alpha = blipAlpha), 2.8f * density, Offset(ax - r * 0.4f, ay + r * 0.45f))
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
            drawCircle(color.copy(alpha = 0.15f), r * 0.75f, Offset(ax, ay), style = Stroke(width = 1.2f * density))
            val earthAng = Math.toRadians((progress * 360.0))
            val ex = ax + (cos(earthAng) * r * 0.75f).toFloat()
            val ey = ay + (sin(earthAng) * r * 0.75f).toFloat()
            drawCircle(Color(0xFF38BDF8), 6f * density, Offset(ex, ey))

            val moonAng = Math.toRadians((progress * 360.0 * 4.0))
            val mx = ex + (cos(moonAng) * 14f * density).toFloat()
            val my = ey + (sin(moonAng) * 14f * density).toFloat()
            drawCircle(Color(0xFFE2E8F0), 2.2f * density, Offset(mx, my))
        }
        "liquid_wave" -> {
            val r = radius * .85f * scale
            val fluidLevel = ay + r * 0.25f
            val wavePath = Path().apply {
                moveTo(ax - r, ay + r)
                lineTo(ax - r, fluidLevel)
                val steps = 24
                val stepW = (r * 2f) / steps
                for (i in 0..steps) {
                    val px = ax - r + i * stepW
                    val py = fluidLevel + (sin((i.toFloat() / steps * 2.5 * Math.PI) + progress * 2.0 * Math.PI) * 5f * density).toFloat()
                    lineTo(px, py)
                }
                lineTo(ax + r, ay + r)
                close()
            }
            val waveClip = Path().apply {
                addOval(Rect(Offset(ax - r * .95f, ay - r * .95f), Size(r * 1.9f, r * 1.9f)))
            }
            clipPath(waveClip) { drawPath(wavePath, color.copy(alpha = 0x45 / 255f)) }
        }
        "aurora_shimmer" -> {
            val r = radius * .9f * scale
            val auroraTop = ay - r * 0.75f
            val auroraPath = Path().apply {
                moveTo(ax - r * 0.8f, ay)
                for (i in 0..10) {
                    val px = ax - r * 0.8f + i * (r * 1.6f / 10f)
                    val py = auroraTop + (sin((i * 0.5) + progress * 2.0 * Math.PI) * 18f * density).toFloat()
                    lineTo(px, py)
                }
                lineTo(ax + r * 0.8f, ay)
                close()
            }
            drawPath(auroraPath, color.copy(alpha = 0x30 / 255f))
        }
    }
}


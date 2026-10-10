package com.healthsync.watch.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Base64
import android.view.View
import com.healthsync.watch.data.watchface.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * 60 FPS hardware-accelerated Canvas engine for rendering declarative .hswf watch faces.
 * Dynamically adapts to round/square watch screens with sub-pixel anti-aliasing,
 * drop shadows, gradient shaders, and real-time health complication bindings.
 */
class DynamicWatchFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var activePackage: HswfPackage? = null

    // Time tracking
    private var hours = 10
    private var minutes = 10
    private var seconds = 30
    private var millis = 0
    private var dayOfWeek = "SAT"
    private var dayOfMonth = 10
    private var month = "OCT"
    private val timeCal = Calendar.getInstance()

    // Health & System state
    private var heartRate: Int = 72
    private var heartRateUnverified: Boolean = false
    private var stepCount: Int = 4520
    private var stepGoal: Int = 10000
    private var calories: Double = 230.0
    private var distanceKm: Double = 3.2
    private var batteryLevel: Int = 85
    private var isBluetoothConnected: Boolean = true

    // Ambient / AOD state
    private var isAmbient: Boolean = false
    private var aodStyle: String = "match_dim"

    // Drawing paints & caches
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val digitalClockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val dateBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gaugePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val gaugeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val handPath = Path()
    private val arcRect = RectF()
    private var cachedBgBitmap: Bitmap? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Loads and activates a dynamic watch face schema. */
    fun loadFace(pkg: HswfPackage) {
        activePackage = pkg
        cachedBgBitmap?.recycle()
        cachedBgBitmap = null

        // Decode background image if supplied
        val b64 = pkg.background.imageBase64
        if (!b64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                cachedBgBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {}
        }
        invalidate()
    }

    /** Returns currently loaded face package. */
    fun getActivePackage(): HswfPackage? = activePackage

    fun updateTime(cal: Calendar) {
        timeCal.timeInMillis = cal.timeInMillis
        hours = cal.get(Calendar.HOUR)
        minutes = cal.get(Calendar.MINUTE)
        seconds = cal.get(Calendar.SECOND)
        millis = cal.get(Calendar.MILLISECOND)

        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())
        dayOfWeek = dayFmt.format(cal.time).uppercase(Locale.getDefault())
        month = monthFmt.format(cal.time).uppercase(Locale.getDefault())
        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

        invalidate()
    }

    fun setHealthData(bpm: Int, steps: Int, goal: Int, cals: Number, dist: Number) {
        heartRate = bpm
        stepCount = steps
        if (goal > 0) stepGoal = goal
        calories = cals.toDouble()
        distanceKm = dist.toDouble()
        if (!isAmbient) invalidate()
    }

    fun setHeartRateTime(time: Long) {}
    fun setHeartRateUnverified(value: Boolean) {
        heartRateUnverified = value
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

    fun setAmbientMode(ambient: Boolean) {
        isAmbient = ambient
        invalidate()
    }

    fun setAodStyle(style: String) {
        aodStyle = style
        if (isAmbient) invalidate()
    }

    fun stopIlluminator() {}
    fun triggerIlluminator() {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pkg = activePackage ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(cx, cy)
        val density = resources.displayMetrics.density

        // 1. Draw Background
        drawBackground(canvas, pkg.background, w, h, cx, cy, radius)

        // 2. Draw Dial Ticks & Numbers
        drawDial(canvas, pkg.dial, cx, cy, radius, density)

        // 3. Draw Digital Clock (if configured)
        pkg.digitalClock?.let { if (it.enabled) drawDigitalClock(canvas, it, w, h, density) }

        // 4. Draw Date Window (if configured)
        pkg.date?.let { if (it.enabled) drawDate(canvas, it, w, h, density) }

        // 5. Draw Complications
        drawComplications(canvas, pkg.complications, w, h, radius, density)

        // 6. Draw Analog Hands (if configured)
        pkg.hands?.let { drawHands(canvas, it, cx, cy, radius, density) }
    }

    private fun drawBackground(
        canvas: Canvas,
        bg: BackgroundConfig,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        if (isAmbient && activePackage?.aod?.hideBackground == true) {
            canvas.drawColor(Color.BLACK)
            return
        }

        when (bg.type) {
            "image" -> {
                val bmp = cachedBgBitmap
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, RectF(0f, 0f, w, h), bgPaint)
                } else {
                    bgPaint.shader = null
                    bgPaint.color = parseHex(bg.colorHex, 0xFF0A0D12.toInt())
                    canvas.drawRect(0f, 0f, w, h, bgPaint)
                }
            }
            "gradient_radial" -> {
                val start = parseHex(bg.gradientStartHex ?: bg.colorHex, 0xFF101622.toInt())
                val end = parseHex(bg.gradientEndHex ?: "#000000", Color.BLACK)
                bgPaint.shader = RadialGradient(cx, cy, radius, start, end, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, bgPaint)
            }
            "gradient_linear" -> {
                val start = parseHex(bg.gradientStartHex ?: bg.colorHex, 0xFF101622.toInt())
                val end = parseHex(bg.gradientEndHex ?: "#000000", Color.BLACK)
                val rad = Math.toRadians(bg.gradientAngle.toDouble())
                val dx = (cos(rad) * radius).toFloat()
                val dy = (sin(rad) * radius).toFloat()
                bgPaint.shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, start, end, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, bgPaint)
            }
            else -> {
                bgPaint.shader = null
                bgPaint.color = parseHex(bg.colorHex, 0xFF0A0D12.toInt())
                canvas.drawRect(0f, 0f, w, h, bgPaint)
            }
        }
    }

    private fun drawDial(
        canvas: Canvas,
        dial: DialConfig,
        cx: Float,
        cy: Float,
        radius: Float,
        density: Float
    ) {
        val dialR = radius * dial.dialRadiusRatio

        // Ticks
        if (dial.showTicks) {
            val count = if (isAmbient && activePackage?.aod?.hideMinorTicks == true) 12 else dial.tickCount
            val majorColor = parseHex(dial.majorTickColorHex, 0xFF00E5FF.toInt())
            val minorColor = if (isAmbient) 0xFF334155.toInt() else parseHex(dial.minorTickColorHex, 0xFF334155.toInt())

            for (i in 0 until count) {
                val isMajor = (i % (count / 12)) == 0
                val angleDeg = (i * 360f / count) - 90f
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val tickLen = (if (isMajor) dial.majorTickLength else dial.minorTickLength) * density
                tickPaint.color = if (isMajor) majorColor else minorColor
                tickPaint.strokeWidth = dial.tickWidth * density

                val outerX = cx + (cos(angleRad) * dialR).toFloat()
                val outerY = cy + (sin(angleRad) * dialR).toFloat()
                val innerX = cx + (cos(angleRad) * (dialR - tickLen)).toFloat()
                val innerY = cy + (sin(angleRad) * (dialR - tickLen)).toFloat()

                canvas.drawLine(innerX, innerY, outerX, outerY, tickPaint)
            }
        }

        // Numbers / Indices
        if (dial.showNumbers) {
            numberPaint.color = parseHex(dial.numberColorHex, Color.WHITE)
            numberPaint.textSize = dial.numberSizeSp * density
            val numRadius = dialR - (dial.majorTickLength + 14f) * density

            when (dial.numberType) {
                "arabic" -> {
                    for (h in 1..12) {
                        val angleRad = Math.toRadians((h * 30.0) - 90.0)
                        val nx = cx + (cos(angleRad) * numRadius).toFloat()
                        val ny = cy + (sin(angleRad) * numRadius).toFloat() + (numberPaint.textSize * 0.35f)
                        canvas.drawText(h.toString(), nx, ny, numberPaint)
                    }
                }
                "minimal_cardinal" -> {
                    val cardinal = listOf(12 to "12", 3 to "3", 6 to "6", 9 to "9")
                    for ((h, txt) in cardinal) {
                        val angleRad = Math.toRadians((h * 30.0) - 90.0)
                        val nx = cx + (cos(angleRad) * numRadius).toFloat()
                        val ny = cy + (sin(angleRad) * numRadius).toFloat() + (numberPaint.textSize * 0.35f)
                        canvas.drawText(txt, nx, ny, numberPaint)
                    }
                }
                "roman" -> {
                    val romans = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")
                    for (idx in romans.indices) {
                        val h = idx
                        val angleRad = Math.toRadians((h * 30.0) - 90.0)
                        val nx = cx + (cos(angleRad) * numRadius).toFloat()
                        val ny = cy + (sin(angleRad) * numRadius).toFloat() + (numberPaint.textSize * 0.35f)
                        canvas.drawText(romans[idx], nx, ny, numberPaint)
                    }
                }
            }
        }
    }

    private fun drawDigitalClock(
        canvas: Canvas,
        cfg: DigitalClockConfig,
        w: Float,
        h: Float,
        density: Float
    ) {
        val x = w * cfg.xRatio
        val y = h * cfg.yRatio

        val timeStr = when (cfg.format) {
            "hh:mm a" -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(timeCal.time)
            "HH:mm:ss" -> String.format(Locale.getDefault(), "%02d:%02d:%02d", (hours % 12).let { if (it == 0) 12 else it }, minutes, seconds)
            else -> String.format(Locale.getDefault(), "%02d:%02d", (hours % 12).let { if (it == 0) 12 else it }, minutes)
        }

        digitalClockPaint.textSize = cfg.fontSizeSp * density
        digitalClockPaint.color = parseHex(cfg.colorHex, Color.WHITE)
        digitalClockPaint.typeface = when (cfg.fontFamily) {
            "monospace" -> Typeface.create(Typeface.MONOSPACE, if (cfg.isBold) Typeface.BOLD else Typeface.NORMAL)
            "serif" -> Typeface.create(Typeface.SERIF, if (cfg.isBold) Typeface.BOLD else Typeface.NORMAL)
            else -> Typeface.create(Typeface.SANS_SERIF, if (cfg.isBold) Typeface.BOLD else Typeface.NORMAL)
        }

        if (!isAmbient && cfg.glowColorHex != null) {
            digitalClockPaint.setShadowLayer(10f * density, 0f, 0f, parseHex(cfg.glowColorHex, Color.CYAN))
        } else {
            digitalClockPaint.clearShadowLayer()
        }

        canvas.drawText(timeStr, x, y, digitalClockPaint)
        digitalClockPaint.clearShadowLayer()
    }

    private fun drawDate(
        canvas: Canvas,
        cfg: DateConfig,
        w: Float,
        h: Float,
        density: Float
    ) {
        val x = w * cfg.xRatio
        val y = h * cfg.yRatio

        val dateStr = when (cfg.format) {
            "d" -> "$dayOfMonth"
            "dd/MM" -> String.format(Locale.getDefault(), "%02d/%02d", dayOfMonth, timeCal.get(Calendar.MONTH) + 1)
            "EEE, MMM d" -> "$dayOfWeek, $month $dayOfMonth"
            else -> "$dayOfWeek $dayOfMonth"
        }

        datePaint.textSize = cfg.fontSizeSp * density
        datePaint.color = parseHex(cfg.colorHex, 0xFF94A3B8.toInt())

        if (cfg.hasFrame && !isAmbient) {
            val textWidth = datePaint.measureText(dateStr)
            val padX = 8f * density
            val padY = 4f * density
            val frameRect = RectF(
                x - textWidth / 2f - padX,
                y - datePaint.textSize - padY + (4f * density),
                x + textWidth / 2f + padX,
                y + padY
            )
            dateBoxPaint.style = Paint.Style.FILL
            dateBoxPaint.color = parseHex(cfg.frameColorHex, 0xFF1E293B.toInt())
            canvas.drawRoundRect(frameRect, 4f * density, 4f * density, dateBoxPaint)
        }

        canvas.drawText(dateStr, x, y, datePaint)
    }

    private fun drawComplications(
        canvas: Canvas,
        cfg: ComplicationsConfig,
        w: Float,
        h: Float,
        radius: Float,
        density: Float
    ) {
        // Heart Rate
        if (cfg.heartRate.enabled) {
            val hrX = w * cfg.heartRate.xRatio
            val hrY = h * cfg.heartRate.yRatio
            val hrR = radius * cfg.heartRate.radiusRatio
            drawSubdialWidget(
                canvas, hrX, hrY, hrR,
                currentVal = heartRate.toFloat(),
                maxVal = 180f,
                label = cfg.heartRate.label ?: "BPM",
                valText = if (heartRate > 0) "$heartRate" else "--",
                style = cfg.heartRate.style,
                colorHex = cfg.heartRate.colorHex,
                accentColorHex = cfg.heartRate.accentColorHex,
                density = density
            )
        }

        // Steps
        if (cfg.steps.enabled) {
            val stepX = w * cfg.steps.xRatio
            val stepY = h * cfg.steps.yRatio
            val stepR = radius * cfg.steps.radiusRatio
            drawSubdialWidget(
                canvas, stepX, stepY, stepR,
                currentVal = stepCount.toFloat(),
                maxVal = stepGoal.toFloat().coerceAtLeast(1f),
                label = cfg.steps.label ?: "STEPS",
                valText = if (stepCount >= 1000) String.format(Locale.getDefault(), "%.1fk", stepCount / 1000f) else "$stepCount",
                style = cfg.steps.style,
                colorHex = cfg.steps.colorHex,
                accentColorHex = cfg.steps.accentColorHex,
                density = density
            )
        }

        // Battery
        if (cfg.battery.enabled) {
            val batX = w * cfg.battery.xRatio
            val batY = h * cfg.battery.yRatio
            val batR = radius * cfg.battery.radiusRatio
            drawSubdialWidget(
                canvas, batX, batY, batR,
                currentVal = batteryLevel.toFloat(),
                maxVal = 100f,
                label = "BAT",
                valText = "$batteryLevel%",
                style = cfg.battery.style,
                colorHex = if (batteryLevel <= 20) "#EF4444" else cfg.battery.colorHex,
                accentColorHex = cfg.battery.accentColorHex,
                density = density
            )
        }
    }

    private fun drawSubdialWidget(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        currentVal: Float,
        maxVal: Float,
        label: String,
        valText: String,
        style: String,
        colorHex: String,
        accentColorHex: String,
        density: Float
    ) {
        val primaryColor = parseHex(colorHex, Color.CYAN)
        val trackColor = parseHex(accentColorHex, 0xFF1E293B.toInt())

        when (style) {
            "arc_gauge", "subdial" -> {
                val strokeW = 3.5f * density
                gaugeTrackPaint.color = trackColor
                gaugeTrackPaint.strokeWidth = strokeW
                gaugePaint.color = primaryColor
                gaugePaint.strokeWidth = strokeW

                arcRect.set(cx - r, cy - r, cx + r, cy + r)
                val startAngle = 135f
                val sweepAngle = 270f
                canvas.drawArc(arcRect, startAngle, sweepAngle, false, gaugeTrackPaint)

                val progressSweep = (currentVal / maxVal).coerceIn(0f, 1f) * sweepAngle
                if (progressSweep > 0) {
                    canvas.drawArc(arcRect, startAngle, progressSweep, false, gaugePaint)
                }

                // Value Text
                textPaint.textSize = (r * 0.58f)
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText(valText, cx, cy + (textPaint.textSize * 0.35f), textPaint)

                // Subtitle Label
                textPaint.textSize = (r * 0.32f)
                textPaint.color = parseHex("#94A3B8", Color.GRAY)
                textPaint.typeface = Typeface.DEFAULT
                canvas.drawText(label, cx, cy + r - (2f * density), textPaint)
            }
            else -> {
                // text_only
                textPaint.textSize = 12f * density
                textPaint.color = primaryColor
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText("$valText $label", cx, cy, textPaint)
            }
        }
    }

    private fun drawHands(
        canvas: Canvas,
        hands: HandsConfig,
        cx: Float,
        cy: Float,
        radius: Float,
        density: Float
    ) {
        val hourVal = hours + (minutes / 60f) + (seconds / 3600f)
        val minVal = minutes + (seconds / 60f)
        val secVal = if (hands.secondHand?.smoothSweep == true && !isAmbient) {
            seconds + (millis / 1000f)
        } else {
            seconds.toFloat()
        }

        val hourAngle = (hourVal * 30f)
        val minAngle = (minVal * 6f)
        val secAngle = (secVal * 6f)

        // 1. Hour Hand
        drawSingleHand(canvas, cx, cy, hourAngle, hands.hourHand, radius, density)

        // 2. Minute Hand
        drawSingleHand(canvas, cx, cy, minAngle, hands.minuteHand, radius, density)

        // 3. Second Hand (suppressed in AOD mode)
        if (!isAmbient || activePackage?.aod?.hideSecondsHand == false) {
            hands.secondHand?.let { drawSingleHand(canvas, cx, cy, secAngle, it, radius, density) }
        }

        // 4. Center Pin Cap
        capPaint.color = parseHex(hands.minuteHand.colorHex, Color.WHITE)
        canvas.drawCircle(cx, cy, (hands.hourHand.capRadius * density), capPaint)
    }

    private fun drawSingleHand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        angleDeg: Float,
        style: HandStyle,
        radius: Float,
        density: Float
    ) {
        canvas.save()
        canvas.rotate(angleDeg, cx, cy)

        val handLength = radius * style.lengthRatio
        val handWidth = style.widthDp * density
        val tailLength = handLength * style.tailRatio

        handPaint.color = parseHex(style.colorHex, Color.WHITE)
        handPaint.style = Paint.Style.FILL

        if (!isAmbient && style.glowColorHex != null) {
            handPaint.setShadowLayer(8f * density, 0f, 0f, parseHex(style.glowColorHex, Color.CYAN))
        } else {
            handPaint.clearShadowLayer()
        }

        handPath.reset()
        when (style.shape) {
            "sword" -> {
                handPath.moveTo(cx - handWidth / 2f, cy + tailLength)
                handPath.lineTo(cx + handWidth / 2f, cy + tailLength)
                handPath.lineTo(cx + handWidth * 0.7f, cy - handLength * 0.75f)
                handPath.lineTo(cx, cy - handLength)
                handPath.lineTo(cx - handWidth * 0.7f, cy - handLength * 0.75f)
                handPath.close()
                canvas.drawPath(handPath, handPaint)
            }
            "baton" -> {
                val handRect = RectF(
                    cx - handWidth / 2f,
                    cy - handLength,
                    cx + handWidth / 2f,
                    cy + tailLength
                )
                canvas.drawRoundRect(handRect, handWidth / 2f, handWidth / 2f, handPaint)
            }
            else -> {
                // needle
                handPaint.strokeWidth = handWidth
                handPaint.style = Paint.Style.STROKE
                canvas.drawLine(cx, cy + tailLength, cx, cy - handLength, handPaint)
                if (style.hasCounterweight) {
                    handPaint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy + tailLength * 0.7f, handWidth * 2f, handPaint)
                }
            }
        }

        canvas.restore()
        handPaint.clearShadowLayer()
    }

    private fun parseHex(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            fallback
        }
    }
}

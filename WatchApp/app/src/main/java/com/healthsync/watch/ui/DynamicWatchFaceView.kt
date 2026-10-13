package com.healthsync.watch.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.healthsync.watch.data.watchface.*
import com.healthsync.watch.sensor.HeartRatePolicy
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
    private var heartRate: Int = 0
    private var heartMeasuredAt = 0L
    private var heartRateUnverified: Boolean = false
    private var stepCount: Int = -1
    private var stepGoal: Int = 10000
    private var calories: Double = 0.0
    private var distanceKm: Double = 0.0
    private var batteryLevel: Int = -1
    private var isBluetoothConnected: Boolean = false

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
    private val animStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val animFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val animPath = Path()

    // Animation & Interactive states
    private var animProgress = 0f
    private var lastAnimTime = 0L
    private var currentPaletteIndex = 0
    private var isMinimalMode = false
    private var rippleX = 0f
    private var rippleY = 0f
    private var rippleRadius = 0f
    private var rippleAlpha = 0

    private val handPath = Path()
    private val arcRect = RectF()
    private var cachedBgBitmap: Bitmap? = null

    init {
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Loads and activates a dynamic watch face schema. */
    fun loadFace(pkg: HswfPackage) {
        activePackage = pkg
        cachedBgBitmap?.recycle()
        cachedBgBitmap = null
        animProgress = 0f
        lastAnimTime = 0L
        currentPaletteIndex = 0
        isMinimalMode = false
        rippleAlpha = 0

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

    fun setHeartRateTime(time: Long) { heartMeasuredAt = time }
    fun setHeartRateUnverified(value: Boolean) {
        heartRateUnverified = value
        if (!isAmbient) invalidate()
    }

    fun setBluetoothConnected(connected: Boolean) {
        isBluetoothConnected = connected
        if (!isAmbient) invalidate()
    }

    fun setBatteryLevel(level: Int) {
        batteryLevel = level.takeIf { it in 0..100 } ?: -1
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

    private var isPowerSaver = false

    fun setPowerSaverMode(enabled: Boolean) {
        if (isPowerSaver == enabled) return
        isPowerSaver = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pkg = activePackage ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val now = SystemClock.uptimeMillis()
        val hasAnimation = !isAmbient && !isPowerSaver && pkg.animation != null && pkg.animation.type != "none"
        if (hasAnimation) {
            val dt = if (lastAnimTime > 0L) (now - lastAnimTime).coerceIn(0L, 100L) else 16L
            lastAnimTime = now
            animProgress = (animProgress + (dt / 1000f) * (pkg.animation?.speedRatio ?: 1f)) % 1000f
        } else {
            lastAnimTime = now
        }

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(cx, cy)
        val density = resources.displayMetrics.density

        // 1. Draw Background
        drawBackground(canvas, pkg.background, w, h, cx, cy, radius)

        // 2. Draw Animation Layer (Dynamic effects)
        pkg.animation?.let { if (it.type != "none") drawAnimationLayer(canvas, it, w, h, cx, cy, radius, density) }

        // 3. Draw Dial Ticks & Numbers
        drawDial(canvas, pkg.dial, cx, cy, radius, density)

        // 4. Draw Digital Clock (if configured)
        pkg.digitalClock?.let { if (it.enabled) drawDigitalClock(canvas, it, w, h, density) }

        // 5. Draw Date Window (if configured)
        pkg.date?.let { if (it.enabled) drawDate(canvas, it, w, h, density) }

        // 6. Draw Complications (hidden in minimal glance mode)
        if (!isMinimalMode) {
            drawComplications(canvas, pkg.complications, w, h, radius, density)
        }

        // 7. Draw Analog Hands (if configured)
        pkg.hands?.let { drawHands(canvas, it, cx, cy, radius, density) }

        // 8. Draw Touch Ripple Layer
        if (rippleAlpha > 0) {
            drawRipple(canvas, density)
        }

        if (hasAnimation || rippleAlpha > 0) {
            postInvalidateOnAnimation()
        }
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
                label = if (heartRate > 0) HeartRatePolicy.complicationUnit(heartMeasuredAt, System.currentTimeMillis()) else "BPM",
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
                valText = StepDisplayLabels.display(if(stepCount >= 1000) String.format(Locale.getDefault(), "%.1fk", stepCount / 1000f) else "$stepCount",stepCount),
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
                valText = if (batteryLevel >= 0) "$batteryLevel%" else "--",
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val pkg = activePackage ?: return super.onTouchEvent(event)
            val inter = pkg.interactive ?: return super.onTouchEvent(event)
            if (inter.hapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            when (inter.tapEffect) {
                "palette_cycle" -> {
                    if (inter.colorPalettes.isNotEmpty()) {
                        currentPaletteIndex = (currentPaletteIndex + 1) % inter.colorPalettes.size
                        invalidate()
                        return true
                    }
                }
                "mode_toggle" -> {
                    isMinimalMode = !isMinimalMode
                    invalidate()
                    return true
                }
                "ripple_burst" -> {
                    rippleX = event.x
                    rippleY = event.y
                    rippleRadius = 10f
                    rippleAlpha = 220
                    postInvalidateOnAnimation()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun drawAnimationLayer(
        canvas: Canvas,
        anim: AnimationConfig,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        radius: Float,
        density: Float
    ) {
        if (isAmbient) return
        val activeHex = activePackage?.interactive?.colorPalettes?.getOrNull(currentPaletteIndex)
        val animColor = parseHex(activeHex ?: anim.colorHex, Color.CYAN)
        val animX = w * anim.xRatio
        val animY = h * anim.yRatio
        val scale = anim.scaleRatio.coerceIn(0.2f, 2.5f)

        when (anim.type) {
            "pulse_ecg" -> drawEcgWave(canvas, animX, animY, radius * 0.95f * scale, animColor, density)
            "radar_sweep" -> drawRadarSweep(canvas, cx, cy, radius * 0.88f * scale, animColor, density)
            "equalizer_bars" -> drawEqualizerBars(canvas, animX, animY, radius * 0.75f * scale, animColor, density)
            "liquid_wave" -> drawLiquidWave(canvas, cx, cy, radius * 0.85f * scale, animColor, density)
            "tourbillon_spin" -> drawTourbillon(canvas, animX, animY, radius * 0.35f * scale, animColor, density)
            "orbital_space" -> drawOrbitalSpace(canvas, cx, cy, radius * 0.8f * scale, animColor, density)
            "aurora_shimmer" -> drawAuroraShimmer(canvas, cx, cy, radius * 0.9f * scale, animColor, density)
        }
    }

    private fun drawEcgWave(canvas: Canvas, x: Float, y: Float, width: Float, color: Int, density: Float) {
        animStrokePaint.color = color
        animStrokePaint.strokeWidth = 2.5f * density
        animPath.reset()

        val halfW = width / 2f
        val startX = x - halfW
        val endX = x + halfW
        val baseline = y
        val waveLen = width * 0.45f
        val offset = (animProgress * 150f) % (width + waveLen) - waveLen

        animPath.moveTo(startX, baseline)

        var curX = startX
        while (curX <= endX) {
            val relX = curX - (startX + offset)
            val waveY = if (relX in 0f..waveLen) {
                val p = relX / waveLen
                when {
                    p < 0.2f -> baseline
                    p < 0.35f -> baseline - (sin((p - 0.2f) / 0.15f * Math.PI) * 7f * density).toFloat()
                    p < 0.45f -> baseline + (sin((p - 0.35f) / 0.10f * Math.PI) * 6f * density).toFloat()
                    p < 0.55f -> baseline - (sin((p - 0.45f) / 0.10f * Math.PI) * 30f * density).toFloat()
                    p < 0.65f -> baseline + (sin((p - 0.55f) / 0.10f * Math.PI) * 11f * density).toFloat()
                    p < 0.85f -> baseline - (sin((p - 0.65f) / 0.20f * Math.PI) * 10f * density).toFloat()
                    else -> baseline
                }
            } else baseline

            animPath.lineTo(curX, waveY)
            curX += 4f * density
        }

        canvas.drawPath(animPath, animStrokePaint)

        // Glowing pulse head
        val headX = (startX + offset + waveLen * 0.55f).coerceIn(startX, endX)
        animFillPaint.color = Color.WHITE
        canvas.drawCircle(headX, baseline, 3f * density, animFillPaint)
        animFillPaint.color = (color and 0x00FFFFFF) or 0x55000000
        canvas.drawCircle(headX, baseline, 6.5f * density, animFillPaint)
    }

    private fun drawRadarSweep(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, density: Float) {
        val sweepAngle = (animProgress * 90f) % 360f

        animStrokePaint.color = (color and 0x00FFFFFF) or 0x22000000
        animStrokePaint.strokeWidth = 1.2f * density
        canvas.drawCircle(cx, cy, radius * 0.33f, animStrokePaint)
        canvas.drawCircle(cx, cy, radius * 0.66f, animStrokePaint)
        canvas.drawCircle(cx, cy, radius, animStrokePaint)

        canvas.save()
        canvas.rotate(sweepAngle, cx, cy)
        animStrokePaint.color = color
        animStrokePaint.strokeWidth = 2.5f * density
        canvas.drawLine(cx, cy, cx, cy - radius, animStrokePaint)

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        animFillPaint.shader = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, (color and 0x00FFFFFF) or 0x40000000), floatArrayOf(0.75f, 1f))
        canvas.drawArc(arcRect, -90f, 90f, true, animFillPaint)
        animFillPaint.shader = null
        canvas.restore()

        val blipAlpha = ((sin(animProgress * 6f) + 1f) * 0.5f * 255).toInt().coerceIn(40, 255)
        animFillPaint.color = (color and 0x00FFFFFF) or (blipAlpha shl 24)
        val blip1X = cx + radius * 0.52f
        val blip1Y = cy - radius * 0.38f
        canvas.drawCircle(blip1X, blip1Y, 3.5f * density, animFillPaint)
        val blip2X = cx - radius * 0.42f
        val blip2Y = cy + radius * 0.48f
        canvas.drawCircle(blip2X, blip2Y, 2.8f * density, animFillPaint)
    }

    private fun drawEqualizerBars(canvas: Canvas, x: Float, y: Float, width: Float, color: Int, density: Float) {
        val barCount = 10
        val barWidth = (width / barCount) * 0.65f
        val spacing = (width / barCount) * 0.35f
        val startX = x - width / 2f
        val maxH = 26f * density

        animFillPaint.color = color
        for (i in 0 until barCount) {
            val bx = startX + i * (barWidth + spacing)
            val hFactor = ((sin(animProgress * 7f + i * 0.85f) + 1f) * 0.5f * 0.85f + 0.15f).toFloat()
            val barH = maxH * hFactor
            val top = y - barH / 2f
            val bottom = y + barH / 2f
            canvas.drawRoundRect(RectF(bx, top, bx + barWidth, bottom), barWidth / 2f, barWidth / 2f, animFillPaint)
        }
    }

    private fun drawLiquidWave(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, density: Float) {
        val fluidLevel = cy + radius * 0.25f
        animPath.reset()
        animPath.moveTo(cx - radius, cy + radius)
        animPath.lineTo(cx - radius, fluidLevel)

        val steps = 24
        val stepW = (radius * 2f) / steps
        for (i in 0..steps) {
            val px = cx - radius + i * stepW
            val py = fluidLevel + (sin((i.toFloat() / steps * 2.5 * Math.PI) + animProgress * 5f) * 5f * density).toFloat()
            animPath.lineTo(px, py)
        }
        animPath.lineTo(cx + radius, cy + radius)
        animPath.close()

        canvas.save()
        val clipP = Path().apply { addCircle(cx, cy, radius * 0.95f, Path.Direction.CW) }
        canvas.clipPath(clipP)
        animFillPaint.color = (color and 0x00FFFFFF) or 0x45000000
        canvas.drawPath(animPath, animFillPaint)
        canvas.restore()
    }

    private fun drawTourbillon(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, density: Float) {
        val rotAngle = (animProgress * 150f) % 360f

        animStrokePaint.color = 0xFFD4AF37.toInt()
        animStrokePaint.strokeWidth = 2.5f * density
        canvas.drawCircle(x, y, radius, animStrokePaint)

        canvas.save()
        canvas.rotate(rotAngle, x, y)
        animStrokePaint.color = color
        animStrokePaint.strokeWidth = 2f * density
        canvas.drawCircle(x, y, radius * 0.8f, animStrokePaint)

        for (i in 0..2) {
            val ang = i * 120.0
            val rad = Math.toRadians(ang)
            val ex = (x + cos(rad) * radius * 0.8f).toFloat()
            val ey = (y + sin(rad) * radius * 0.8f).toFloat()
            canvas.drawLine(x, y, ex, ey, animStrokePaint)
            animFillPaint.color = 0xFFD4AF37.toInt()
            canvas.drawCircle(ex, ey, 2.2f * density, animFillPaint)
        }
        canvas.restore()

        animFillPaint.color = 0xFFE11D48.toInt()
        canvas.drawCircle(x, y, 4f * density, animFillPaint)
    }

    private fun drawOrbitalSpace(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, density: Float) {
        animStrokePaint.color = (color and 0x00FFFFFF) or 0x22000000
        animStrokePaint.strokeWidth = 1.2f * density
        canvas.drawCircle(cx, cy, radius * 0.75f, animStrokePaint)

        val earthAngle = Math.toRadians((animProgress * 25.0) % 360.0)
        val earthX = (cx + cos(earthAngle) * radius * 0.75f).toFloat()
        val earthY = (cy + sin(earthAngle) * radius * 0.75f).toFloat()

        animFillPaint.color = 0xFF38BDF8.toInt()
        canvas.drawCircle(earthX, earthY, 6f * density, animFillPaint)

        val moonAngle = Math.toRadians((animProgress * 100.0) % 360.0)
        val moonX = (earthX + cos(moonAngle) * 14f * density).toFloat()
        val moonY = (earthY + sin(moonAngle) * 14f * density).toFloat()
        animFillPaint.color = 0xFFE2E8F0.toInt()
        canvas.drawCircle(moonX, moonY, 2.2f * density, animFillPaint)
    }

    private fun drawAuroraShimmer(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, density: Float) {
        val auroraTop = cy - radius * 0.75f
        animPath.reset()
        animPath.moveTo(cx - radius * 0.8f, cy)
        for (i in 0..10) {
            val px = cx - radius * 0.8f + i * (radius * 1.6f / 10f)
            val py = auroraTop + (sin((i * 0.5) + animProgress * 2.5) * 18f * density).toFloat()
            animPath.lineTo(px, py)
        }
        animPath.lineTo(cx + radius * 0.8f, cy)
        animPath.close()

        animFillPaint.color = (color and 0x00FFFFFF) or 0x30000000
        canvas.drawPath(animPath, animFillPaint)
    }

    private fun drawRipple(canvas: Canvas, density: Float) {
        animStrokePaint.color = (0x00FFFFFF) or (rippleAlpha shl 24)
        animStrokePaint.strokeWidth = 2.5f * density
        canvas.drawCircle(rippleX, rippleY, rippleRadius, animStrokePaint)
        rippleRadius += 4.5f * density
        rippleAlpha = (rippleAlpha - 14).coerceAtLeast(0)
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


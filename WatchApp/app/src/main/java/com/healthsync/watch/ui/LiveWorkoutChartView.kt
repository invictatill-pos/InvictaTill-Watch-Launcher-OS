package com.healthsync.watch.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Custom View that draws a live, scrolling area chart (e.g. for Heart Rate),
 * mimicking the Samsung Galaxy Watch Ultra elevation/heart rate graph.
 */
class LiveWorkoutChartView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(ctx, attrs, defStyle) {

    private val dataPoints = mutableListOf<Float>()
    private val maxDataPoints = 60 // Keep last 60 points
    private var maxValue = 200f
    private var minValue = 40f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#00E5FF") // Neon Cyan
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#33FFFFFF")
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun addDataPoint(value: Float) {
        dataPoints.add(value)
        if (dataPoints.size > maxDataPoints) {
            dataPoints.removeAt(0)
        }
        
        // Dynamically adjust bounds for a cooler effect
        val currentMax = dataPoints.maxOrNull() ?: 200f
        val currentMin = dataPoints.minOrNull() ?: 40f
        maxValue = Math.max(currentMax + 20f, 150f)
        minValue = Math.min(currentMin - 20f, 60f)

        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        areaPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#8800E5FF"),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        if (dataPoints.isEmpty()) return

        // Draw faint vertical grid lines to look technical
        val gridSegments = 4
        for (i in 0..gridSegments) {
            val x = w * (i.toFloat() / gridSegments)
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }
        
        // Draw horizontal grid line at the bottom
        canvas.drawLine(0f, h - 2f, w, h - 2f, gridPaint)

        val path = Path()
        val areaPath = Path()

        val stepX = w / (maxDataPoints - 1)
        val valueRange = maxValue - minValue

        // Starting point for area (bottom left of graph)
        var startX = w - (dataPoints.size - 1) * stepX
        if (startX < 0) startX = 0f
        
        areaPath.moveTo(startX, h)

        for (i in dataPoints.indices) {
            val x = w - ((dataPoints.size - 1 - i) * stepX)
            
            // Invert Y axis (0 is top)
            val normalizedY = 1f - ((dataPoints[i] - minValue) / valueRange).coerceIn(0f, 1f)
            val y = normalizedY * h

            if (i == 0) {
                path.moveTo(x, y)
                areaPath.lineTo(x, y)
            } else {
                // Smooth bezier curve would be better, but straight lines with ROUND join is okay and highly performant
                path.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }

        // Close the area path to the bottom right
        areaPath.lineTo(w, h)
        areaPath.close()

        canvas.drawPath(areaPath, areaPaint)
        canvas.drawPath(path, linePaint)
        
        // Draw a glowing dot at the most recent point
        if (dataPoints.isNotEmpty()) {
            val lastY = 1f - ((dataPoints.last() - minValue) / valueRange).coerceIn(0f, 1f)
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(w, lastY * h, 6f, dotPaint)
            dotPaint.color = Color.parseColor("#00E5FF")
            dotPaint.style = Paint.Style.STROKE
            dotPaint.strokeWidth = 3f
            canvas.drawCircle(w, lastY * h, 10f, dotPaint)
        }
    }
}

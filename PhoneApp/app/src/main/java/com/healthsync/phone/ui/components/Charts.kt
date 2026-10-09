package com.healthsync.phone.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RadarChart(
    modifier: Modifier = Modifier,
    values: List<Float>, // Values between 0f and 1f
    labels: List<String>,
    color: Color = Color(0xFF00E676)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) / 2f * 0.75f
        val center = Offset(size.width / 2f, size.height / 2f)
        val sides = values.size
        val angleStep = (2 * Math.PI / sides).toFloat()

        // Draw web/grid
        val webSteps = 4
        for (i in 1..webSteps) {
            val stepRadius = radius * (i.toFloat() / webSteps)
            val path = Path()
            for (j in 0 until sides) {
                val angle = j * angleStep - (Math.PI / 2).toFloat()
                val x = center.x + stepRadius * cos(angle)
                val y = center.y + stepRadius * sin(angle)
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path = path, color = Color.LightGray, style = Stroke(width = 1.dp.toPx()))
        }

        // Draw axes and labels
        val textPaint = Paint().apply {
            this.color = android.graphics.Color.DKGRAY
            this.textSize = 28f
            this.textAlign = Paint.Align.CENTER
            this.isFakeBoldText = true
        }
        for (j in 0 until sides) {
            val angle = j * angleStep - (Math.PI / 2).toFloat()
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            drawLine(
                color = Color.LightGray,
                start = center,
                end = Offset(x, y),
                strokeWidth = 1.dp.toPx()
            )
            
            // Draw labels
            val labelX = center.x + (radius + 40f) * cos(angle)
            val labelY = center.y + (radius + 40f) * sin(angle)
            drawContext.canvas.nativeCanvas.drawText(labels[j], labelX, labelY, textPaint)
        }

        // Draw data polygon
        val dataPath = Path()
        for (j in 0 until sides) {
            val angle = j * angleStep - (Math.PI / 2).toFloat()
            val valueRadius = radius * values[j]
            val x = center.x + valueRadius * cos(angle)
            val y = center.y + valueRadius * sin(angle)
            if (j == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()

        drawPath(path = dataPath, color = color.copy(alpha = 0.4f), style = Fill)
        drawPath(path = dataPath, color = color, style = Stroke(width = 3.dp.toPx()))
        
        // Draw dots at vertices
        for (j in 0 until sides) {
            val angle = j * angleStep - (Math.PI / 2).toFloat()
            val valueRadius = radius * values[j]
            val x = center.x + valueRadius * cos(angle)
            val y = center.y + valueRadius * sin(angle)
            drawCircle(color = color, radius = 6.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
fun SimpleAreaGraph(
    modifier: Modifier = Modifier,
    data: List<Float>,
    lineColor: Color = Color(0xFF2979FF)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (data.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        val stepX = width / (data.size - 1).coerceAtLeast(1)
        
        val maxVal = data.maxOrNull() ?: 100f
        val minVal = data.minOrNull() ?: 0f
        val range = maxVal - minVal
        
        val path = Path()
        val areaPath = Path()
        
        areaPath.moveTo(0f, height)
        
        for (i in data.indices) {
            val x = i * stepX
            val normalizedY = if (range == 0f) 0.5f else 1f - ((data[i] - minVal) / range)
            val y = normalizedY * height
            
            if (i == 0) {
                path.moveTo(x, y)
                areaPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        
        areaPath.lineTo(width, height)
        areaPath.close()
        
        drawPath(
            path = areaPath,
            color = lineColor.copy(alpha = 0.2f),
            style = Fill
        )
        
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

package com.healthsync.phone.ui.watchface

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.healthsync.phone.data.watchface.FaceLayer
import com.healthsync.phone.data.watchface.HswfPackage
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * An editable 320-unit design viewport. History belongs to the caller: one start/end pair
 * surrounds every drag, while all intermediate changes only update that current gesture.
 * Hit testing inverse-rotates the pointer and uses a padded layer rectangle, so transparent
 * corners of an ellipse and empty space around text intentionally remain easy to select.
 */
@Composable
fun StudioCanvas(
    pkg: HswfPackage,
    selectedLayerId: String?,
    onSelectLayer: (String?) -> Unit,
    onLayerChange: (FaceLayer) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    showGrid: Boolean,
    snapToGrid: Boolean,
    isAmbient: Boolean,
    hours: Int,
    minutes: Int,
    seconds: Int,
    batteryPct: Int,
    steps: Int,
    heartRate: Int,
    modifier: Modifier = Modifier,
    previewShape: String = "round"
) {
    val currentPackage by rememberUpdatedState(pkg)
    val currentAmbient by rememberUpdatedState(isAmbient)
    val currentSnap by rememberUpdatedState(snapToGrid)
    val currentShape by rememberUpdatedState(previewShape)
    val selectLayer by rememberUpdatedState(onSelectLayer)
    val changeLayer by rememberUpdatedState(onLayerChange)
    val gestureStart by rememberUpdatedState(onGestureStart)
    val gestureEnd by rememberUpdatedState(onGestureEnd)
    val roundPreview = previewShape != "square"

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(if (roundPreview) CircleShape else RectangleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val viewport = Size(size.width.toFloat(), size.height.toFloat())
                    val touched = hitLayer(currentPackage, down.position, viewport, currentAmbient, currentShape)
                    if (touched == null || touched.locked) {
                        if (waitForUpOrCancellation() != null) selectLayer(touched?.id)
                        return@awaitEachGesture
                    }

                    // Let surrounding scrolling work until the pointer passes touch slop.
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop == null) {
                        if (currentEvent.changes.none { it.pressed || it.isConsumed }) selectLayer(touched.id)
                        return@awaitEachGesture
                    }

                    gestureStart()
                    selectLayer(touched.id)
                    var center = Offset(touched.xRatio * viewport.width, touched.yRatio * viewport.height)
                    fun moveLayer(delta: Offset) {
                        center += delta
                        val x = (center.x / viewport.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val y = (center.y / viewport.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        changeLayer(touched.copy(
                            xRatio = if (currentSnap) snapRatio(x) else x,
                            yRatio = if (currentSnap) snapRatio(y) else y
                        ))
                    }
                    try {
                        moveLayer(slop.position - down.position)
                        drag(slop.id) { change ->
                            moveLayer(change.positionChange())
                            change.consume()
                        }
                    } finally {
                        gestureEnd()
                    }
                }
            }
    ) {
        DynamicFacePreview(
            pkg = pkg,
            modifier = Modifier.fillMaxSize(),
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            heartRate = heartRate,
            steps = steps,
            battery = batteryPct,
            ambientLayout = isAmbient,
            roundPreview = roundPreview
        )

        Canvas(Modifier.fillMaxSize()) {
            val designScale = min(size.width, size.height) / 320f
            if (showGrid) {
                for (i in 1 until 20) {
                    val ratio = i / 20f
                    val major = i % 5 == 0
                    val color = Color.White.copy(alpha = if (major) .22f else .08f)
                    drawLine(color, Offset(size.width * ratio, 0f), Offset(size.width * ratio, size.height), designScale)
                    drawLine(color, Offset(0f, size.height * ratio), Offset(size.width, size.height * ratio), designScale)
                }
                drawCircle(
                    Color(0xFF38BDF8).copy(alpha = .5f),
                    radius = min(size.width, size.height) * .46f,
                    center = center,
                    style = Stroke(designScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * designScale, 4f * designScale)))
                )
            }
            val selected = pkg.layers.firstOrNull {
                it.id == selectedLayerId && it.visible && (!isAmbient || it.showInAmbient)
            }?.normalized() ?: return@Canvas
            val layerCenter = Offset(selected.xRatio * size.width, selected.yRatio * size.height)
            val bounds = layerSize(selected, size)
            val padding = 4f * designScale
            val outlineSize = Size(bounds.width + padding * 2f, bounds.height + padding * 2f)
            val topLeft = layerCenter - Offset(outlineSize.width / 2f, outlineSize.height / 2f)
            val color = if (selected.locked) Color(0xFFFBBF24) else Color(0xFF38BDF8)
            withTransform({ rotate(selected.rotationDegrees, layerCenter) }) {
                drawRect(color, topLeft, outlineSize, style = Stroke(1.5f * designScale))
                listOf(
                    topLeft,
                    topLeft + Offset(outlineSize.width, 0f),
                    topLeft + Offset(0f, outlineSize.height),
                    topLeft + Offset(outlineSize.width, outlineSize.height)
                ).forEach { drawCircle(color, 3f * designScale, it) }
                drawLine(color, layerCenter - Offset(4f * designScale, 0f), layerCenter + Offset(4f * designScale, 0f), designScale)
                drawLine(color, layerCenter - Offset(0f, 4f * designScale), layerCenter + Offset(0f, 4f * designScale), designScale)
            }
        }
    }
}

private fun snapRatio(value: Float): Float = (round(value / .05f) * .05f).coerceIn(0f, 1f)

private fun hitLayer(pkg: HswfPackage, point: Offset, viewport: Size, ambient: Boolean, previewShape: String): FaceLayer? {
    if (viewport.width <= 0f || viewport.height <= 0f) return null
    if (previewShape != "square") {
        val radius = min(viewport.width, viewport.height) / 2f
        val delta = point - Offset(viewport.width / 2f, viewport.height / 2f)
        if (delta.getDistance() > radius) return null
    }
    val padding = 6f * min(viewport.width, viewport.height) / 320f
    return pkg.layers.asReversed().firstOrNull { original ->
        val layer = original.normalized()
        if (!layer.visible || (ambient && !layer.showInAmbient)) return@firstOrNull false
        val delta = point - Offset(layer.xRatio * viewport.width, layer.yRatio * viewport.height)
        val angle = Math.toRadians(-layer.rotationDegrees.toDouble())
        val local = Offset(
            (delta.x * cos(angle) - delta.y * sin(angle)).toFloat(),
            (delta.x * sin(angle) + delta.y * cos(angle)).toFloat()
        )
        val bounds = layerSize(layer, viewport)
        kotlin.math.abs(local.x) <= bounds.width / 2f + padding && kotlin.math.abs(local.y) <= bounds.height / 2f + padding
    }?.normalized()
}

private fun layerSize(layer: FaceLayer, viewport: Size): Size {
    val width = layer.widthRatio * viewport.width
    val height = layer.heightRatio * viewport.height
    if (layer.type == "line") return Size(width + height, height)
    if (layer.type != "text") return Size(width, height)
    val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        textSize = layer.fontSizeSp * min(viewport.width, viewport.height) / 320f
        typeface = Typeface.create(
            when (layer.fontFamily) {
                "serif" -> Typeface.SERIF
                "monospace" -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            },
            if (layer.isBold) Typeface.BOLD else Typeface.NORMAL
        )
    }
    return Size(max(width, paint.measureText(layer.text)), max(height, paint.fontMetrics.descent - paint.fontMetrics.ascent))
}

package com.healthsync.phone.ui.watchface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthsync.phone.data.watchface.*
import com.healthsync.phone.ui.theme.*
import java.util.Locale
import java.util.UUID

internal class StudioGesture(val begin: () -> Unit, val end: () -> Unit)
internal val LocalStudioGesture = staticCompositionLocalOf { StudioGesture({}, {}) }

@Composable
private fun StudioHeading(title: String, hint: String = "") {
    Text(title, color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    if (hint.isNotEmpty()) Text(hint, color = TextDim, fontSize = 12.sp)
}

@Composable
private fun StudioChoices(value: String, choices: List<Pair<String, String>>, change: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (key, label) -> FilterChip(selected = key == value, onClick = { change(key) }, label = { Text(label, fontSize = 12.sp) }) }
    }
}

@Composable
private fun StudioToggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextWhite, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = value, onCheckedChange = change)
    }
}

@Composable
internal fun StudioSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    val gesture = LocalStudioGesture.current
    val focusManager = LocalFocusManager.current
    var sliding by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    fun formatted(number: Float) = String.format(Locale.US, "%.2f", number).trimEnd('0').trimEnd('.')
    var input by remember { mutableStateOf(formatted(value)) }
    LaunchedEffect(value, focused) { if (!focused) input = formatted(value) }
    DisposableEffect(Unit) { onDispose { if (sliding) gesture.end() } }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            val number = input.toFloatOrNull()
            OutlinedTextField(
                value = input, onValueChange = {
                    input = it.take(12)
                    input.toFloatOrNull()?.takeIf { number -> number.isFinite() && number in range }?.let(change)
                },
                modifier = Modifier.width(100.dp).onFocusChanged {
                    focused = it.isFocused
                    if (!focused) input = formatted(value)
                },
                singleLine = true, isError = focused && (number == null || !number.isFinite() || number !in range),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                shape = RoundedCornerShape(10.dp)
            )
        }
        Slider(value = value.coerceIn(range), onValueChange = {
            if (!sliding) { focusManager.clearFocus(); gesture.begin(); sliding = true }
            change(it)
        }, onValueChangeFinished = { if (sliding) { gesture.end(); sliding = false } }, valueRange = range)
    }
}

@Composable
private fun StudioColor(label: String, hex: String, change: (String) -> Unit) {
    var input by remember(hex) { mutableStateOf(hex) }
    val valid = Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(input)
    OutlinedTextField(value = input, onValueChange = {
        input = it.take(9)
        if (Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(input)) change(input.uppercase())
    }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, isError = !valid,
        leadingIcon = {
            Box(Modifier.size(20.dp).clip(CircleShape).background(runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)))
        }, supportingText = { Text("Hex color • #RRGGBB or #AARRGGBB", fontSize = 10.sp) })
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("#FFFFFF", "#00E5FF", "#D4AF37", "#FF3366", "#10B981", "#A855F7", "#F59E0B", "#0A0D12").forEach { color ->
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(color)))
                .border(2.dp, if (hex.equals(color, true)) AccentCyan else BorderSubtle, CircleShape)
                .semantics { contentDescription = "Choose color " + color }.clickable { change(color) })
        }
    }
}

@Composable
internal fun StudioProjectControl(pkg: HswfPackage, templates: List<HswfPackage>, change: (HswfPackage) -> Unit, blank: (String) -> Unit) {
    StudioHeading("Your project", "Start with a clean canvas or a preset. Every change can be undone.")
    OutlinedTextField(pkg.name, { change(pkg.copy(name = it.take(100))) }, Modifier.fillMaxWidth(), label = { Text("Face name") }, singleLine = true)
    OutlinedTextField(pkg.author, { change(pkg.copy(author = it.take(100))) }, Modifier.fillMaxWidth(), label = { Text("Creator") }, singleLine = true)
    OutlinedTextField(pkg.description, { change(pkg.copy(description = it.take(2000))) }, Modifier.fillMaxWidth(), label = { Text("Description") }, maxLines = 4)
    Text("New blank project", color = TextDim, fontSize = 12.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("analog", "digital", "hybrid").forEach { type ->
            OutlinedButton(onClick = { blank(type) }) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(type.replaceFirstChar { it.uppercase() }) }
        }
    }
    HorizontalDivider(color = BorderSubtle)
    StudioHeading("Starter collection", "Apply a foundation, then refine every detail.")
    templates.forEach { template ->
        Surface(onClick = {
            change(template.copy(id = pkg.id, name = template.name + " Custom", author = pkg.author))
        }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderSubtle), color = BgDeep) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DynamicFacePreview(template, Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(template.name, color = TextWhite, fontWeight = FontWeight.SemiBold)
                    Text(template.description, color = TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ChevronRight, null, tint = AccentCyan)
            }
        }
    }
}

@Composable
internal fun StudioLayersControl(pkg: HswfPackage, selectedId: String?, select: (String?) -> Unit, change: (HswfPackage) -> Unit) {
    fun update(layer: FaceLayer) = change(pkg.copy(layers = pkg.layers.map { if (it.id == layer.id) layer else it }))
    StudioHeading("Layer composition", "Add text and graphics. Top rows appear above lower rows and the clock.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("text" to "Text", "rectangle" to "Panel", "circle" to "Circle", "line" to "Line").forEach { (type, label) ->
            OutlinedButton(enabled = pkg.layers.size < 128, onClick = {
                val layer = FaceLayer(
                    id = "layer_" + UUID.randomUUID(), name = label + " " + (pkg.layers.size + 1),
                    type = type, text = "YOUR DESIGN", yRatio = 0.75f,
                    widthRatio = if (type == "circle") 0.2f else 0.45f,
                    heightRatio = when (type) { "line" -> 0.01f; "circle" -> 0.2f; else -> 0.12f },
                    colorHex = if (type == "text") "#FFFFFF" else "#235E49"
                )
                change(pkg.copy(layers = pkg.layers + layer)); select(layer.id)
            }) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(label) }
        }
    }
    if (pkg.layers.isEmpty()) Text("Your canvas has no custom layers. Add a label, panel, circle, or divider above.", color = TextDim, fontSize = 13.sp)
    pkg.layers.asReversed().forEach { layer ->
        Surface(onClick = { select(layer.id) }, color = if (selectedId == layer.id) AccentCyan.copy(alpha = 0.08f) else BgDeep,
            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (selectedId == layer.id) AccentCyan else BorderSubtle)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (layer.type == "text") Icons.Default.TextFields else Icons.Default.Category, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f).padding(8.dp)) {
                    Text(layer.name, color = TextWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(layer.type + if (layer.showInAmbient) " • active + ambient" else " • active", color = TextDim, fontSize = 10.sp)
                }
                IconButton(onClick = { update(layer.copy(visible = !layer.visible)) }) {
                    Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (layer.visible) "Hide layer" else "Show layer", tint = TextDim, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { update(layer.copy(locked = !layer.locked)) }) {
                    Icon(if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen, if (layer.locked) "Unlock layer" else "Lock layer", tint = TextDim, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    val layer = pkg.layers.find { it.id == selectedId }
    if (layer != null) {
        HorizontalDivider(color = BorderSubtle)
        StudioHeading("Layer inspector", if (layer.locked) "Unlock this layer to edit its appearance and position." else "Position values use the full canvas. X and Y anchor the center.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val index = pkg.layers.indexOf(layer)
            OutlinedButton(enabled = !layer.locked && index < pkg.layers.lastIndex, onClick = {
                val layers = pkg.layers.toMutableList(); java.util.Collections.swap(layers, index, index + 1); change(pkg.copy(layers = layers))
            }) { Text("Forward") }
            OutlinedButton(enabled = !layer.locked && index > 0, onClick = {
                val layers = pkg.layers.toMutableList(); java.util.Collections.swap(layers, index, index - 1); change(pkg.copy(layers = layers))
            }) { Text("Backward") }
            OutlinedButton(enabled = pkg.layers.size < 128, onClick = {
                val copy = layer.copy(id = "layer_" + UUID.randomUUID(), name = (layer.name + " Copy").take(80), locked = false, xRatio = (layer.xRatio + 0.05f).coerceAtMost(1f))
                change(pkg.copy(layers = pkg.layers + copy)); select(copy.id)
            }) { Icon(Icons.Default.ContentCopy, "Duplicate layer", Modifier.size(18.dp)) }
            OutlinedButton(enabled = !layer.locked, onClick = { change(pkg.copy(layers = pkg.layers.filter { it.id != layer.id })); select(null) }) {
                Icon(Icons.Default.DeleteOutline, "Delete layer", tint = StatusError, modifier = Modifier.size(18.dp))
            }
        }
        if (!layer.locked) {
            OutlinedTextField(layer.name, { update(layer.copy(name = it.take(80))) }, Modifier.fillMaxWidth(), label = { Text("Layer name") }, singleLine = true)
            if (layer.type == "text") {
                OutlinedTextField(layer.text, { update(layer.copy(text = it.take(256))) }, Modifier.fillMaxWidth(), label = { Text("Text") }, singleLine = true)
                StudioChoices(layer.fontFamily, listOf("sans" to "Sans", "serif" to "Serif", "monospace" to "Mono")) { update(layer.copy(fontFamily = it)) }
                StudioToggle("Bold", layer.isBold) { update(layer.copy(isBold = it)) }
                StudioSlider("Font size / 320-unit canvas", layer.fontSizeSp, 6f..128f) { update(layer.copy(fontSizeSp = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { update(layer.copy(xRatio = 0.5f)) }, modifier = Modifier.weight(1f)) { Text("Center X") }
                OutlinedButton(onClick = { update(layer.copy(yRatio = 0.5f)) }, modifier = Modifier.weight(1f)) { Text("Center Y") }
            }
            StudioSlider("X position %", layer.xRatio * 100, 0f..100f) { update(layer.copy(xRatio = it / 100)) }
            StudioSlider("Y position %", layer.yRatio * 100, 0f..100f) { update(layer.copy(yRatio = it / 100)) }
            if (layer.type != "text") {
                StudioSlider("Width %", layer.widthRatio * 100, 1f..100f) { update(layer.copy(widthRatio = it / 100)) }
                StudioSlider(if (layer.type == "line") "Stroke height %" else "Height %", layer.heightRatio * 100, 0.5f..100f) { update(layer.copy(heightRatio = it / 100)) }
            }
            StudioSlider("Rotation degrees", layer.rotationDegrees, -180f..180f) { update(layer.copy(rotationDegrees = it)) }
            StudioSlider("Opacity %", layer.opacity * 100, 0f..100f) { update(layer.copy(opacity = it / 100)) }
            if (layer.type == "rectangle") StudioSlider("Corner radius", layer.cornerRadiusDp, 0f..100f) { update(layer.copy(cornerRadiusDp = it)) }
            StudioColor("Layer color", layer.colorHex) { update(layer.copy(colorHex = it)) }
            StudioToggle("Visible in ambient layout", layer.showInAmbient) { update(layer.copy(showInAmbient = it)) }
        }
    }
}

@Composable
internal fun StudioBackgroundPrecision(bg: BackgroundConfig, change: (BackgroundConfig) -> Unit) {
    if (bg.type == "image") StudioSlider("Photo dimming %", bg.imageDimAmount * 100, 0f..100f) { change(bg.copy(imageDimAmount = it / 100)) }
    StudioColor(if (bg.type.startsWith("gradient")) "Gradient start" else "Background color", if (bg.type.startsWith("gradient")) bg.gradientStartHex ?: bg.colorHex else bg.colorHex) {
        change(bg.copy(colorHex = it, gradientStartHex = it))
    }
    if (bg.type.startsWith("gradient")) StudioColor("Gradient end", bg.gradientEndHex ?: "#05070A") { change(bg.copy(gradientEndHex = it)) }
    if (bg.type == "gradient_linear") StudioSlider("Gradient angle", bg.gradientAngle, 0f..360f) { change(bg.copy(gradientAngle = it)) }
}

@Composable
internal fun StudioDialPrecision(dial: DialConfig, change: (DialConfig) -> Unit) {
    StudioSlider("Dial radius %", dial.dialRadiusRatio * 100, 30f..98f) { change(dial.copy(dialRadiusRatio = it / 100)) }
    StudioSlider("Major tick length", dial.majorTickLength, 1f..40f) { change(dial.copy(majorTickLength = it)) }
    StudioSlider("Minor tick length", dial.minorTickLength, 1f..20f) { change(dial.copy(minorTickLength = it)) }
    StudioSlider("Tick width", dial.tickWidth, 0.1f..10f) { change(dial.copy(tickWidth = it)) }
    StudioSlider("Numeral size", dial.numberSizeSp, 4f..40f) { change(dial.copy(numberSizeSp = it)) }
    StudioColor("Major markers", dial.majorTickColorHex) { change(dial.copy(majorTickColorHex = it)) }
    StudioColor("Minor markers", dial.minorTickColorHex) { change(dial.copy(minorTickColorHex = it)) }
}

@Composable
internal fun StudioHandsPrecision(hands: HandsConfig, change: (HandsConfig) -> Unit) {
    val focusManager = LocalFocusManager.current
    var hand by remember { mutableStateOf("hour") }
    StudioChoices(hand, listOf("hour" to "Hour", "minute" to "Minute", "second" to "Second")) { focusManager.clearFocus(); hand = it }
    fun update(style: HandStyle) { change(when (hand) { "hour" -> hands.copy(hourHand = style); "minute" -> hands.copy(minuteHand = style); else -> hands.copy(secondHand = style) }) }
    val style = when (hand) { "hour" -> hands.hourHand; "minute" -> hands.minuteHand; else -> hands.secondHand }
    if (hand == "second") StudioToggle("Show seconds hand", style != null) { change(hands.copy(secondHand = if (it) HandStyle(lengthRatio = 0.88f, widthDp = 1.8f) else null)) }
    if (style != null) {
        StudioSlider("Length % of radius", style.lengthRatio * 100, 5f..100f) { update(style.copy(lengthRatio = it / 100)) }
        StudioSlider("Width", style.widthDp, 0.1f..20f) { update(style.copy(widthDp = it)) }
        StudioSlider("Tail % of radius", style.tailRatio * 100, 0f..50f) { update(style.copy(tailRatio = it / 100)) }
        StudioColor("Hand color", style.colorHex) { update(style.copy(colorHex = it)) }
        StudioToggle("Counterweight", style.hasCounterweight) { update(style.copy(hasCounterweight = it)) }
    }
}

@Composable
internal fun StudioClockPrecision(pkg: HswfPackage, change: (HswfPackage) -> Unit) {
    pkg.digitalClock?.takeIf { it.enabled }?.let { clock ->
        StudioHeading("Time placement")
        StudioSlider("Time X %", clock.xRatio * 100, 0f..100f) { change(pkg.copy(digitalClock = clock.copy(xRatio = it / 100))) }
        StudioSlider("Time Y %", clock.yRatio * 100, 0f..100f) { change(pkg.copy(digitalClock = clock.copy(yRatio = it / 100))) }
        StudioSlider("Time font size", clock.fontSizeSp, 4f..120f) { change(pkg.copy(digitalClock = clock.copy(fontSizeSp = it))) }
        StudioToggle("Bold time", clock.isBold) { change(pkg.copy(digitalClock = clock.copy(isBold = it))) }
        StudioColor("Time color", clock.colorHex) { change(pkg.copy(digitalClock = clock.copy(colorHex = it))) }
    }
    pkg.date?.takeIf { it.enabled }?.let { date ->
        StudioHeading("Date placement")
        StudioSlider("Date X %", date.xRatio * 100, 0f..100f) { change(pkg.copy(date = date.copy(xRatio = it / 100))) }
        StudioSlider("Date Y %", date.yRatio * 100, 0f..100f) { change(pkg.copy(date = date.copy(yRatio = it / 100))) }
        StudioSlider("Date font size", date.fontSizeSp, 4f..50f) { change(pkg.copy(date = date.copy(fontSizeSp = it))) }
        StudioToggle("Date frame", date.hasFrame) { change(pkg.copy(date = date.copy(hasFrame = it))) }
        StudioColor("Date color", date.colorHex) { change(pkg.copy(date = date.copy(colorHex = it))) }
    }
}

@Composable
internal fun StudioWidgetPrecision(widgets: ComplicationsConfig, change: (ComplicationsConfig) -> Unit) {
    val focusManager = LocalFocusManager.current
    var target by remember { mutableStateOf("heart") }
    StudioChoices(target, listOf("heart" to "Heart rate", "steps" to "Steps", "battery" to "Battery")) { focusManager.clearFocus(); target = it }
    val widget = when (target) { "heart" -> widgets.heartRate; "steps" -> widgets.steps; else -> widgets.battery }
    fun update(value: ComplicationWidget) { change(when (target) { "heart" -> widgets.copy(heartRate = value); "steps" -> widgets.copy(steps = value); else -> widgets.copy(battery = value) }) }
    if (widget.enabled) {
        StudioChoices(widget.style, listOf("text_only" to "Text", "arc_gauge" to "Gauge", "subdial" to "Subdial")) { update(widget.copy(style = it)) }
        StudioSlider("Widget X %", widget.xRatio * 100, 0f..100f) { update(widget.copy(xRatio = it / 100)) }
        StudioSlider("Widget Y %", widget.yRatio * 100, 0f..100f) { update(widget.copy(yRatio = it / 100)) }
        StudioSlider("Widget radius %", widget.radiusRatio * 100, 2f..35f) { update(widget.copy(radiusRatio = it / 100)) }
        StudioColor("Widget color", widget.colorHex) { update(widget.copy(colorHex = it)) }
        StudioColor("Gauge track", widget.accentColorHex) { update(widget.copy(accentColorHex = it)) }
    } else Text("Enable this widget above to edit its placement.", color = TextDim, fontSize = 12.sp)
}

@Composable
internal fun StudioEffectsControl(pkg: HswfPackage, change: (HswfPackage) -> Unit) {
    StudioHeading("Motion & interaction", "Animations run in the active face. Ambient mode stays still.")
    val anim = pkg.animation ?: AnimationConfig()
    StudioChoices(anim.type, listOf(
        "none" to "None", "pulse_ecg" to "ECG art", "radar_sweep" to "Radar", "equalizer_bars" to "Equalizer",
        "liquid_wave" to "Wave", "tourbillon_spin" to "Tourbillon", "orbital_space" to "Orbit",
        "aurora_shimmer" to "Aurora", "zen_ripple" to "Ripple"
    )) { change(pkg.copy(animation = if (it == "none") null else anim.copy(type = it))) }
    if (anim.type != "none") {
        Text("Decorative motion • not a sensor reading", color = TextDim, fontSize = 12.sp)
        StudioSlider("Animation speed", anim.speedRatio, 0.1f..5f) { change(pkg.copy(animation = anim.copy(speedRatio = it))) }
        StudioSlider("Animation scale", anim.scaleRatio, 0.1f..3f) { change(pkg.copy(animation = anim.copy(scaleRatio = it))) }
        StudioSlider("Animation X %", anim.xRatio * 100, 0f..100f) { change(pkg.copy(animation = anim.copy(xRatio = it / 100))) }
        StudioSlider("Animation Y %", anim.yRatio * 100, 0f..100f) { change(pkg.copy(animation = anim.copy(yRatio = it / 100))) }
        StudioColor("Animation color", anim.colorHex) { change(pkg.copy(animation = anim.copy(colorHex = it))) }
    }
    HorizontalDivider(color = BorderSubtle)
    val interaction = pkg.interactive ?: InteractiveConfig()
    StudioChoices(interaction.tapEffect, listOf("none" to "No tap action", "palette_cycle" to "Cycle color", "mode_toggle" to "Toggle layout")) {
        change(pkg.copy(interactive = if (it == "none") null else interaction.copy(tapEffect = it, colorPalettes = interaction.colorPalettes.ifEmpty { listOf("#00E5FF", "#D4AF37", "#FF3366", "#10B981") })))
    }
    if (interaction.tapEffect != "none") {
        StudioToggle("Tap haptics", interaction.hapticFeedback) { change(pkg.copy(interactive = interaction.copy(hapticFeedback = it))) }
        Text("Tap actions run on your watch. Canvas taps select design layers.", color = TextDim, fontSize = 12.sp)
        if (interaction.tapEffect == "palette_cycle") interaction.colorPalettes.forEachIndexed { index, color ->
            StudioColor("Tap palette " + (index + 1), color) { chosen ->
                val colors = interaction.colorPalettes.toMutableList(); colors[index] = chosen
                change(pkg.copy(interactive = interaction.copy(colorPalettes = colors)))
            }
        }
    }
}

@Composable
internal fun StudioAmbientControl(aod: AodConfig, change: (AodConfig) -> Unit) {
    StudioHeading("Ambient design", "Tune the always-on layout. Actual always-on availability depends on your watch.")
    StudioChoices(aod.mode, listOf("match_dim" to "Dim active", "monochrome" to "Monochrome", "digital_minimal" to "Minimal time")) { change(aod.copy(mode = it)) }
    StudioSlider("Ambient brightness %", aod.maxBrightnessFactor * 100, 0f..100f) { change(aod.copy(maxBrightnessFactor = it / 100)) }
    if (aod.mode == "monochrome") StudioColor("Ambient color", aod.monochromeHex ?: "#FFFFFF") { change(aod.copy(monochromeHex = it)) }
    StudioToggle("Hide background", aod.hideBackground) { change(aod.copy(hideBackground = it)) }
    StudioToggle("Hide seconds hand", aod.hideSecondsHand) { change(aod.copy(hideSecondsHand = it)) }
    StudioToggle("Hide minor ticks", aod.hideMinorTicks) { change(aod.copy(hideMinorTicks = it)) }
    StudioToggle("Hide widgets", aod.hideComplications) { change(aod.copy(hideComplications = it)) }
    Text("Choose ambient visibility separately for each custom layer in Layers.", color = TextDim, fontSize = 12.sp)
}

@Composable
internal fun StudioTestControl(
    pkg: HswfPackage, liveTime: Boolean, liveChange: (Boolean) -> Unit,
    hours: Int, hoursChange: (Int) -> Unit, minutes: Int, minutesChange: (Int) -> Unit, seconds: Int, secondsChange: (Int) -> Unit,
    samples: Boolean, samplesChange: (Boolean) -> Unit, battery: Int, batteryChange: (Int) -> Unit,
    steps: Int, stepsChange: (Int) -> Unit, heart: Int, heartChange: (Int) -> Unit
) {
    StudioHeading("Preview test bench", "Check legibility at different times, values, and display modes.")
    StudioToggle("Use current time", liveTime, liveChange)
    if (!liveTime) {
        StudioSlider("Hour (24-hour)", hours.toFloat(), 0f..23f) { hoursChange(it.toInt()) }
        StudioSlider("Minute", minutes.toFloat(), 0f..59f) { minutesChange(it.toInt()) }
        StudioSlider("Second", seconds.toFloat(), 0f..59f) { secondsChange(it.toInt()) }
    }
    StudioToggle("Use example widget values", samples, samplesChange)
    if (samples) {
        StudioSlider("Example battery %", battery.toFloat(), 0f..100f) { batteryChange(it.toInt()) }
        StudioSlider("Example steps", steps.toFloat(), 0f..30000f) { stepsChange(it.toInt()) }
        StudioSlider("Example heart rate", heart.toFloat(), 40f..200f) { heartChange(it.toInt()) }
    }
    HorizontalDivider(color = BorderSubtle)
    val errors = FaceStudioDocument.validationErrors(pkg)
    val warnings = FaceStudioDocument.warnings(pkg)
    Text("Package • " + (FaceStudioDocument.estimatedBytes(pkg) / 1024f).let { String.format(Locale.US, "%.1f KB", it) } +
        " • " + pkg.layers.size + " custom layers", color = AccentCyan, fontSize = 12.sp)
    if (errors.isEmpty()) Text("Ready to save and export", color = StatusSuccess, fontWeight = FontWeight.SemiBold)
    errors.forEach { Text(it, color = StatusError, fontSize = 12.sp) }
    warnings.forEach { Text(it, color = StatusWarning, fontSize = 12.sp) }
    if (warnings.isEmpty()) Text("No layout warnings", color = TextDim, fontSize = 12.sp)
}

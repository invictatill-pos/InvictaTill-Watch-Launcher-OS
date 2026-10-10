package com.healthsync.watch.data.watchface

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * Declarative HealthSync Watch Face (.hswf) schema.
 * Represents a dynamic, downloadable watch face bundle rendered natively on Canvas.
 */
@Keep
data class HswfPackage(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("author") val author: String = "HealthSync Studio",
    @SerializedName("version") val version: String = "1.0.0",
    @SerializedName("description") val description: String = "",
    @SerializedName("previewBase64") val previewBase64: String? = null,
    @SerializedName("type") val type: String = "hybrid", // "analog", "digital", "hybrid"
    @SerializedName("background") val background: BackgroundConfig = BackgroundConfig(),
    @SerializedName("dial") val dial: DialConfig = DialConfig(),
    @SerializedName("hands") val hands: HandsConfig? = HandsConfig(),
    @SerializedName("digitalClock") val digitalClock: DigitalClockConfig? = null,
    @SerializedName("date") val date: DateConfig? = DateConfig(),
    @SerializedName("complications") val complications: ComplicationsConfig = ComplicationsConfig(),
    @SerializedName("aod") val aod: AodConfig = AodConfig()
)

@Keep
data class BackgroundConfig(
    @SerializedName("type") val type: String = "solid", // "solid", "gradient_linear", "gradient_radial", "image"
    @SerializedName("colorHex") val colorHex: String = "#0A0D12",
    @SerializedName("gradientStartHex") val gradientStartHex: String? = "#101622",
    @SerializedName("gradientEndHex") val gradientEndHex: String? = "#05070A",
    @SerializedName("gradientAngle") val gradientAngle: Float = 45f,
    @SerializedName("imageBase64") val imageBase64: String? = null
)

@Keep
data class DialConfig(
    @SerializedName("showTicks") val showTicks: Boolean = true,
    @SerializedName("tickCount") val tickCount: Int = 60, // 12, 60
    @SerializedName("majorTickLength") val majorTickLength: Float = 14f,
    @SerializedName("minorTickLength") val minorTickLength: Float = 6f,
    @SerializedName("tickWidth") val tickWidth: Float = 2f,
    @SerializedName("majorTickColorHex") val majorTickColorHex: String = "#00E5FF",
    @SerializedName("minorTickColorHex") val minorTickColorHex: String = "#334155",
    @SerializedName("showNumbers") val showNumbers: Boolean = true,
    @SerializedName("numberType") val numberType: String = "arabic", // "arabic", "roman", "dots", "minimal_cardinal"
    @SerializedName("numberColorHex") val numberColorHex: String = "#F8FAFC",
    @SerializedName("numberSizeSp") val numberSizeSp: Float = 16f,
    @SerializedName("dialRadiusRatio") val dialRadiusRatio: Float = 0.88f
)

@Keep
data class HandsConfig(
    @SerializedName("hourHand") val hourHand: HandStyle = HandStyle(
        lengthRatio = 0.52f,
        widthDp = 5.5f,
        colorHex = "#F8FAFC",
        shape = "sword",
        capRadius = 7f,
        glowColorHex = null
    ),
    @SerializedName("minuteHand") val minuteHand: HandStyle = HandStyle(
        lengthRatio = 0.76f,
        widthDp = 3.8f,
        colorHex = "#38BDF8",
        shape = "sword",
        capRadius = 6f,
        glowColorHex = null
    ),
    @SerializedName("secondHand") val secondHand: HandStyle? = HandStyle(
        lengthRatio = 0.88f,
        widthDp = 1.8f,
        colorHex = "#FF3366",
        shape = "needle",
        tailRatio = 0.22f,
        capRadius = 4f,
        hasCounterweight = true,
        smoothSweep = true
    )
)

@Keep
data class HandStyle(
    @SerializedName("lengthRatio") val lengthRatio: Float = 0.6f,
    @SerializedName("widthDp") val widthDp: Float = 4f,
    @SerializedName("colorHex") val colorHex: String = "#FFFFFF",
    @SerializedName("shape") val shape: String = "sword", // "sword", "baton", "needle", "arrow"
    @SerializedName("tailRatio") val tailRatio: Float = 0.15f,
    @SerializedName("capRadius") val capRadius: Float = 5f,
    @SerializedName("hasCounterweight") val hasCounterweight: Boolean = false,
    @SerializedName("smoothSweep") val smoothSweep: Boolean = false,
    @SerializedName("glowColorHex") val glowColorHex: String? = null
)

@Keep
data class DigitalClockConfig(
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("format") val format: String = "HH:mm", // "HH:mm", "hh:mm a", "HH:mm:ss"
    @SerializedName("xRatio") val xRatio: Float = 0.5f, // 0.0 to 1.0 (center = 0.5)
    @SerializedName("yRatio") val yRatio: Float = 0.35f,
    @SerializedName("fontSizeSp") val fontSizeSp: Float = 36f,
    @SerializedName("colorHex") val colorHex: String = "#FFFFFF",
    @SerializedName("glowColorHex") val glowColorHex: String? = null,
    @SerializedName("isBold") val isBold: Boolean = true,
    @SerializedName("fontFamily") val fontFamily: String = "monospace" // "sans", "serif", "monospace"
)

@Keep
data class DateConfig(
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("format") val format: String = "EEE d", // "EEE, MMM d", "dd/MM", "d", "EEE d"
    @SerializedName("xRatio") val xRatio: Float = 0.72f,
    @SerializedName("yRatio") val yRatio: Float = 0.5f,
    @SerializedName("fontSizeSp") val fontSizeSp: Float = 13f,
    @SerializedName("colorHex") val colorHex: String = "#94A3B8",
    @SerializedName("hasFrame") val hasFrame: Boolean = true,
    @SerializedName("frameColorHex") val frameColorHex: String = "#1E293B"
)

@Keep
data class ComplicationsConfig(
    @SerializedName("heartRate") val heartRate: ComplicationWidget = ComplicationWidget(
        enabled = true,
        xRatio = 0.5f,
        yRatio = 0.72f,
        style = "arc_gauge", // "text_only", "arc_gauge", "subdial"
        colorHex = "#FF3366",
        accentColorHex = "#1E293B"
    ),
    @SerializedName("steps") val steps: ComplicationWidget = ComplicationWidget(
        enabled = true,
        xRatio = 0.28f,
        yRatio = 0.55f,
        style = "arc_gauge",
        colorHex = "#00E5FF",
        accentColorHex = "#1E293B"
    ),
    @SerializedName("battery") val battery: ComplicationWidget = ComplicationWidget(
        enabled = true,
        xRatio = 0.5f,
        yRatio = 0.22f,
        style = "text_only",
        colorHex = "#10B981",
        accentColorHex = "#1E293B"
    ),
    @SerializedName("calories") val calories: ComplicationWidget? = null,
    @SerializedName("distance") val distance: ComplicationWidget? = null
)

@Keep
data class ComplicationWidget(
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("xRatio") val xRatio: Float = 0.5f,
    @SerializedName("yRatio") val yRatio: Float = 0.5f,
    @SerializedName("style") val style: String = "arc_gauge", // "text_only", "arc_gauge", "subdial", "icon_text"
    @SerializedName("radiusRatio") val radiusRatio: Float = 0.14f,
    @SerializedName("colorHex") val colorHex: String = "#FFFFFF",
    @SerializedName("accentColorHex") val accentColorHex: String = "#334155",
    @SerializedName("label") val label: String? = null
)

@Keep
data class AodConfig(
    @SerializedName("mode") val mode: String = "match_dim", // "match_dim", "monochrome", "digital_minimal"
    @SerializedName("hideSecondsHand") val hideSecondsHand: Boolean = true,
    @SerializedName("hideBackground") val hideBackground: Boolean = true,
    @SerializedName("hideMinorTicks") val hideMinorTicks: Boolean = true,
    @SerializedName("maxBrightnessFactor") val maxBrightnessFactor: Float = 0.6f
)

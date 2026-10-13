package com.healthsync.phone.data.watchface

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Manages user-created custom watch faces on the phone.
 * Supports saving, loading, deleting, image compression for photo dials,
 * and built-in starter templates for the Creator Studio.
 */
class CustomFaceStore private constructor(private val context: Context) {

    private val customDir: File by lazy {
        File(context.filesDir, "custom_watchfaces").apply {
            if (!exists()) mkdirs()
        }
    }
    private val documents: FaceStudioFileStore by lazy { FaceStudioFileStore(customDir) }

    companion object {
        private const val TAG = "CustomFaceStore"
        @Volatile private var instance: CustomFaceStore? = null

        fun getInstance(context: Context): CustomFaceStore {
            return instance ?: synchronized(this) {
                instance ?: CustomFaceStore(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Compresses a source bitmap into a square Base64 JPEG optimized for smartwatch screens (320x320).
         * Keeps transfer payload small (~20-40KB) for instantaneous Bluetooth beaming to Kolabee U8 Ultra.
         */
        fun encodePhotoBitmap(bitmap: Bitmap, targetDimension: Int = 320, quality: Int = 85): String {
            require(targetDimension in 32..1024) { "Photo dimensions must be between 32 and 1024 pixels." }
            require(quality in 1..100) { "JPEG quality must be between 1 and 100." }
            require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "The photo bitmap is not available." }
            val minSide = minOf(bitmap.width, bitmap.height)
            val cropX = (bitmap.width - minSide) / 2
            val cropY = (bitmap.height - minSide) / 2
            val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, minSide, minSide)
            var scaled: Bitmap? = null
            try {
                val outputBitmap = if (minSide != targetDimension) {
                    Bitmap.createScaledBitmap(cropped, targetDimension, targetDimension, true)
                } else cropped
                scaled = outputBitmap
                return ByteArrayOutputStream().use { stream ->
                    check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) { "The photo could not be encoded." }
                    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                }
            } finally {
                if (scaled !== bitmap && scaled !== cropped) scaled?.recycle()
                if (cropped !== bitmap) cropped.recycle()
            }
        }
    }

    /** Lists all custom watch faces saved by the user. */
    fun listCustomFaces(): List<HswfPackage> = documents.list()

    /** Finds a custom face by ID. */
    fun getCustomFace(id: String): HswfPackage? = documents.load(id)

    /** Saves or updates a custom watch face. */
    fun saveCustomFace(pkg: HswfPackage): Boolean {
        return documents.save(pkg).also { saved ->
            if (saved) {
                Log.d(TAG, "Successfully saved custom face: ${pkg.name} (${pkg.id})")
            } else {
                Log.e(TAG, "Failed to save custom face ${pkg.id}: invalid document or storage error")
            }
        }
    }

    /** Deletes a custom watch face. */
    fun deleteCustomFace(id: String): Boolean = documents.delete(id)

    /** An independent recovery document, written atomically and excluded from the saved library. */
    fun saveDraft(pkg: HswfPackage): Boolean = documents.saveDraft(pkg)
    fun loadDraft(id: String? = null): HswfPackage? = documents.loadDraft(id)
    fun clearDraft(): Boolean = documents.clearDraft()

    fun getTemplates(): List<HswfPackage> = FaceStudioTemplates.all()
}

/** The exact studio starter designs, available without an Android Context for validation. */
object FaceStudioTemplates {
    /** Provides curated starter design templates that users can customize. */
    fun all(): List<HswfPackage> {
        return listOf(
            createPhotoDialTemplate(),
            createExecutiveChronoTemplate(),
            createCyberNeonTemplate(),
            createMinimalBauhausTemplate(),
            createSportsPaceTemplate(),
            createRetroLcdTemplate()
        )
    }

    private fun createPhotoDialTemplate(): HswfPackage {
        return HswfPackage(
            id = "template_photo",
            name = "Photo Memories",
            author = "HealthSync Studio",
            description = "High-visibility dial optimized for personal photos and family portraits.",
            type = "hybrid",
            background = BackgroundConfig(
                type = "solid",
                colorHex = "#0F172A",
                gradientStartHex = "#1E293B",
                gradientEndHex = "#020617"
            ),
            dial = DialConfig(
                showTicks = true,
                tickCount = 12,
                majorTickLength = 8f,
                minorTickLength = 4f,
                tickWidth = 2f,
                majorTickColorHex = "#F8FAFC",
                minorTickColorHex = "#94A3B8",
                showNumbers = true,
                numberType = "minimal_cardinal",
                numberColorHex = "#F8FAFC",
                numberSizeSp = 14f,
                dialRadiusRatio = 0.90f
            ),
            hands = HandsConfig(
                hourHand = HandStyle(lengthRatio = 0.48f, widthDp = 4f, colorHex = "#FFFFFF", shape = "baton"),
                minuteHand = HandStyle(lengthRatio = 0.72f, widthDp = 2.8f, colorHex = "#38BDF8", shape = "baton"),
                secondHand = HandStyle(lengthRatio = 0.84f, widthDp = 1.5f, colorHex = "#F43F5E", shape = "needle", smoothSweep = true)
            ),
            digitalClock = DigitalClockConfig(
                enabled = true,
                format = "HH:mm",
                xRatio = 0.5f,
                yRatio = 0.75f,
                fontSizeSp = 28f,
                colorHex = "#FFFFFF",
                glowColorHex = "#38BDF8"
            ),
            date = DateConfig(
                enabled = true,
                format = "EEE d",
                xRatio = 0.5f,
                yRatio = 0.25f,
                fontSizeSp = 13f,
                colorHex = "#94A3B8",
                hasFrame = true,
                frameColorHex = "#1E293B"
            ),
            complications = ComplicationsConfig(
                battery = ComplicationWidget(enabled = true, xRatio = 0.28f, yRatio = 0.5f, style = "text_only", colorHex = "#10B981"),
                heartRate = ComplicationWidget(enabled = true, xRatio = 0.72f, yRatio = 0.5f, style = "text_only", colorHex = "#F43F5E")
            )
        )
    }

    private fun createExecutiveChronoTemplate(): HswfPackage {
        return HswfPackage(
            id = "template_executive",
            name = "Executive Chrono",
            author = "HealthSync Studio",
            description = "Refined luxury analog layout with Roman numerals and dual subdials.",
            type = "analog",
            background = BackgroundConfig(
                type = "gradient_radial",
                colorHex = "#0B0E14",
                gradientStartHex = "#161D2A",
                gradientEndHex = "#06080C"
            ),
            dial = DialConfig(
                showTicks = true,
                tickCount = 60,
                majorTickLength = 14f,
                minorTickLength = 6f,
                tickWidth = 2.2f,
                majorTickColorHex = "#D4AF37",
                minorTickColorHex = "#475569",
                showNumbers = true,
                numberType = "roman",
                numberColorHex = "#F8FAFC",
                numberSizeSp = 15f,
                dialRadiusRatio = 0.86f
            ),
            hands = HandsConfig(
                hourHand = HandStyle(lengthRatio = 0.52f, widthDp = 5.5f, colorHex = "#F8FAFC", shape = "sword", capRadius = 7f),
                minuteHand = HandStyle(lengthRatio = 0.76f, widthDp = 3.8f, colorHex = "#D4AF37", shape = "sword", capRadius = 6f),
                secondHand = HandStyle(lengthRatio = 0.88f, widthDp = 1.6f, colorHex = "#00E5FF", shape = "needle", smoothSweep = true)
            ),
            digitalClock = null,
            date = DateConfig(
                enabled = true,
                format = "d",
                xRatio = 0.74f,
                yRatio = 0.5f,
                fontSizeSp = 14f,
                colorHex = "#D4AF37",
                hasFrame = true,
                frameColorHex = "#1E293B"
            ),
            complications = ComplicationsConfig(
                heartRate = ComplicationWidget(enabled = true, xRatio = 0.5f, yRatio = 0.72f, style = "arc_gauge", colorHex = "#00E5FF", accentColorHex = "#1E293B"),
                steps = ComplicationWidget(enabled = true, xRatio = 0.5f, yRatio = 0.28f, style = "arc_gauge", colorHex = "#D4AF37", accentColorHex = "#1E293B")
            )
        )
    }

    private fun createCyberNeonTemplate(): HswfPackage {
        return HswfPackage(
            id = "template_cyber",
            name = "Cyber Neon",
            author = "HealthSync Studio",
            description = "High-tech cyberpunk aesthetic with glowing digital clock and neon telemetry.",
            type = "digital",
            background = BackgroundConfig(
                type = "gradient_linear",
                colorHex = "#05050A",
                gradientStartHex = "#0A0818",
                gradientEndHex = "#020205",
                gradientAngle = 90f
            ),
            dial = DialConfig(
                showTicks = true,
                tickCount = 12,
                majorTickLength = 8f,
                minorTickLength = 4f,
                tickWidth = 2f,
                majorTickColorHex = "#00F0FF",
                minorTickColorHex = "#FF0055",
                showNumbers = false,
                dialRadiusRatio = 0.90f
            ),
            hands = null,
            digitalClock = DigitalClockConfig(
                enabled = true,
                format = "HH:mm:ss",
                xRatio = 0.5f,
                yRatio = 0.44f,
                fontSizeSp = 34f,
                colorHex = "#00F0FF",
                glowColorHex = "#00F0FF",
                fontFamily = "monospace"
            ),
            date = DateConfig(
                enabled = true,
                format = "EEE, MMM d",
                xRatio = 0.5f,
                yRatio = 0.58f,
                fontSizeSp = 12f,
                colorHex = "#FF0055",
                hasFrame = false
            ),
            complications = ComplicationsConfig(
                heartRate = ComplicationWidget(enabled = true, xRatio = 0.28f, yRatio = 0.74f, style = "arc_gauge", colorHex = "#FF0055", accentColorHex = "#200515"),
                steps = ComplicationWidget(enabled = true, xRatio = 0.72f, yRatio = 0.74f, style = "arc_gauge", colorHex = "#00F0FF", accentColorHex = "#051520"),
                battery = ComplicationWidget(enabled = true, xRatio = 0.5f, yRatio = 0.24f, style = "text_only", colorHex = "#00FF66")
            )
        )
    }

    private fun createMinimalBauhausTemplate(): HswfPackage {
        return HswfPackage(
            id = "template_bauhaus",
            name = "Minimal Bauhaus",
            author = "HealthSync Studio",
            description = "Timeless Swiss minimalism with stark monochrome geometric clarity.",
            type = "analog",
            background = BackgroundConfig(
                type = "solid",
                colorHex = "#0A0A0A"
            ),
            dial = DialConfig(
                showTicks = true,
                tickCount = 12,
                majorTickLength = 10f,
                minorTickLength = 0f,
                tickWidth = 3f,
                majorTickColorHex = "#E5E5E5",
                minorTickColorHex = "#262626",
                showNumbers = false,
                dialRadiusRatio = 0.88f
            ),
            hands = HandsConfig(
                hourHand = HandStyle(lengthRatio = 0.48f, widthDp = 5f, colorHex = "#FFFFFF", shape = "baton", capRadius = 6f),
                minuteHand = HandStyle(lengthRatio = 0.76f, widthDp = 3f, colorHex = "#E5E5E5", shape = "baton", capRadius = 5f),
                secondHand = HandStyle(lengthRatio = 0.86f, widthDp = 1.6f, colorHex = "#E11D48", shape = "needle", smoothSweep = true)
            ),
            digitalClock = null,
            date = DateConfig(
                enabled = true,
                format = "d",
                xRatio = 0.5f,
                yRatio = 0.68f,
                fontSizeSp = 13f,
                colorHex = "#A3A3A3",
                hasFrame = false
            ),
            complications = ComplicationsConfig(
                battery = ComplicationWidget(enabled = true, xRatio = 0.5f, yRatio = 0.32f, style = "text_only", colorHex = "#737373")
            )
        )
    }

    private fun createSportsPaceTemplate(): HswfPackage {
        return HswfPackage(
            id = "template_sports",
            name = "Velocity Sport",
            author = "HealthSync Studio",
            description = "High-energy sports performance dial with pacing arcs and bold digital clock.",
            type = "hybrid",
            background = BackgroundConfig(
                type = "gradient_radial",
                colorHex = "#05070A",
                gradientStartHex = "#0C131E",
                gradientEndHex = "#020305"
            ),
            dial = DialConfig(
                showTicks = true,
                tickCount = 60,
                majorTickLength = 12f,
                minorTickLength = 5f,
                tickWidth = 2f,
                majorTickColorHex = "#10B981",
                minorTickColorHex = "#1E293B",
                showNumbers = false
            ),
            hands = HandsConfig(
                hourHand = HandStyle(lengthRatio = 0.48f, widthDp = 4.8f, colorHex = "#FFFFFF", shape = "sword", capRadius = 6f),
                minuteHand = HandStyle(lengthRatio = 0.72f, widthDp = 3.4f, colorHex = "#10B981", shape = "sword", capRadius = 5f),
                secondHand = HandStyle(lengthRatio = 0.85f, widthDp = 1.6f, colorHex = "#06B6D4", shape = "needle", smoothSweep = true)
            ),
            digitalClock = DigitalClockConfig(
                enabled = true,
                format = "HH:mm",
                xRatio = 0.5f,
                yRatio = 0.32f,
                fontSizeSp = 28f,
                colorHex = "#FFFFFF",
                fontFamily = "sans"
            ),
            date = DateConfig(
                enabled = true,
                format = "EEE d",
                xRatio = 0.5f,
                yRatio = 0.20f,
                fontSizeSp = 12f,
                colorHex = "#94A3B8"
            ),
            complications = ComplicationsConfig(
                heartRate = ComplicationWidget(enabled = true, xRatio = 0.28f, yRatio = 0.65f, style = "arc_gauge", colorHex = "#EF4444", accentColorHex = "#1E293B"),
                steps = ComplicationWidget(enabled = true, xRatio = 0.72f, yRatio = 0.65f, style = "arc_gauge", colorHex = "#10B981", accentColorHex = "#1E293B"),
                battery = ComplicationWidget(enabled = true, xRatio = 0.5f, yRatio = 0.80f, style = "text_only", colorHex = "#06B6D4")
            )
        )
    }

    private fun createRetroLcdTemplate(): HswfPackage {
        return HswfPackage(
            id = "template_retro_lcd",
            name = "Digital Retro 1991",
            author = "HealthSync Studio",
            description = "Iconic slanted digital LCD matrix with authentic green phosphor look.",
            type = "digital",
            background = BackgroundConfig(
                type = "solid",
                colorHex = "#08100C"
            ),
            dial = DialConfig(showTicks = false, showNumbers = false),
            hands = null,
            digitalClock = DigitalClockConfig(
                enabled = true,
                format = "HH:mm",
                xRatio = 0.5f,
                yRatio = 0.36f,
                fontSizeSp = 48f,
                colorHex = "#34D399",
                glowColorHex = "#059669",
                isBold = true,
                fontFamily = "monospace"
            ),
            date = DateConfig(
                enabled = true,
                format = "EEE, MMM d",
                xRatio = 0.5f,
                yRatio = 0.20f,
                fontSizeSp = 13f,
                colorHex = "#34D399",
                hasFrame = false
            ),
            complications = ComplicationsConfig(
                heartRate = ComplicationWidget(enabled = true, xRatio = 0.30f, yRatio = 0.68f, style = "arc_gauge", colorHex = "#F87171", accentColorHex = "#1E293B"),
                steps = ComplicationWidget(enabled = true, xRatio = 0.70f, yRatio = 0.68f, style = "arc_gauge", colorHex = "#34D399", accentColorHex = "#1E293B"),
                battery = ComplicationWidget(enabled = true, xRatio = 0.5f, yRatio = 0.52f, style = "text_only", colorHex = "#34D399")
            )
        )
    }
}

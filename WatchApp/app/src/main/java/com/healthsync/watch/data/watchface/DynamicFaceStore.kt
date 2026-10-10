package com.healthsync.watch.data.watchface

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages installed .hswf dynamic watch faces on the watch filesystem.
 * Handles storage in filesDir/installed_watchfaces/, JSON serialization/deserialization,
 * and first-boot installation of starter showcase dials.
 */
class DynamicFaceStore(private val context: Context) {

    private val gson = Gson()
    private val storeDir: File by lazy {
        File(context.filesDir, "installed_watchfaces").apply {
            if (!exists()) mkdirs()
        }
    }

    private val memoryCache = ConcurrentHashMap<String, HswfPackage>()

    init {
        ensureStarterFacesInstalled()
        loadAllIntoCache()
    }

    /** Returns all currently installed dynamic watch face packages. */
    fun listInstalledFaces(): List<HswfPackage> {
        return memoryCache.values.toList().sortedBy { it.name }
    }

    /** Finds an installed face by ID. */
    fun getFace(id: String): HswfPackage? {
        val cleanId = id.removePrefix("dynamic_")
        return memoryCache[cleanId] ?: loadFromFile(cleanId)
    }

    /** Installs or updates a watch face from its package definition. */
    fun saveFace(pkg: HswfPackage): Boolean {
        return try {
            val json = gson.toJson(pkg)
            val file = File(storeDir, "${pkg.id}.hswf")
            file.writeText(json, Charsets.UTF_8)
            memoryCache[pkg.id] = pkg
            Log.d(TAG, "Successfully installed watchface: ${pkg.name} (${pkg.id})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save watchface ${pkg.id}", e)
            false
        }
    }

    /** Uninstalls a dynamic face from storage. */
    fun deleteFace(id: String): Boolean {
        val cleanId = id.removePrefix("dynamic_")
        memoryCache.remove(cleanId)
        val file = File(storeDir, "$cleanId.hswf")
        return file.exists() && file.delete()
    }

    private fun loadAllIntoCache() {
        val files = storeDir.listFiles { _, name -> name.endsWith(".hswf") } ?: return
        for (file in files) {
            try {
                val json = file.readText(Charsets.UTF_8)
                val pkg = gson.fromJson(json, HswfPackage::class.java)
                if (pkg != null && pkg.id.isNotBlank()) {
                    memoryCache[pkg.id] = pkg
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading face from ${file.name}", e)
            }
        }
    }

    private fun loadFromFile(id: String): HswfPackage? {
        val file = File(storeDir, "$id.hswf")
        if (!file.exists()) return null
        return try {
            val json = file.readText(Charsets.UTF_8)
            val pkg = gson.fromJson(json, HswfPackage::class.java)
            if (pkg != null) memoryCache[id] = pkg
            pkg
        } catch (e: Exception) {
            Log.w(TAG, "Error reading $id.hswf", e)
            null
        }
    }

    /** Seeds high-quality starter dynamic dials on first boot. */
    private fun ensureStarterFacesInstalled() {
        val prefs = context.getSharedPreferences("hswf_store_meta", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("starter_faces_v1", false)) {
            saveFace(createNeonCyberpunkSample())
            saveFace(createBauhausMinimalSample())
            prefs.edit().putBoolean("starter_faces_v1", true).apply()
        }
    }

    companion object {
        private const val TAG = "DynamicFaceStore"

        @Volatile
        private var instance: DynamicFaceStore? = null

        fun getInstance(context: Context): DynamicFaceStore {
            return instance ?: synchronized(this) {
                instance ?: DynamicFaceStore(context.applicationContext).also { instance = it }
            }
        }

        /** Sample 1: Neon Cyberpunk Hybrid Watch Face */
        fun createNeonCyberpunkSample(): HswfPackage {
            return HswfPackage(
                id = "neon_cyberpunk",
                name = "Neon Cyberpunk",
                author = "HealthSync Studio",
                description = "Futuristic cyan & magenta neon hybrid with live circular gauge subdials.",
                type = "hybrid",
                background = BackgroundConfig(
                    type = "gradient_radial",
                    colorHex = "#05070B",
                    gradientStartHex = "#0C1222",
                    gradientEndHex = "#030407"
                ),
                dial = DialConfig(
                    showTicks = true,
                    tickCount = 60,
                    majorTickLength = 16f,
                    minorTickLength = 6f,
                    tickWidth = 2.2f,
                    majorTickColorHex = "#00F0FF", // Neon Cyan
                    minorTickColorHex = "#1E293B",
                    showNumbers = true,
                    numberType = "arabic",
                    numberColorHex = "#F8FAFC",
                    numberSizeSp = 15f
                ),
                hands = HandsConfig(
                    hourHand = HandStyle(
                        lengthRatio = 0.52f,
                        widthDp = 6f,
                        colorHex = "#00F0FF",
                        shape = "sword",
                        capRadius = 7f,
                        glowColorHex = "#00F0FF"
                    ),
                    minuteHand = HandStyle(
                        lengthRatio = 0.76f,
                        widthDp = 4f,
                        colorHex = "#E0E7FF",
                        shape = "sword",
                        capRadius = 6f,
                        glowColorHex = null
                    ),
                    secondHand = HandStyle(
                        lengthRatio = 0.88f,
                        widthDp = 1.8f,
                        colorHex = "#FF007F", // Neon Magenta
                        shape = "needle",
                        tailRatio = 0.22f,
                        capRadius = 4f,
                        hasCounterweight = true,
                        smoothSweep = true
                    )
                ),
                digitalClock = DigitalClockConfig(
                    enabled = true,
                    format = "HH:mm",
                    xRatio = 0.5f,
                    yRatio = 0.34f,
                    fontSizeSp = 30f,
                    colorHex = "#00F0FF",
                    glowColorHex = "#00F0FF",
                    isBold = true
                ),
                date = DateConfig(
                    enabled = true,
                    format = "EEE d",
                    xRatio = 0.72f,
                    yRatio = 0.5f,
                    fontSizeSp = 12f,
                    colorHex = "#94A3B8",
                    hasFrame = true,
                    frameColorHex = "#0F172A"
                ),
                complications = ComplicationsConfig(
                    heartRate = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.5f,
                        yRatio = 0.72f,
                        style = "arc_gauge",
                        radiusRatio = 0.14f,
                        colorHex = "#FF007F",
                        accentColorHex = "#1E293B",
                        label = "BPM"
                    ),
                    steps = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.28f,
                        yRatio = 0.52f,
                        style = "arc_gauge",
                        radiusRatio = 0.13f,
                        colorHex = "#00F0FF",
                        accentColorHex = "#1E293B",
                        label = "STEPS"
                    ),
                    battery = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.5f,
                        yRatio = 0.20f,
                        style = "text_only",
                        colorHex = "#10B981",
                        accentColorHex = "#1E293B"
                    )
                ),
                aod = AodConfig(
                    mode = "match_dim",
                    hideSecondsHand = true,
                    hideBackground = true,
                    hideMinorTicks = true
                )
            )
        }

        /** Sample 2: Bauhaus Minimal Analog Watch Face */
        fun createBauhausMinimalSample(): HswfPackage {
            return HswfPackage(
                id = "bauhaus_minimal",
                name = "Bauhaus Minimal",
                author = "HealthSync Studio",
                description = "Classic German Bauhaus analog simplicity with refined baton hands and gold accents.",
                type = "analog",
                background = BackgroundConfig(
                    type = "solid",
                    colorHex = "#0F172A"
                ),
                dial = DialConfig(
                    showTicks = true,
                    tickCount = 12,
                    majorTickLength = 18f,
                    minorTickLength = 8f,
                    tickWidth = 2.5f,
                    majorTickColorHex = "#F59E0B", // Amber Gold
                    minorTickColorHex = "#475569",
                    showNumbers = true,
                    numberType = "minimal_cardinal", // 12, 3, 6, 9
                    numberColorHex = "#F1F5F9",
                    numberSizeSp = 18f,
                    dialRadiusRatio = 0.86f
                ),
                hands = HandsConfig(
                    hourHand = HandStyle(
                        lengthRatio = 0.50f,
                        widthDp = 4.5f,
                        colorHex = "#F1F5F9",
                        shape = "baton",
                        capRadius = 5f
                    ),
                    minuteHand = HandStyle(
                        lengthRatio = 0.78f,
                        widthDp = 3.0f,
                        colorHex = "#CBD5E1",
                        shape = "baton",
                        capRadius = 4f
                    ),
                    secondHand = HandStyle(
                        lengthRatio = 0.85f,
                        widthDp = 1.5f,
                        colorHex = "#F59E0B",
                        shape = "needle",
                        tailRatio = 0.2f,
                        capRadius = 3f,
                        smoothSweep = true
                    )
                ),
                digitalClock = null,
                date = DateConfig(
                    enabled = true,
                    format = "d",
                    xRatio = 0.74f,
                    yRatio = 0.5f,
                    fontSizeSp = 13f,
                    colorHex = "#F59E0B",
                    hasFrame = true,
                    frameColorHex = "#1E293B"
                ),
                complications = ComplicationsConfig(
                    heartRate = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.5f,
                        yRatio = 0.70f,
                        style = "subdial",
                        radiusRatio = 0.15f,
                        colorHex = "#EF4444",
                        accentColorHex = "#1E293B",
                        label = "HR"
                    ),
                    steps = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.28f,
                        yRatio = 0.5f,
                        style = "text_only",
                        colorHex = "#F1F5F9"
                    ),
                    battery = ComplicationWidget(
                        enabled = true,
                        xRatio = 0.5f,
                        yRatio = 0.26f,
                        style = "text_only",
                        colorHex = "#10B981"
                    )
                ),
                aod = AodConfig(
                    mode = "outline",
                    hideSecondsHand = true,
                    hideBackground = true
                )
            )
        }
    }
}

package com.healthsync.phone.data.watchface

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.math.BigDecimal
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.Collections
import java.util.UUID
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

data class FaceStudioImportResult(
    val packageOrNull: HswfPackage? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = packageOrNull != null && errors.isEmpty()
}

/** Portable document operations shared by the editor, file picker and local storage. */
object FaceStudioDocument {
    const val MAX_PACKAGE_BYTES = 512 * 1024
    const val MAX_LAYERS = 128
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val safeId = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
    private val reservedId = Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])")
    private val color = Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")
    private val nullableFields = setOf(
        "hands", "digitalClock", "date", "animation", "interactive", "secondHand",
        "calories", "distance", "previewBase64", "gradientStartHex", "gradientEndHex",
        "imageBase64", "glowColorHex", "label", "secondaryColorHex", "monochromeHex"
    )

    fun isSafeId(id: String): Boolean = safeId.matches(id) && !reservedId.matches(id)

    fun blank(type: String = "hybrid"): HswfPackage {
        require(type in setOf("analog", "digital", "hybrid")) { "Choose analog, digital or hybrid." }
        return HswfPackage(
            id = newId(), name = "Untitled ${type.replaceFirstChar { it.uppercase() }}", author = "You",
            type = type, background = BackgroundConfig(colorHex = "#080D16"),
            dial = DialConfig(showTicks = false, showNumbers = false),
            hands = if (type == "digital") null else HandsConfig(),
            digitalClock = if (type == "analog") null else DigitalClockConfig(yRatio = 0.5f),
            date = null,
            complications = ComplicationsConfig(
                heartRate = ComplicationWidget(enabled = false),
                steps = ComplicationWidget(enabled = false),
                battery = ComplicationWidget(enabled = false)
            )
        )
    }

    fun duplicate(pkg: HswfPackage, name: String = "${pkg.name} Copy"): HswfPackage =
        snapshot(pkg.copy(id = newId(), name = name.trim().take(100).ifBlank { "Untitled copy" }, layers = pkg.layers.map { it.copy(id = newId()) }))

    fun estimatedBytes(pkg: HswfPackage): Int = try {
        gson.toJson(pkg).toByteArray(Charsets.UTF_8).size
    } catch (_: IllegalArgumentException) {
        Int.MAX_VALUE
    }

    fun importJson(json: String): FaceStudioImportResult {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_PACKAGE_BYTES) {
            return FaceStudioImportResult(errors = listOf("This face exceeds the 512 KB package limit."))
        }
        return try {
            val reader = JsonReader(StringReader(json)).apply { isLenient = false }
            val input = reader.use {
                val value = readStrictValue(it, 0)
                require(it.peek() == JsonToken.END_DOCUMENT) { "Unexpected content after the JSON document." }
                require(value.isJsonObject) { "A watch face must be a JSON object." }
                value.asJsonObject
            }
            val errors = mutableListOf<String>()
            for (field in listOf("id", "name")) {
                if (!input.has(field)) errors += "$field is required."
            }
            val normalized = normalizeObject(input, defaultPackage(), "", errors)
            if (errors.isNotEmpty()) return FaceStudioImportResult(errors = errors.distinct())
            val pkg = gson.fromJson(normalized, HswfPackage::class.java)
            errors += validateValues(pkg)
            if (estimatedBytes(pkg) > MAX_PACKAGE_BYTES) errors += "This face exceeds the 512 KB package limit."
            if (errors.isNotEmpty()) FaceStudioImportResult(errors = errors.distinct())
            else FaceStudioImportResult(packageOrNull = snapshot(pkg), warnings = warnings(pkg))
        } catch (e: Exception) {
            FaceStudioImportResult(errors = listOf("Invalid .hswf file: ${e.message ?: "malformed JSON"}"))
        }
    }

    fun validationErrors(pkg: HswfPackage): List<String> = try {
        // Validate the JSON types as well: Gson can produce Kotlin objects with null non-null fields.
        val errors = mutableListOf<String>()
        normalizeObject(gson.toJsonTree(pkg).asJsonObject, defaultPackage(), "", errors)
        if (errors.isEmpty()) errors += validateValues(pkg)
        if (estimatedBytes(pkg) > MAX_PACKAGE_BYTES) errors += "This face exceeds the 512 KB package limit."
        errors.distinct()
    } catch (_: Exception) {
        listOf("The face contains a missing configuration or a non-finite numeric value.")
    }

    fun exportJson(pkg: HswfPackage): String {
        val errors = validationErrors(pkg)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        return gson.toJson(pkg)
    }

    fun warnings(pkg: HswfPackage): List<String> = buildList {
        if (pkg.hands == null && pkg.digitalClock?.enabled != true) add("There is no visible clock on this face.")
        if (pkg.layers.size > 64) add("More than 64 layers may reduce performance on the watch.")
        val visibleText = pkg.layers.filter { it.visible && it.opacity > 0f && it.type == "text" }
        if (visibleText.sumOf { it.text.length } > 4096) add("A large amount of visible text may reduce rendering performance on the watch.")
        pkg.layers.filter { it.visible && it.opacity > 0f }.forEach { layer ->
            val rotation = Math.toRadians(layer.rotationDegrees.toDouble())
            // Text paints at its font size rather than fitting inside the selection rectangle.
            // Keep this portable preflight conservative; the canvas uses measured glyph bounds.
            val estimatedTextWidth = layer.text.codePointCount(0, layer.text.length) * layer.fontSizeSp * 0.65 / 320.0
            val halfWidth = if (layer.type == "text") max(layer.widthRatio.toDouble(), estimatedTextWidth) / 2.0 else layer.widthRatio / 2.0
            val halfHeight = if (layer.type == "text") max(layer.heightRatio.toDouble(), layer.fontSizeSp / 320.0) / 2.0 else layer.heightRatio / 2.0
            val touchesEdge = listOf(-1, 1).any { x ->
                listOf(-1, 1).any { y ->
                    val dx = x * halfWidth * cos(rotation) - y * halfHeight * sin(rotation)
                    val dy = x * halfWidth * sin(rotation) + y * halfHeight * cos(rotation)
                    hypot(layer.xRatio - 0.5 + dx, layer.yRatio - 0.5 + dy) > 0.48
                }
            }
            if (touchesEdge) add("${layer.name} may be clipped by the circular screen edge.")
        }
        if (pkg.background.type == "image" && pkg.background.imageBase64.isNullOrBlank()) {
            add("The photo background has no image yet.")
        }
        if ((pkg.animation != null && pkg.animation.type != "none") || pkg.hands?.secondHand?.smoothSweep == true) {
            add("Continuous motion can increase battery use.")
        }
    }

    internal fun snapshot(pkg: HswfPackage): HswfPackage = pkg.copy(
        layers = Collections.unmodifiableList(pkg.layers.map { it.copy() }),
        interactive = pkg.interactive?.copy(colorPalettes = Collections.unmodifiableList(pkg.interactive.colorPalettes.toList()))
    )

    private fun newId() = "custom_${UUID.randomUUID()}"
    private fun defaultPackage() = gson.toJsonTree(HswfPackage(id = "face", name = "Untitled")).asJsonObject

    private fun optionalTemplate(field: String): JsonElement = when (field) {
        "hands" -> gson.toJsonTree(HandsConfig())
        "digitalClock" -> gson.toJsonTree(DigitalClockConfig())
        "date" -> gson.toJsonTree(DateConfig())
        "animation" -> gson.toJsonTree(AnimationConfig())
        "interactive" -> gson.toJsonTree(InteractiveConfig())
        "secondHand" -> gson.toJsonTree(HandStyle())
        "calories", "distance" -> gson.toJsonTree(ComplicationWidget())
        else -> JsonPrimitive("")
    }

    /** Fill omitted defaults, but reject malformed explicit fields rather than silently coercing them. */
    private fun normalizeObject(
        input: JsonObject, defaults: JsonObject, path: String, errors: MutableList<String>
    ): JsonObject {
        val result = JsonObject()
        for ((field, default) in defaults.entrySet()) {
            val fieldPath = if (path.isEmpty()) field else "$path.$field"
            if (!input.has(field)) {
                result.add(field, default.deepCopy())
                continue
            }
            val value = input[field]
            if (value.isJsonNull) {
                if (field !in nullableFields) errors += "$fieldPath cannot be null."
                result.add(field, JsonNull.INSTANCE)
                continue
            }
            val template = if (default.isJsonNull) optionalTemplate(field) else default
            when {
                template.isJsonObject -> {
                    if (!value.isJsonObject) errors += "$fieldPath must be an object."
                    else result.add(field, normalizeObject(value.asJsonObject, template.asJsonObject, fieldPath, errors))
                }
                template.isJsonArray -> {
                    if (!value.isJsonArray) errors += "$fieldPath must be an array."
                    else {
                        val array = JsonArray()
                        if (field == "layers" && value.asJsonArray.size() > MAX_LAYERS) errors += "A face supports at most $MAX_LAYERS layers."
                        if (field == "colorPalettes" && value.asJsonArray.size() > 32) errors += "At most 32 palette colors are supported."
                        value.asJsonArray.forEachIndexed { index, item ->
                            // A small hostile input containing many empty objects must not expand
                            // into thousands of full default layer objects during normalization.
                            if (index >= if (field == "layers") MAX_LAYERS else 32) return@forEachIndexed
                            if (field == "layers") {
                                if (!item.isJsonObject) errors += "$fieldPath[$index] must be an object."
                                else {
                                    val layerDefaults = gson.toJsonTree(FaceLayer()).asJsonObject
                                    // A new default ID is generated for every older layer that omitted its ID.
                                    array.add(normalizeObject(item.asJsonObject, layerDefaults, "$fieldPath[$index]", errors))
                                }
                            } else if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString) {
                                errors += "$fieldPath[$index] must be a string."
                            } else array.add(item)
                        }
                        result.add(field, array)
                    }
                }
                !value.isJsonPrimitive -> errors += "$fieldPath has an invalid value."
                template.asJsonPrimitive.isString && !value.asJsonPrimitive.isString -> errors += "$fieldPath must be a string."
                template.asJsonPrimitive.isBoolean && !value.asJsonPrimitive.isBoolean -> errors += "$fieldPath must be true or false."
                template.asJsonPrimitive.isNumber && !value.asJsonPrimitive.isNumber -> errors += "$fieldPath must be a number."
                template.asJsonPrimitive.isNumber && !value.asDouble.isFinite() -> errors += "$fieldPath must be finite."
                template.asJsonPrimitive.isNumber && field in setOf("schemaVersion", "tickCount") &&
                    (value.asBigDecimal.stripTrailingZeros().scale() > 0 || value.asDouble !in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) ->
                    errors += "$fieldPath must be a whole number."
                else -> result.add(field, value)
            }
        }
        return result
    }

    private fun validateValues(pkg: HswfPackage): List<String> = buildList {
        fun choice(path: String, value: String, allowed: Set<String>) {
            if (value !in allowed) add("$path has an unsupported value: $value.")
        }
        fun range(path: String, value: Float, min: Float, max: Float) {
            if (!value.isFinite() || value !in min..max) add("$path must be between $min and $max.")
        }
        fun hex(path: String, value: String?) {
            if (value != null && !color.matches(value)) add("$path must be a #RRGGBB or #AARRGGBB color.")
        }
        fun text(path: String, value: String, max: Int, allowEmpty: Boolean = true) {
            if ((!allowEmpty && value.isBlank()) || value.length > max) add("$path must contain ${if (allowEmpty) "0" else "1"}–$max characters.")
            if (value.any { it.code < 32 && it != '\n' && it != '\t' }) add("$path contains unsupported control characters.")
        }
        fun point(path: String, x: Float, y: Float) { range("$path.xRatio", x, 0f, 1f); range("$path.yRatio", y, 0f, 1f) }
        fun encodedImage(path: String, value: String?) {
            if (value.isNullOrEmpty()) return
            val compact = value.filterNot { it == '\n' || it == '\r' || it == ' ' || it == '\t' }
            if (runCatching { java.util.Base64.getDecoder().decode(compact) }.isFailure) add("$path must contain a valid Base64 image.")
        }
        fun hand(path: String, item: HandStyle) {
            range("$path.lengthRatio", item.lengthRatio, 0.05f, 1f)
            range("$path.widthDp", item.widthDp, 0.1f, 30f)
            range("$path.tailRatio", item.tailRatio, 0f, 1f)
            range("$path.capRadius", item.capRadius, 0f, 30f)
            choice("$path.shape", item.shape, setOf("sword", "baton", "needle", "arrow"))
            hex("$path.colorHex", item.colorHex); hex("$path.glowColorHex", item.glowColorHex)
        }
        fun widget(path: String, item: ComplicationWidget?) {
            if (item == null) return
            point(path, item.xRatio, item.yRatio)
            choice("$path.style", item.style, setOf("text_only", "arc_gauge", "subdial", "icon_text"))
            range("$path.radiusRatio", item.radiusRatio, 0.02f, 0.5f)
            hex("$path.colorHex", item.colorHex); hex("$path.accentColorHex", item.accentColorHex)
            item.label?.let { text("$path.label", it, 80) }
        }
        if (pkg.schemaVersion != 1) add("Only .hswf schema version 1 is supported.")
        if (!isSafeId(pkg.id)) add("The face ID may contain only letters, digits, hyphens and underscores (maximum 128 characters).")
        text("name", pkg.name, 100, false); text("author", pkg.author, 100); text("version", pkg.version, 40)
        text("description", pkg.description, 2000)
        encodedImage("previewBase64", pkg.previewBase64)
        choice("type", pkg.type, setOf("analog", "digital", "hybrid"))
        with(pkg.background) {
            choice("background.type", type, setOf("solid", "gradient_linear", "gradient_radial", "image"))
            hex("background.colorHex", colorHex); hex("background.gradientStartHex", gradientStartHex); hex("background.gradientEndHex", gradientEndHex)
            range("background.gradientAngle", gradientAngle, 0f, 360f)
            range("background.imageDimAmount", imageDimAmount, 0f, 1f)
            encodedImage("background.imageBase64", imageBase64)
        }
        with(pkg.dial) {
            if (tickCount !in setOf(12, 60)) add("dial.tickCount must be 12 or 60.")
            range("dial.majorTickLength", majorTickLength, 0f, 50f); range("dial.minorTickLength", minorTickLength, 0f, 50f)
            range("dial.tickWidth", tickWidth, 0.1f, 20f); range("dial.numberSizeSp", numberSizeSp, 4f, 100f)
            range("dial.dialRadiusRatio", dialRadiusRatio, 0.1f, 1f)
            choice("dial.numberType", numberType, setOf("arabic", "roman", "dots", "minimal_cardinal"))
            hex("dial.majorTickColorHex", majorTickColorHex); hex("dial.minorTickColorHex", minorTickColorHex); hex("dial.numberColorHex", numberColorHex)
        }
        pkg.hands?.let { hand("hands.hourHand", it.hourHand); hand("hands.minuteHand", it.minuteHand); it.secondHand?.let { item -> hand("hands.secondHand", item) } }
        pkg.digitalClock?.let {
            point("digitalClock", it.xRatio, it.yRatio); range("digitalClock.fontSizeSp", it.fontSizeSp, 4f, 120f)
            choice("digitalClock.format", it.format, setOf("HH:mm", "HH:mm:ss", "hh:mm a", "hh:mm", "h:mm a"))
            choice("digitalClock.fontFamily", it.fontFamily, setOf("sans", "serif", "monospace"))
            hex("digitalClock.colorHex", it.colorHex); hex("digitalClock.glowColorHex", it.glowColorHex)
        }
        pkg.date?.let {
            point("date", it.xRatio, it.yRatio); range("date.fontSizeSp", it.fontSizeSp, 4f, 100f)
            choice("date.format", it.format, setOf("EEE d", "EEE, MMM d", "dd/MM", "d", "EEE", "MMM d", "dd MMM", "MM/dd", "yyyy-MM-dd"))
            hex("date.colorHex", it.colorHex); hex("date.frameColorHex", it.frameColorHex)
        }
        with(pkg.complications) { widget("complications.heartRate", heartRate); widget("complications.steps", steps); widget("complications.battery", battery); widget("complications.calories", calories); widget("complications.distance", distance) }
        with(pkg.aod) {
            choice("aod.mode", mode, setOf("match_dim", "monochrome", "digital_minimal"))
            range("aod.maxBrightnessFactor", maxBrightnessFactor, 0f, 1f); hex("aod.monochromeHex", monochromeHex)
        }
        pkg.animation?.let {
            choice("animation.type", it.type, setOf("none", "pulse_ecg", "radar_sweep", "equalizer_bars", "liquid_wave", "tourbillon_spin", "orbital_space", "aurora_shimmer", "zen_ripple"))
            range("animation.speedRatio", it.speedRatio, 0.1f, 5f); range("animation.scaleRatio", it.scaleRatio, 0.1f, 3f)
            point("animation", it.xRatio, it.yRatio); hex("animation.colorHex", it.colorHex); hex("animation.secondaryColorHex", it.secondaryColorHex)
        }
        pkg.interactive?.let {
            choice("interactive.tapEffect", it.tapEffect, setOf("none", "palette_cycle", "mode_toggle", "ripple_burst", "complication_shortcuts"))
            if (it.colorPalettes.size > 32) add("At most 32 palette colors are supported.")
            it.colorPalettes.forEachIndexed { index, item -> hex("interactive.colorPalettes[$index]", item) }
        }
        if (pkg.layers.size > MAX_LAYERS) add("A face supports at most $MAX_LAYERS layers.")
        if (pkg.layers.map { it.id }.distinct().size != pkg.layers.size) add("Every layer must have a unique ID.")
        pkg.layers.forEachIndexed { index, layer ->
            val path = "layers[$index]"
            if (!isSafeId(layer.id)) add("$path.id is not a safe layer ID.")
            text("$path.name", layer.name, 80, false); text("$path.text", layer.text, 256)
            choice("$path.type", layer.type, setOf("text", "rectangle", "circle", "line"))
            choice("$path.fontFamily", layer.fontFamily, setOf("sans", "serif", "monospace"))
            point(path, layer.xRatio, layer.yRatio)
            range("$path.widthRatio", layer.widthRatio, 0.005f, 1f); range("$path.heightRatio", layer.heightRatio, 0.005f, 1f)
            range("$path.fontSizeSp", layer.fontSizeSp, 6f, 128f); range("$path.opacity", layer.opacity, 0f, 1f)
            range("$path.rotationDegrees", layer.rotationDegrees, -360f, 360f); range("$path.cornerRadiusDp", layer.cornerRadiusDp, 0f, 160f)
            hex("$path.colorHex", layer.colorHex)
        }
    }

    private fun readStrictValue(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 40) { "The JSON nesting is too deep." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().also { obj ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(!obj.has(key)) { "Duplicate JSON field: $key." }
                    obj.add(key, readStrictValue(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().also { array ->
                reader.beginArray()
                while (reader.hasNext()) array.add(readStrictValue(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> throw IllegalArgumentException("Malformed JSON value.")
        }
    }
}

/** Bounded document history. Slider and drag updates become one undo step per gesture. */
class FaceStudioHistory(
    initial: HswfPackage,
    private val capacity: Int = 80,
    private val maxRetainedBytes: Int = 16 * 1024 * 1024
) {
    private data class Entry(val pkg: HswfPackage, val bytes: Int)
    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()
    private var gestureStart: Entry? = null
    /** Conservative serialized bytes retained in history; shared strings often use less heap. */
    var retainedBytes: Long = 0
        private set
    var current: HswfPackage = FaceStudioDocument.snapshot(initial)
        private set
    private var currentBytes: Int? = null
    val canUndo: Boolean get() = undoStack.isNotEmpty() || (gestureStart?.let { it.pkg != current } == true)
    val canRedo: Boolean get() = redoStack.isNotEmpty() && gestureStart == null

    init {
        require(capacity in 1..500) { "History capacity must be between 1 and 500." }
        require(maxRetainedBytes >= FaceStudioDocument.MAX_PACKAGE_BYTES) { "The history budget must fit at least one maximum-size document." }
    }

    fun commit(pkg: HswfPackage): HswfPackage {
        val next = FaceStudioDocument.snapshot(pkg)
        if (next == current) return current
        if (gestureStart == null) { append(undoStack, currentEntry()); clear(redoStack) }
        current = next
        currentBytes = null
        trim()
        return current
    }

    fun update(pkg: HswfPackage): HswfPackage = commit(pkg)
    fun beginGesture() { if (gestureStart == null) gestureStart = currentEntry() }
    fun endGesture(): HswfPackage {
        gestureStart?.let { start -> if (start.pkg != current) { append(undoStack, start); clear(redoStack) } }
        gestureStart = null
        trim()
        return current
    }
    fun cancelGesture(): HswfPackage {
        gestureStart?.let { current = it.pkg; currentBytes = it.bytes }
        gestureStart = null
        return current
    }
    fun undo(): HswfPackage {
        endGesture()
        if (undoStack.isNotEmpty()) {
            val previous = removeLast(undoStack)
            append(redoStack, currentEntry())
            current = previous.pkg
            currentBytes = previous.bytes
            trim()
        }
        return current
    }
    fun redo(): HswfPackage {
        endGesture()
        if (redoStack.isNotEmpty()) {
            val next = removeLast(redoStack)
            append(undoStack, currentEntry())
            current = next.pkg
            currentBytes = next.bytes
            trim()
        }
        return current
    }
    fun reset(pkg: HswfPackage): HswfPackage {
        current = FaceStudioDocument.snapshot(pkg); currentBytes = null; clear(undoStack); clear(redoStack); gestureStart = null
        return current
    }
    private fun currentEntry(): Entry = Entry(current, currentBytes ?: FaceStudioDocument.estimatedBytes(current).also { currentBytes = it })
    private fun append(stack: ArrayDeque<Entry>, entry: Entry) {
        stack.addLast(entry)
        retainedBytes += entry.bytes.toLong()
    }
    private fun removeLast(stack: ArrayDeque<Entry>): Entry = stack.removeLast().also { retainedBytes -= it.bytes.toLong() }
    private fun removeFirst(stack: ArrayDeque<Entry>) { retainedBytes -= stack.removeFirst().bytes.toLong() }
    private fun clear(stack: ArrayDeque<Entry>) { while (stack.isNotEmpty()) removeFirst(stack) }
    private fun trim() {
        while (undoStack.size > capacity) removeFirst(undoStack)
        while (redoStack.size > capacity) removeFirst(redoStack)
        while (retainedBytes > maxRetainedBytes) {
            // Keep the nearest undo and redo steps; discard the oldest retained states first.
            if (undoStack.isNotEmpty()) removeFirst(undoStack) else removeFirst(redoStack)
        }
    }
}

/** Atomic, path-safe persistence with no Android dependency, also used for crash recovery drafts. */
class FaceStudioFileStore(directory: File) {
    private val root = directory.canonicalFile

    @Synchronized fun save(pkg: HswfPackage): Boolean = runCatching { write(faceFile(pkg.id), FaceStudioDocument.exportJson(pkg)); true }.getOrDefault(false)
    @Synchronized fun load(id: String): HswfPackage? = runCatching { read(faceFile(id))?.takeIf { it.id == id } }.getOrNull()
    @Synchronized fun delete(id: String): Boolean = runCatching { faceFile(id).delete() }.getOrDefault(false)
    @Synchronized fun saveDraft(pkg: HswfPackage): Boolean = runCatching {
        val json = FaceStudioDocument.exportJson(pkg)
        write(draftFile(pkg.id), json)
        write(draftFile(), json)
        pruneProjectDrafts()
        true
    }.getOrDefault(false)
    @Synchronized fun loadDraft(id: String? = null): HswfPackage? = runCatching {
        read(draftFile(id))?.takeIf { id == null || it.id == id }
    }.getOrNull()
    @Synchronized fun clearDraft(): Boolean = runCatching {
        val files = root.listFiles { file -> isDraftName(file.name) }.orEmpty()
        var cleared = true
        files.forEach { file -> if (!checkedFile(file.name).delete()) cleared = false }
        cleared
    }.getOrDefault(false)
    @Synchronized fun list(): List<HswfPackage> = root.listFiles { file -> file.isFile && file.name.endsWith(".hswf") && !file.name.startsWith(".") }
        ?.sortedByDescending { it.lastModified() }
        ?.mapNotNull { file -> runCatching { val id = file.name.removeSuffix(".hswf"); load(id) }.getOrNull() }
        ?: emptyList()

    private fun faceFile(id: String): File {
        require(FaceStudioDocument.isSafeId(id)) { "Unsafe face ID." }
        return checkedFile("$id.hswf")
    }
    private fun draftFile(id: String? = null): File {
        if (id == null) return checkedFile(".studio-draft.hswf")
        require(FaceStudioDocument.isSafeId(id)) { "Unsafe draft ID." }
        return checkedFile(".studio-draft-$id.hswf")
    }
    private fun isDraftName(name: String): Boolean = name == ".studio-draft.hswf" ||
        (name.startsWith(".studio-draft-") && name.endsWith(".hswf") &&
            FaceStudioDocument.isSafeId(name.removePrefix(".studio-draft-").removeSuffix(".hswf")))
    private fun pruneProjectDrafts() {
        root.listFiles { file -> file.isFile && file.name.startsWith(".studio-draft-") && isDraftName(file.name) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(20)
            ?.forEach { file -> check(checkedFile(file.name).delete()) { "An old studio draft could not be removed." } }
    }
    private fun checkedFile(name: String): File = File(root, name).also {
        require(it.canonicalFile.parentFile == root) { "The face path leaves the storage directory." }
    }
    private fun read(file: File): HswfPackage? {
        if (!file.isFile || file.length() > FaceStudioDocument.MAX_PACKAGE_BYTES) return null
        // Limit the read too, in case an external process changes the file after the size check.
        val bytes = file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            val limit = FaceStudioDocument.MAX_PACKAGE_BYTES + 1
            while (output.size() < limit) {
                val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (bytes.size > FaceStudioDocument.MAX_PACKAGE_BYTES) return null
        return FaceStudioDocument.importJson(bytes.toString(Charsets.UTF_8)).packageOrNull
    }
    private fun write(file: File, json: String) {
        check(root.isDirectory || root.mkdirs()) { "Cannot create the watch face directory." }
        val temporary = checkedFile(".${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { stream -> stream.write(json.toByteArray(Charsets.UTF_8)); stream.fd.sync() }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }
}

package com.healthsync.phone.service

/** Pure, conservative rules for Google's public notification text; no route or locale is inferred. */
internal object NavigationNotificationParser {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"
    private const val NAVIGATION_CATEGORY = "navigation"

    data class Source(
        val packageName: String,
        val key: String,
        val ongoing: Boolean,
        val category: String? = null,
        val title: String? = null,
        val text: String? = null,
        val bigText: String? = null,
        val subText: String? = null,
        val groupSummary: Boolean = false,
        val progress: Boolean = false,
        val actionTitles: List<String> = emptyList()
    )

    data class Directions(val sessionId: String, val instruction: String, val distance: String = "",
                          val road: String = "", val summary: String = "", val maneuver: String = "UNKNOWN")

    // A distance is only lifted out of the start of a source field. A trip's remaining distance is
    // never substituted for the distance to the next maneuver. Unknown units stay in the text.
    private val leadingDistance = Regex(
        "^(?:[Ii]n\\s+)?([\\p{N}]+(?:[.,][\\p{N}]+)?\\s*(?:km|mi|ft|m|kilometers?|kilometres?|meters?|metres?|miles?|feet))(?=\\s|[·•,–—:;-]|$)",
        RegexOption.IGNORE_CASE)
    private val separator = Regex("^[\\s·•,–—:;-]+")
    private val englishInstruction = Regex(
        "\\b(?:turn|keep|continue|head|merge|take|exit|make a u-turn|at the roundabout|arriv(?:e|ing))\\b",
        RegexOption.IGNORE_CASE)
    private val exitNavigation = Regex("^(?:exit|stop|end) navigation$", RegexOption.IGNORE_CASE)
    private val noise = Regex("\\b(?:download(?:ing|ed|s)?|offline maps?|traffic (?:alert|update)|commute (?:alert|update))\\b",
        RegexOption.IGNORE_CASE)

    fun parse(source: Source): Directions? {
        if (source.packageName != MAPS_PACKAGE || !source.ongoing || source.groupSummary || source.progress || source.key.isBlank()) return null
        val title = bounded(source.title, 600)
        val text = bounded(source.text, 600)
        val bigText = bounded(source.bigText, 900)
        val subText = bounded(source.subText, 300)
        val fields = listOf(title, text, bigText).filter(String::isNotBlank)
        if (fields.isEmpty() || fields.any { noise.containsMatchIn(it) }) return null

        val categorized = source.category == NAVIGATION_CATEGORY
        // Older Maps versions sometimes omit CATEGORY_NAVIGATION. Require an explicit navigation
        // action or both a next-turn distance and a maneuver phrase, never merely "Maps is running".
        val fallback = source.actionTitles.take(8).any { exitNavigation.matches(bounded(it, 100)) } ||
            (fields.any { leadingDistance.find(it) != null } && fields.any { englishInstruction.containsMatchIn(it) })
        if (!categorized && !fallback) return null

        var instruction = title.ifBlank { text.ifBlank { bigText } }
        var distance = ""
        var road = ""
        val titleDistance = leadingDistance.find(title)
        if (titleDistance != null) {
            distance = bounded(titleDistance.groupValues[1], 40)
            val remainder = title.substring(titleDistance.range.last + 1).replaceFirst(separator, "")
            if (remainder.isNotBlank()) {
                instruction = remainder
                road = text.takeUnless { it == instruction }.orEmpty()
            } else {
                instruction = text.ifBlank { bigText }
            }
        } else if (title.isNotBlank()) {
            // Most Maps versions expose instruction in title, road in text, and trip summary in
            // subText/bigText. Preserve these labels instead of interpreting times or street names.
            val textDistance = leadingDistance.find(text)
            if (textDistance != null && text.substring(textDistance.range.last + 1).isBlank()) {
                distance = bounded(textDistance.groupValues[1], 40)
            } else road = text.takeUnless { it == instruction }.orEmpty()
        }
        if (instruction.isBlank()) return null
        val summary = listOf(subText, bigText).filter { it.isNotBlank() && it != instruction && it != road && it != title }
            .distinct().joinToString(" · ")
        return Directions(
            sessionId = if (source.key.length <= 512) source.key else source.key.take(480) + "-" + source.key.hashCode(),
            instruction = bounded(instruction, 300), distance = distance,
            road = bounded(road, 300), summary = bounded(summary, 300), maneuver = maneuver(instruction))
    }

    /** Anchored source instructions only. A street such as "Right Lane Road" supplies no turn. */
    fun maneuver(instruction: String): String {
        val text = bounded(instruction, 300)
        return when {
            Regex("^make (?:a )?u[- ]turn (?:to the )?left\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "UTURN_LEFT"
            Regex("^make (?:a )?u[- ]turn (?:to the )?right\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "UTURN_RIGHT"
            Regex("^(?:turn (?:sharply |sharp )|sharp )left\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "SHARP_LEFT"
            Regex("^(?:turn (?:sharply |sharp )|sharp )right\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "SHARP_RIGHT"
            Regex("^(?:turn (?:slightly |slight )|slight )left\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "SLIGHT_LEFT"
            Regex("^(?:turn (?:slightly |slight )|slight )right\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "SLIGHT_RIGHT"
            Regex("^turn left\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "LEFT"
            Regex("^turn right\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "RIGHT"
            Regex("^keep left\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "FORK_LEFT"
            Regex("^keep right\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "FORK_RIGHT"
            Regex("^continue straight\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "STRAIGHT"
            Regex("^at the roundabout\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "ROUNDABOUT"
            Regex("^merge(?: onto| into| left| right|$)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "MERGE"
            Regex("^(?:you have arrived|you've arrived|arrive at your destination)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "ARRIVE"
            else -> "UNKNOWN"
        }
    }

    fun shouldReplaceOrdinary(enabled: Boolean, watchConnected: Boolean, watchSupported: Boolean): Boolean =
        enabled && watchConnected && watchSupported

    fun bounded(value: String?, limit: Int): String {
        if (value.isNullOrEmpty()) return ""
        var result = value.take(limit).filter { it >= ' ' || it == '\n' || it == '\t' }.trim()
        if (result.lastOrNull()?.isHighSurrogate() == true) result = result.dropLast(1)
        return result
    }
}

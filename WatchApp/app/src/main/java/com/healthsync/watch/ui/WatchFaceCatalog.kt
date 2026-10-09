package com.healthsync.watch.ui

/** The picker, persisted preferences and renderers share the same supported face ids. */
object WatchFaceCatalog {
    data class Entry(val id: String, val title: String, val description: String,
                     val native: Boolean = true, val clockShortcuts: Boolean = true)

    val entries = listOf(
        Entry("orbit", "Orbit", "Mint goal arc with health complications", native = true, clockShortcuts = true),
        Entry("classic", "Casio Retro", "Iconic Casio digital LCD with Illuminator backlight", native = true, clockShortcuts = true)
    )
    val styles = entries.map { it.id }
    fun isNative(style: String) = entries.any { it.id == style && it.native }
    fun entry(style: String) = entries.firstOrNull { it.id == style } ?: entries.first()
    fun showsClockShortcuts(style: String, enabled: Boolean) = enabled && entry(style).clockShortcuts

    val ambientEntries = listOf(
        Entry("face", "Match watch face", "Dim clock in the selected face's style"),
        Entry("digital", "Digital", "Simple time and date"),
        Entry("retro", "Retro LCD", "Dim seven-segment digits")
    )
    val ambientStyles = ambientEntries.map { it.id }

    fun normalizeAmbientStyle(value: String) = value.takeIf { it in ambientStyles } ?: "face"

    fun ambientStyleForFace(style: String) = when (style) {
        "classic" -> "retro"
        else -> "digital"
    }
}

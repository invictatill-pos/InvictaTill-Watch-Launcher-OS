package com.healthsync.watch.ui

import android.content.Context
import com.healthsync.watch.data.watchface.DynamicFaceStore

/** The picker, persisted preferences and renderers share the same supported face ids. */
object WatchFaceCatalog {
    data class Entry(val id: String, val title: String, val description: String,
                     val native: Boolean = true, val clockShortcuts: Boolean = true)

    val nativeEntries = listOf(
        Entry("orbit", "Orbit Pro", "Mint activity goal arc with live complications", native = true, clockShortcuts = true),
        Entry("chrono", "Chrono Ultra", "Luxury sports chronograph with luminous hands & live subdials", native = true, clockShortcuts = true),
        Entry("classic", "Retro Illuminator", "Vintage digital LCD with cyan backlight & health odometer", native = true, clockShortcuts = false),
        Entry("neon", "Cyberpunk HUD", "Futuristic matrix with ECG pulse wave & 4 neon themes", native = true, clockShortcuts = true),
        Entry("bauhaus", "Bauhaus Minimal", "Swiss luxury executive dial with fine batons & date aperture", native = true, clockShortcuts = true)
    )
    val entries = nativeEntries
    val styles get() = nativeEntries.map { it.id }

    fun allEntries(context: Context? = null): List<Entry> {
        val list = nativeEntries.toMutableList()
        if (context != null) {
            val installed = DynamicFaceStore.getInstance(context).listInstalledFaces()
            for (face in installed) {
                list.add(Entry(
                    id = "dynamic_${face.id}",
                    title = face.name,
                    description = face.description.ifBlank { "Dynamic ${face.type} dial" },
                    native = false,
                    clockShortcuts = true
                ))
            }
        }
        return list
    }

    fun isNative(style: String) = nativeEntries.any { it.id == style }

    fun isDynamic(style: String) = style.startsWith("dynamic_")

    fun entry(style: String, context: Context? = null): Entry {
        val direct = nativeEntries.firstOrNull { it.id == style }
        if (direct != null) return direct

        if (style.startsWith("dynamic_") && context != null) {
            val dynamicFace = DynamicFaceStore.getInstance(context).getFace(style)
            if (dynamicFace != null) {
                return Entry("dynamic_${dynamicFace.id}", dynamicFace.name, dynamicFace.description, native = false)
            }
        }
        return nativeEntries.first()
    }

    fun showsClockShortcuts(style: String, enabled: Boolean) = enabled

    val ambientEntries = listOf(
        Entry("face", "Match watch face", "Dim clock in the selected face's style"),
        Entry("digital", "Digital", "Simple time and date"),
        Entry("chrono_dim", "Chrono Luminous", "Minimal luminous outlines & markers")
    )
    val ambientStyles = ambientEntries.map { it.id }

    fun normalizeAmbientStyle(value: String) = value.takeIf { it in ambientStyles } ?: "face"

    fun ambientStyleForFace(style: String) = when (style) {
        "chrono", "classic", "bauhaus" -> "chrono_dim"
        else -> "digital"
    }
}

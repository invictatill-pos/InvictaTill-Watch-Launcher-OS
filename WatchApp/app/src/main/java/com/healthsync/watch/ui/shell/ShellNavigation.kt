package com.healthsync.watch.ui.shell

/** Home/Back history is independent of Android's external app tasks. */
internal class ShellNavigation {
    companion object { val PANELS = setOf("apps", "controls", "notifications", "fitness", "settings", "faces", "media") }
    private val history = mutableListOf<String>()
    val current: String? get() = history.lastOrNull()
    fun open(panel: String) {
        if (panel !in PANELS || current == panel) return
        val existing = history.indexOf(panel)
        if (existing >= 0) while (history.lastIndex > existing) history.removeAt(history.lastIndex)
        else history.add(panel)
    }
    fun back(): String? { if (history.isNotEmpty()) history.removeAt(history.lastIndex); return current }
    fun home() { history.clear() }
    fun snapshot(): ArrayList<String> = ArrayList(history)
    fun restore(saved: List<String>) { history.clear(); saved.filter { it in PANELS }.take(12).forEach { open(it) } }
}

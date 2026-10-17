package com.healthsync.phone.data.model

/** Current Google Maps notification state, independent of phone screen capture. */
data class NavigationPayload(
    val status: String = "REQUEST",
    val streamId: String = "",
    val sequence: Long = 0L,
    val sessionId: String = "",
    val instruction: String = "",
    val distance: String = "",
    val road: String = "",
    val summary: String = "",
    val maneuver: String = "UNKNOWN",
    val iconBase64: String = "",
    val message: String = ""
)

object NavigationProtocol {
    const val STALE_AFTER_MS = 20_000L
    const val HEARTBEAT_MS = 5_000L
    const val MAX_ICON_BYTES = 12_288
    const val MAX_ICON_BASE64 = 16_384
    const val MAX_ICON_SIDE = 96
    val STATUSES = setOf("ACTIVE", "IDLE", "DISABLED", "UNAVAILABLE")
    val MANEUVERS = setOf("UNKNOWN", "LEFT", "RIGHT", "SLIGHT_LEFT", "SLIGHT_RIGHT",
        "SHARP_LEFT", "SHARP_RIGHT", "STRAIGHT", "UTURN_LEFT", "UTURN_RIGHT",
        "ROUNDABOUT", "ARRIVE", "MERGE", "FORK_LEFT", "FORK_RIGHT")

    fun validState(value: NavigationPayload): Boolean = runCatching {
        value.status in STATUSES && value.streamId.matches(Regex("[A-Za-z0-9-]{1,64}")) &&
            value.sequence > 0 && value.sessionId.length <= 512 &&
            value.instruction.length <= 300 && value.distance.length <= 40 &&
            value.road.length <= 300 && value.summary.length <= 300 && value.message.length <= 300 &&
            value.maneuver in MANEUVERS && value.iconBase64.length <= MAX_ICON_BASE64 &&
            (value.status != "ACTIVE" || (value.sessionId.isNotBlank() && value.instruction.isNotBlank())) &&
            (value.status == "ACTIVE" || (value.instruction.isEmpty() && value.distance.isEmpty() &&
                value.road.isEmpty() && value.summary.isEmpty() && value.iconBase64.isEmpty() &&
                value.maneuver == "UNKNOWN"))
    }.getOrDefault(false)
}

package com.healthsync.watch.ui.navigation

import com.healthsync.watch.data.NavigationPayload
import com.healthsync.watch.data.NavigationProtocol
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure transport gate. A disconnected or expired route is never returned as usable guidance. */
internal class NavigationReceiverPolicy {
    data class Receipt(val accepted: Boolean, val maneuverChanged: Boolean = false)
    enum class Status { DISCONNECTED, WAITING, ACTIVE, IDLE, DISABLED, UNAVAILABLE, STALE }
    data class Snapshot(val connected: Boolean, val status: Status, val payload: NavigationPayload?,
        val receivedAt: Long?)

    var connected = false
        private set
    private var latest: NavigationPayload? = null
    private var receivedAt: Long? = null
    private var streamId: String? = null
    private var sequence = 0L
    private val retiredStreams = ArrayDeque<String>()

    fun connectionChanged(value: Boolean) {
        if (connected == value) return
        connected = value
        // Retain the sequence gate, but require a new current phone response on reconnect.
        latest = null
        receivedAt = null
    }

    fun receive(payload: NavigationPayload, now: Long): Receipt {
        if (!connected || now < 0 || !NavigationProtocol.validState(payload) ||
            payload.streamId in retiredStreams ||
            (payload.streamId == streamId && payload.sequence <= sequence)) return Receipt(false)
        val previous = snapshot(now).payload
        if (payload.streamId != streamId) {
            streamId?.let { retiredStreams.addLast(it) }
            while (retiredStreams.size > 16) retiredStreams.removeFirst()
        }
        val changed = meaningfulNavigationChange(previous, payload)
        streamId = payload.streamId
        sequence = payload.sequence
        latest = payload
        receivedAt = now
        return Receipt(true, changed)
    }

    fun snapshot(now: Long): Snapshot {
        if (!connected) return Snapshot(false, Status.DISCONNECTED, null, null)
        val p = latest ?: return Snapshot(true, Status.WAITING, null, null)
        val status = when (p.status) {
            "ACTIVE" -> if (receivedAt?.let { now >= it && now - it < NavigationProtocol.STALE_AFTER_MS } == true)
                Status.ACTIVE else Status.STALE
            "IDLE" -> Status.IDLE
            "DISABLED" -> Status.DISABLED
            else -> Status.UNAVAILABLE
        }
        return Snapshot(true, status, if (status == Status.ACTIVE || status in setOf(Status.IDLE,
            Status.DISABLED, Status.UNAVAILABLE)) p else null, receivedAt)
    }
}

/** Initial snapshots, distance changes, heartbeat/reconnect replays and unknown arrows stay silent. */
internal fun meaningfulNavigationChange(previous: NavigationPayload?, next: NavigationPayload): Boolean =
    previous?.status == "ACTIVE" && next.status == "ACTIVE" &&
        previous.streamId == next.streamId && previous.sessionId == next.sessionId &&
        next.maneuver != "UNKNOWN" &&
        (next.maneuver != previous.maneuver ||
            (next.road.isNotBlank() && previous.road.isNotBlank() && next.road != previous.road &&
                next.instruction.contains(next.road) && previous.instruction.contains(previous.road)))

/** Validate PNG signature, fixed IHDR dimensions and complete IEND before any pixel allocation. */
internal fun validNavigationPngBytes(bytes: ByteArray): Boolean {
    if (bytes.size !in 45..NavigationProtocol.MAX_ICON_BYTES) return false
    val signature = intArrayOf(137, 80, 78, 71, 13, 10, 26, 10)
    if (signature.indices.any { (bytes[it].toInt() and 255) != signature[it] }) return false
    if (pngInt(bytes, 8) != 13 || String(bytes, 12, 4, Charsets.US_ASCII) != "IHDR") return false
    if (!validNavigationIconBounds(pngInt(bytes, 16), pngInt(bytes, 20), "image/png")) return false
    val end = intArrayOf(0, 0, 0, 0, 73, 69, 78, 68, 174, 66, 96, 130)
    return end.indices.all { (bytes[bytes.size - end.size + it].toInt() and 255) == end[it] }
}

private fun pngInt(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 255) shl 24) or ((bytes[offset + 1].toInt() and 255) shl 16) or
        ((bytes[offset + 2].toInt() and 255) shl 8) or (bytes[offset + 3].toInt() and 255)

internal fun validNavigationIconBounds(width: Int, height: Int, mime: String?): Boolean =
    mime == "image/png" && width in 1..NavigationProtocol.MAX_ICON_SIDE && height in 1..NavigationProtocol.MAX_ICON_SIDE

internal data class NavigationBounds(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right get() = left + width
    val bottom get() = top + height
}

internal data class NavigationLayout(val toolbar: NavigationBounds, val content: NavigationBounds)

/** The entire scrolling viewport is an inscribed rectangle, including at large font sizes. */
internal fun navigationLayout(width: Int, height: Int, desiredTouchHeight: Int): NavigationLayout {
    val side = min(width, height).coerceAtLeast(1)
    val top = (height - side) / 2
    val touch = desiredTouchHeight.coerceAtLeast(1).coerceAtMost((side * .14f).roundToInt().coerceAtLeast(1))
    fun bounds(fraction: Float, y: Int, h: Int) = NavigationBounds(
        ((width - side * fraction) / 2f).roundToInt(), y, (side * fraction).roundToInt(), h)
    val toolbar = bounds(.60f, top + (side * .12f).roundToInt(), touch)
    val contentTop = toolbar.bottom + (side * .015f).roundToInt()
    return NavigationLayout(toolbar, bounds(.72f, contentTop, (top + side * .82f).roundToInt() - contentTop))
}

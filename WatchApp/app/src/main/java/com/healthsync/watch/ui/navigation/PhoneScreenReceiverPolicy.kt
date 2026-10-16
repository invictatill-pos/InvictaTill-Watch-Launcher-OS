package com.healthsync.watch.ui.navigation

import com.healthsync.watch.data.PhoneScreenPayload
import com.healthsync.watch.data.PhoneScreenProtocol

/** Pure session gate. The bridge serializes calls; generation invalidates any in-progress decode. */
internal class PhoneScreenReceiverPolicy {
    data class DecodeTicket(val generation: Long, val sessionId: String, val sequence: Long)

    var connected = false
        private set
    var sessionId: String? = null
        private set
    var lastSequence = 0L
        private set
    var frameReceivedAt: Long? = null
        private set
    var message = "Connect your phone, then share Google Maps from HealthSync."
        private set
    private var generation = 0L
    private val closedSessions = ArrayDeque<String>()

    fun connectionChanged(value: Boolean) {
        connected = value
        if (!value) clear("Phone disconnected. Reconnect and start sharing again.")
        else if (sessionId == null) message = "On phone: HealthSync → Device → Google Maps on watch → Share Google Maps."
    }

    /** A repeated START is an ACK handshake, never a new freshness timestamp. */
    fun start(p: PhoneScreenPayload): Boolean {
        if (!connected || p.kind != "start" || p.sequence != 0L ||
            !PhoneScreenProtocol.validControl(p) || p.sessionId in closedSessions) return false
        if (sessionId == p.sessionId) return true
        clear("Waiting for the phone screen…")
        sessionId = p.sessionId
        return true
    }

    fun beginFrame(p: PhoneScreenPayload): DecodeTicket? {
        if (!connected || !PhoneScreenProtocol.validFrame(p) ||
            p.sessionId != sessionId || p.sequence <= lastSequence) return null
        return DecodeTicket(generation, p.sessionId, p.sequence)
    }

    fun commitFrame(ticket: DecodeTicket, now: Long): Boolean {
        if (!connected || ticket.generation != generation || sessionId != ticket.sessionId ||
            ticket.sequence <= lastSequence) return false
        lastSequence = ticket.sequence
        frameReceivedAt = now
        message = "Live phone view"
        return true
    }

    fun stop(p: PhoneScreenPayload): Boolean {
        if (p.kind != "stop" || !PhoneScreenProtocol.validControl(p) ||
            (sessionId != null && sessionId != p.sessionId)) return false
        clear(p.message.orEmpty().ifBlank { "Phone sharing stopped. Start again on your phone." })
        return true
    }

    fun stopLocally(): String? {
        val stopped = sessionId
        clear("Stopped on watch. If your phone still shows sharing, stop it on the phone too.")
        return stopped
    }

    fun isFresh(now: Long): Boolean = connected && sessionId != null &&
        frameReceivedAt?.let { now >= it && now - it < PhoneScreenProtocol.STALE_AFTER_MS } == true

    private fun clear(reason: String) {
        sessionId?.let {
            if (it !in closedSessions) closedSessions.addLast(it)
            while (closedSessions.size > 16) closedSessions.removeFirst()
        }
        generation++
        sessionId = null
        lastSequence = 0
        frameReceivedAt = null
        message = reason.take(160)
    }
}

/** Reject non-JPEG, oversized and malformed data before BitmapFactory allocates any pixels. */
internal fun validPhoneJpegBytes(bytes: ByteArray): Boolean =
    bytes.size in 4..PhoneScreenProtocol.MAX_IMAGE_BYTES &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
        bytes[bytes.lastIndex - 1] == 0xFF.toByte() && bytes.last() == 0xD9.toByte()

internal fun validPhoneFrameBounds(expectedWidth: Int, expectedHeight: Int, width: Int, height: Int, mime: String?): Boolean =
    mime == "image/jpeg" && width == expectedWidth && height == expectedHeight &&
        width in 1..PhoneScreenProtocol.MAX_EDGE && height in 1..PhoneScreenProtocol.MAX_EDGE &&
        width.toLong() * height <= PhoneScreenProtocol.MAX_PIXELS

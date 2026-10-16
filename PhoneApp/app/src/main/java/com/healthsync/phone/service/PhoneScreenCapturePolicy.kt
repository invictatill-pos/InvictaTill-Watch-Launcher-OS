package com.healthsync.phone.service

import com.healthsync.phone.data.model.PhoneScreenProtocol
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

data class PhoneCaptureSize(val width: Int, val height: Int)

/** Revocation is synchronous; a delayed capture worker cannot reactivate a canceled generation. */
class PhoneScreenSessionPolicy {
    private var generation = 0L
    private var activeId: String? = null
    private var activePeer: String? = null

    @Synchronized fun snapshotGeneration(): Long = generation

    @Synchronized fun activate(sessionId: String, peer: String?, expectedGeneration: Long): Boolean {
        if (generation != expectedGeneration || activeId != null || peer.isNullOrBlank()) return false
        activeId = sessionId
        activePeer = peer
        return true
    }

    @Synchronized fun isActive(sessionId: String, connected: Boolean, currentPeer: String?): Boolean =
        connected && activeId == sessionId && currentPeer != null && activePeer == currentPeer

    @Synchronized fun invalidate() {
        generation++
        activeId = null
        activePeer = null
    }

    /** Old callbacks/STOP messages cannot revoke a newer approved session. */
    @Synchronized fun deactivateOwned(sessionId: String?): Boolean {
        if (sessionId == null || activeId != sessionId) return false
        invalidate()
        return true
    }
}

/** A consent result belongs to one attempt and one Bluetooth peer, even across asynchronous startup. */
class PhoneScreenConsentPolicy {
    private var pendingId: String? = null
    private var pendingAddress: String? = null
    private var pendingGeneration: Long? = null

    @Synchronized fun begin(id: String, deviceAddress: String?, sessionGeneration: Long = 0L) {
        pendingId = id
        pendingAddress = deviceAddress
        pendingGeneration = sessionGeneration
    }

    @Synchronized fun isPending(id: String): Boolean = pendingId == id

    @Synchronized fun generationFor(id: String): Long? = if (pendingId == id) pendingGeneration else null

    @Synchronized fun consume(id: String, deviceAddress: String?): Boolean {
        if (pendingId != id || pendingAddress != deviceAddress) return false
        invalidate()
        return true
    }

    @Synchronized fun invalidate() {
        pendingId = null
        pendingAddress = null
        pendingGeneration = null
    }
}

/** Bound the capture surface itself, before allocating any image buffers. */
object PhoneScreenCapturePolicy {
    fun boundedSize(width: Int, height: Int): PhoneCaptureSize {
        require(width > 0 && height > 0)
        val scale = min(1.0, min(
            PhoneScreenProtocol.MAX_EDGE.toDouble() / maxOf(width, height),
            sqrt(PhoneScreenProtocol.MAX_PIXELS.toDouble() / (width.toDouble() * height))
        ))
        return PhoneCaptureSize(maxOf(1, floor(width * scale).toInt()), maxOf(1, floor(height * scale).toInt()))
    }
}

/** Owned by the capture thread: START must be acknowledged before any image can leave. */
class PhoneScreenFlowControl(val sessionId: String, startedAt: Long) {
    var awaitingSequence: Long? = 0L
        private set
    private var sentAt = startedAt
    private var nextSequence = 1L
    private var expired = false

    fun acknowledge(session: String, sequence: Long, now: Long): Boolean {
        if (session != sessionId || sequence != awaitingSequence || timedOut(now)) return false
        awaitingSequence = null
        return true
    }

    fun canCapture(now: Long): Boolean = !expired && awaitingSequence == null && now - sentAt >= PhoneScreenProtocol.FRAME_INTERVAL_MS

    fun reserveFrame(now: Long): Long? {
        if (!canCapture(now)) return null
        val sequence = nextSequence++
        awaitingSequence = sequence
        sentAt = now
        return sequence
    }

    fun timedOut(now: Long): Boolean {
        if (awaitingSequence != null && now - sentAt >= PhoneScreenProtocol.ACK_TIMEOUT_MS) expired = true
        return expired
    }
}

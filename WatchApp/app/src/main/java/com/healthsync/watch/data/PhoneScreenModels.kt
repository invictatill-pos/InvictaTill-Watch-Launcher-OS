package com.healthsync.watch.data

data class PhoneScreenPayload(
    val kind: String = "",
    val sessionId: String = "",
    val sequence: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val imageBase64: String = "",
    val message: String = ""
)

/** A single bounded JPEG in flight. No image is persisted or treated as a route instruction. */
object PhoneScreenProtocol {
    const val MAX_IMAGE_BYTES = 48 * 1024
    const val MAX_BASE64_CHARS = 65_536
    const val MAX_EDGE = 960
    const val MAX_PIXELS = 640_000
    const val FRAME_INTERVAL_MS = 1_500L
    const val ACK_TIMEOUT_MS = 8_000L
    const val STALE_AFTER_MS = 6_000L

    fun validSession(id: String?): Boolean =
        id != null && id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))

    fun validControl(p: PhoneScreenPayload): Boolean =
        p.kind in setOf("start", "ack", "stop", "request") &&
            (if (p.kind == "request") p.sessionId.isNullOrEmpty() || validSession(p.sessionId) else validSession(p.sessionId)) &&
            p.sequence >= 0 && p.message.orEmpty().length <= 160 &&
            p.imageBase64.isNullOrEmpty() && p.width == 0 && p.height == 0

    fun validFrame(p: PhoneScreenPayload): Boolean =
        p.kind == "frame" && validSession(p.sessionId) && p.sequence > 0 &&
            p.width in 1..MAX_EDGE && p.height in 1..MAX_EDGE &&
            p.width.toLong() * p.height <= MAX_PIXELS &&
            !p.imageBase64.isNullOrEmpty() && p.imageBase64.length <= MAX_BASE64_CHARS &&
            p.message.orEmpty().length <= 160
}

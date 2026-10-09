package com.healthsync.phone.service

/** Android-independent content rules, shared by new alerts and reconnect snapshots. */
internal object NotificationMirrorPolicy {
    const val MAX_TITLE = 500
    const val MAX_TEXT = 5_000
    const val MAX_ACTIVE = 100

    data class Message(val text: String?, val sender: String? = null, val attachment: Boolean = false)
    data class Source(
        val title: String? = null,
        val text: String? = null,
        val bigText: String? = null,
        val lines: List<String> = emptyList(),
        val ticker: String? = null,
        val conversationTitle: String? = null,
        val messages: List<Message> = emptyList(),
        val groupSummary: Boolean = false
    )
    data class Content(val title: String, val text: String, val conversationTitle: String) {
        val signature: String get() = "$title\u0000$text\u0000$conversationTitle"
    }
    data class Snapshot(val content: String, val metadata: String)
    data class Decision(val forward: Boolean, val silent: Boolean, val saveHistory: Boolean)

    fun extract(source: Source, appLabel: String): Content? {
        if (source.groupSummary) return null
        val latest = source.messages.takeLast(12).lastOrNull { bounded(it.text, MAX_TEXT).isNotBlank() || it.attachment }
        val body = if (latest != null) bounded(latest.text, MAX_TEXT).ifBlank { "Attachment" }
        else sequenceOf(source.bigText, source.text, source.lines.takeLast(12).joinToString("\n"), source.ticker)
            .map { bounded(it, MAX_TEXT) }.firstOrNull { it.isNotBlank() }.orEmpty()
        val originalTitle = bounded(source.title, MAX_TITLE)
        val conversation = bounded(source.conversationTitle, MAX_TITLE)
        if (originalTitle.isBlank() && body.isBlank() && conversation.isBlank()) return null
        val title = bounded(latest?.sender, MAX_TITLE).ifBlank {
            originalTitle.ifBlank { conversation.ifBlank { bounded(appLabel, MAX_TITLE) } }
        }
        return Content(title, body, conversation)
    }

    fun decide(previous: Snapshot?, current: Snapshot, replay: Boolean, sourceSilent: Boolean,
               ongoing: Boolean, onlyAlertOnce: Boolean): Decision {
        val contentChanged = previous?.content != current.content
        val metadataChanged = previous?.metadata != current.metadata
        return Decision(
            forward = replay || contentChanged || metadataChanged,
            silent = replay || sourceSilent || (previous != null && (!contentChanged || ongoing || onlyAlertOnce)),
            saveHistory = !replay && contentChanged
        )
    }

    fun staleKeys(known: Set<String>, activeAllowed: Set<String>): Set<String> = known - activeAllowed

    fun shouldMirrorDialerCall(syncCalls: Boolean, isDialerCall: Boolean, canonicalCallActive: Boolean): Boolean =
        !syncCalls || !isDialerCall || !canonicalCallActive

    fun replyError(packageName: String?, text: String?, title: String?, key: String?): String? = when {
        packageName.isNullOrBlank() || packageName.length > 255 -> "Invalid notification source"
        text.isNullOrBlank() || text.length > 2_000 -> "Reply must contain 1 to 2,000 characters"
        (key?.length ?: 0) > 2_048 || (title?.length ?: 0) > MAX_TITLE -> "Invalid notification reference"
        key.isNullOrBlank() && title.isNullOrBlank() -> "This notification cannot be identified"
        else -> null
    }

    fun bounded(value: String?, limit: Int): String {
        if (value.isNullOrEmpty()) return ""
        var text = value.take(limit).filter { it >= ' ' || it == '\n' || it == '\t' }.trim()
        // Do not split a UTF-16 character when limiting a large notification.
        if (text.lastOrNull()?.isHighSurrogate() == true) text = text.dropLast(1)
        return text
    }
}

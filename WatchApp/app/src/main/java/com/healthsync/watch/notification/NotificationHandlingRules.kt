package com.healthsync.watch.notification

import com.healthsync.watch.data.NotificationPayload

/** Dismissing prompts with a delete/input/full-screen intent can change the originating app's state. */
internal fun canTakeOverWatchNotification(
    enabled: Boolean,
    clearable: Boolean,
    ongoing: Boolean,
    groupSummary: Boolean,
    hasDeleteIntent: Boolean,
    hasRemoteInput: Boolean,
    hasFullScreenIntent: Boolean,
    hasReadableContent: Boolean = true
): Boolean = enabled && clearable && !ongoing && !groupSummary && !hasDeleteIntent && !hasRemoteInput && !hasFullScreenIntent && hasReadableContent

/** A saved card must never execute an action belonging to a newer reuse of the same Android key. */
internal fun matchesWatchNotificationRevision(entry: NotificationInboxEntry, payload: NotificationPayload): Boolean =
    entry.source == NotificationInboxStore.SOURCE_WATCH && entry.payload.packageName == payload.packageName &&
        entry.payload.notificationKey.isNotBlank() && entry.payload.notificationKey == payload.notificationKey &&
        entry.payload.time == payload.time && entry.payload.title == payload.title && entry.payload.text == payload.text

internal fun canClearInboxEntry(entry: NotificationInboxEntry, protectedWatchNotifications: List<NotificationPayload>): Boolean =
    protectedWatchNotifications.none { matchesWatchNotificationRevision(entry, it) }

internal const val RETAINED_WATCH_ACTION_LIFETIME_MS = 24 * 60 * 60 * 1000L

internal fun retainedWatchActionIsUsable(entry: NotificationInboxEntry, payload: NotificationPayload, capturedAt: Long, now: Long): Boolean =
    matchesWatchNotificationRevision(entry, payload) && now - capturedAt in 0L..RETAINED_WATCH_ACTION_LIFETIME_MS

/** Block pre-clear queued events, preserving updates and new keys arriving during a clear. */
internal class NotificationDismissalGate {
    private data class Cutoff(val time: Long, val content: MutableSet<Pair<String, String>>)
    private val cutoffs = object : LinkedHashMap<Pair<String, String>, Cutoff>(512, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, Cutoff>?) = size > 512
    }
    fun mark(payload: NotificationPayload) {
        val key = payload.packageName to payload.notificationKey
        val existing = cutoffs[key]
        when {
            existing == null || payload.time > existing.time -> cutoffs[key] = Cutoff(payload.time, mutableSetOf(payload.title to payload.text))
            payload.time == existing.time -> existing.content.add(payload.title to payload.text)
        }
    }
    fun shouldIgnore(payload: NotificationPayload): Boolean {
        val cutoff = cutoffs[payload.packageName to payload.notificationKey] ?: return false
        return payload.time < cutoff.time || (payload.time == cutoff.time && (payload.title to payload.text) in cutoff.content)
    }
    fun remove(packageName: String, key: String) { cutoffs.remove(packageName to key) }
}

data class NotificationClearAllResult(val removedCopies: Int, val keptActiveWatch: Int)

data class WatchNotificationAction(val index: Int, val title: String)

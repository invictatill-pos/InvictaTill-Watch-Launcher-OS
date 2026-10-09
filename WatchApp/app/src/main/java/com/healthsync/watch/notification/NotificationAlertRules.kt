package com.healthsync.watch.notification

import com.healthsync.watch.data.NotificationPayload
import com.google.gson.Gson
import com.google.gson.JsonObject

/** Gson does not apply Kotlin constructor defaults when older phones omit added fields. */
internal fun parseNotificationPayload(json: String, gson: Gson): NotificationPayload {
    val wire = gson.fromJson(json, JsonObject::class.java)
    val p = gson.fromJson(wire, NotificationPayload::class.java)
    return p.copy(packageName = p.packageName.orEmpty(), notificationKey = p.notificationKey.orEmpty(),
        appLabel = p.appLabel.orEmpty().take(200), title = p.title.orEmpty().take(1000), text = p.text.orEmpty().take(8000),
        ticker = p.ticker.orEmpty(), category = p.category.orEmpty(), channelName = p.channelName.orEmpty(), conversationTitle = p.conversationTitle.orEmpty(),
        isActive = if (wire.has("isActive")) p.isActive else true)
}

/** Replays, background progress, and native calls must never steal a call screen. */
internal fun shouldInterruptForNotification(payload: NotificationPayload, changed: Boolean, callActive: Boolean,
    source: String = NotificationInboxStore.SOURCE_PHONE): Boolean =
    changed && payload.isActive && !payload.isSilent && (!payload.isOngoing || payload.category.orEmpty() == "call") && !callActive &&
        (source != NotificationInboxStore.SOURCE_WATCH || payload.category.orEmpty() != "call") &&
        (payload.title.orEmpty().isNotBlank() || payload.text.orEmpty().isNotBlank())

internal fun canAutomaticallyShowNotification(sdk: Int, foreground: Boolean, interactive: Boolean, locked: Boolean,
    popupEnabled: Boolean, interruptionAllowed: Boolean): Boolean =
    popupEnabled && interruptionAllowed && !locked && (sdk < 29 || (foreground && interactive))

internal fun replyResultMatches(entry: NotificationInboxEntry, requestId: Long, key: String): Boolean =
    requestId > 0 && entry.source == NotificationInboxStore.SOURCE_PHONE && entry.replyRequestId == requestId &&
        entry.payload.notificationKey == key && entry.replyState in listOf("pending", "unknown")

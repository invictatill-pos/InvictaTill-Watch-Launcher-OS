package com.healthsync.watch.notification

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.healthsync.watch.data.NotificationPayload
import java.security.MessageDigest

data class NotificationInboxEntry(
    val id: Long,
    val payload: NotificationPayload,
    val receivedAt: Long,
    val isRead: Boolean,
    val replyRequested: Boolean,
    val source: String = "phone",
    val replyState: String = "",
    val replyRequestId: Long = 0L,
    val replyRequestedAt: Long = 0L,
    val replyError: String = ""
)

/** Device-local inbox. No notification contents are backed up or sent anywhere. */
class NotificationInboxStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "watch_notifications.db", null, 3) {
    companion object {
        const val ACTION_CHANGED = "com.healthsync.watch.NOTIFICATION_INBOX_CHANGED"
        const val SOURCE_PHONE = "phone"
        const val SOURCE_WATCH = "watch"
        private const val LIMIT = 100
        private const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
        @Volatile private var instance: NotificationInboxStore? = null

        fun getInstance(context: Context): NotificationInboxStore = instance ?: synchronized(this) {
            instance ?: NotificationInboxStore(context).also { instance = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE inbox (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            identity TEXT NOT NULL UNIQUE,
            package_name TEXT NOT NULL,
            app_label TEXT NOT NULL,
            title TEXT NOT NULL,
            body TEXT NOT NULL,
            notification_key TEXT NOT NULL,
            posted_at INTEGER NOT NULL,
            received_at INTEGER NOT NULL,
            can_reply INTEGER NOT NULL,
            is_read INTEGER NOT NULL DEFAULT 0,
            reply_requested INTEGER NOT NULL DEFAULT 0,
            source TEXT NOT NULL DEFAULT 'phone',
            category TEXT NOT NULL DEFAULT '',
            is_ongoing INTEGER NOT NULL DEFAULT 0,
            is_silent INTEGER NOT NULL DEFAULT 0,
            channel_name TEXT NOT NULL DEFAULT '',
            conversation_title TEXT NOT NULL DEFAULT '',
            is_active INTEGER NOT NULL DEFAULT 1,
            reply_state TEXT NOT NULL DEFAULT '',
            reply_request_id INTEGER NOT NULL DEFAULT 0,
            reply_requested_at INTEGER NOT NULL DEFAULT 0,
            reply_error TEXT NOT NULL DEFAULT ''
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE inbox ADD COLUMN source TEXT NOT NULL DEFAULT 'phone'")
        if (oldVersion < 3) {
            listOf("category TEXT NOT NULL DEFAULT ''", "is_ongoing INTEGER NOT NULL DEFAULT 0",
                "is_silent INTEGER NOT NULL DEFAULT 0", "channel_name TEXT NOT NULL DEFAULT ''",
                "conversation_title TEXT NOT NULL DEFAULT ''", "is_active INTEGER NOT NULL DEFAULT 1",
                "reply_state TEXT NOT NULL DEFAULT ''", "reply_request_id INTEGER NOT NULL DEFAULT 0",
                "reply_requested_at INTEGER NOT NULL DEFAULT 0", "reply_error TEXT NOT NULL DEFAULT ''")
                .forEach { db.execSQL("ALTER TABLE inbox ADD COLUMN $it") }
        }
    }

    /** Called on the Bluetooth reader thread before alerting, so closing an alert loses nothing. */
    @Synchronized fun record(payload: NotificationPayload, now: Long = System.currentTimeMillis(), source: String = SOURCE_PHONE): Long {
        require(source == SOURCE_PHONE || source == SOURCE_WATCH)
        // Keep phone identity intact; a malformed oversized identity must never become a reply target.
        require(payload.packageName.orEmpty().length <= 256 && payload.notificationKey.orEmpty().length <= 2048)
        val p = payload.copy(packageName = payload.packageName.orEmpty(),
            appLabel = payload.appLabel.orEmpty().take(200), title = payload.title.orEmpty().take(1000),
            text = payload.text.orEmpty().take(8000), ticker = payload.ticker.orEmpty(), notificationKey = payload.notificationKey.orEmpty(),
            category = payload.category.orEmpty(), channelName = payload.channelName.orEmpty(), conversationTitle = payload.conversationTitle.orEmpty())
        val identity = notificationInboxIdentity(p.packageName, p.notificationKey, p.title, p.text, p.time, source)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = db.query("inbox", null, "identity = ?", arrayOf(identity), null, null, null)
                .use { if (it.moveToFirst()) entry(it) else null }
            val sameContent = existing?.payload?.let { sameNotificationContent(it, p) } == true
            val values = ContentValues().apply {
                put("identity", identity); put("package_name", p.packageName); put("app_label", p.appLabel)
                put("title", p.title); put("body", p.text); put("notification_key", p.notificationKey)
                put("posted_at", p.time); put("received_at", now)
                put("source", source)
                put("can_reply", if (source == SOURCE_PHONE && p.canReply && p.isActive && p.packageName.isNotBlank() && p.notificationKey.isNotBlank()) 1 else 0)
                put("category", p.category.orEmpty().take(80)); put("is_ongoing", if (p.isOngoing) 1 else 0)
                put("is_silent", if (p.isSilent) 1 else 0); put("channel_name", p.channelName.orEmpty().take(200))
                put("conversation_title", p.conversationTitle.orEmpty().take(1000)); put("is_active", if (p.isActive) 1 else 0)
                put("is_read", if (sameContent && existing?.isRead == true) 1 else 0)
                put("reply_requested", if (sameContent && existing?.replyRequested == true) 1 else 0)
                put("reply_state", if (sameContent) existing?.replyState.orEmpty() else "")
                put("reply_request_id", if (sameContent) existing?.replyRequestId ?: 0L else 0L)
                put("reply_requested_at", if (sameContent) existing?.replyRequestedAt ?: 0L else 0L)
                put("reply_error", if (sameContent) existing?.replyError.orEmpty() else "")
            }
            val id = if (existing != null) {
                db.update("inbox", values, "_id = ?", arrayOf(existing.id.toString()))
                existing.id
            } else db.insertOrThrow("inbox", null, values)
            prune(db, now)
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    /** Persistent content dedupe survives reconnects and process restarts. */
    @Synchronized fun recordDelivery(payload: NotificationPayload, now: Long = System.currentTimeMillis(), source: String = SOURCE_PHONE): Pair<Long, Boolean> {
        val identity = notificationInboxIdentity(payload.packageName.orEmpty(), payload.notificationKey.orEmpty(),
            payload.title.orEmpty().take(1000), payload.text.orEmpty().take(8000), payload.time, source)
        val previous = readableDatabase.query("inbox", null, "identity = ?", arrayOf(identity), null, null, null)
            .use { if (it.moveToFirst()) entry(it) else null }
        val changed = previous == null || !sameNotificationContent(previous.payload, payload)
        return record(payload, now, source) to changed
    }

    @Synchronized fun read(now: Long = System.currentTimeMillis()): List<NotificationInboxEntry> {
        prune(writableDatabase, now)
        expireReplies(now)
        return readableDatabase.query("inbox", null, null, null, null, null, "received_at DESC, _id DESC", LIMIT.toString())
            .use { cursor -> buildList { while (cursor.moveToNext()) add(entry(cursor)) } }
    }

    @Synchronized fun get(id: Long): NotificationInboxEntry? {
        expireReplies(System.currentTimeMillis())
        return readableDatabase.query("inbox", null, "_id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) entry(it) else null }
    }

    /** Removal retains readable history but revokes all phone reply targets. */
    @Synchronized fun deactivatePhoneKey(key: String) {
        if (key.isBlank()) return
        writableDatabase.update("inbox", ContentValues().apply { put("is_active", 0); put("can_reply", 0) },
            "source = ? AND notification_key = ?", arrayOf(SOURCE_PHONE, key))
    }

    @Synchronized fun deactivatePhoneReplies() {
        writableDatabase.update("inbox", ContentValues().apply { put("is_active", 0); put("can_reply", 0) },
            "source = ?", arrayOf(SOURCE_PHONE))
    }

    /** A reconnect snapshot also revokes notifications removed while Bluetooth was offline. */
    @Synchronized fun reconcilePhoneActiveKeys(keys: List<String>) {
        val active = keys.take(1000).filter { it.isNotBlank() && it.length <= 2048 }.toSet()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stale = db.query("inbox", arrayOf("_id", "notification_key"), "source = ? AND is_active = 1",
                arrayOf(SOURCE_PHONE), null, null, null).use { cursor -> buildList {
                    while (cursor.moveToNext()) if (cursor.getString(1) !in active) add(cursor.getLong(0))
                } }
            stale.forEach { id -> db.update("inbox", ContentValues().apply { put("is_active", 0); put("can_reply", 0) },
                "_id = ?", arrayOf(id.toString())) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized fun beginReply(displayed: NotificationInboxEntry, requestId: Long, now: Long): Boolean {
        if (requestId <= 0 || !isCurrentInboxReplyTarget(displayed, get(displayed.id), now)) return false
        return writableDatabase.update("inbox", ContentValues().apply {
            put("reply_requested", 1); put("reply_state", "pending"); put("reply_request_id", requestId)
            put("reply_requested_at", now); put("reply_error", "")
        }, "_id = ?", arrayOf(displayed.id.toString())) == 1
    }

    /** A late result cannot affect a replacement message or another reply attempt. */
    @Synchronized fun completeReply(requestId: Long, key: String, success: Boolean, error: String = ""): Boolean {
        if (requestId <= 0 || key.isBlank()) return false
        val pending = readableDatabase.query("inbox", null, "source = ? AND notification_key = ? AND reply_request_id = ?",
            arrayOf(SOURCE_PHONE, key, requestId.toString()), null, null, null, "1")
            .use { if (it.moveToFirst()) entry(it) else null } ?: return false
        if (!replyResultMatches(pending, requestId, key)) return false
        return writableDatabase.update("inbox", ContentValues().apply {
            put("reply_requested", if (success) 1 else 0); put("reply_state", if (success) "sent" else "failed")
            put("reply_error", error.take(200))
        }, "source = ? AND notification_key = ? AND reply_request_id = ? AND reply_state IN ('pending', 'unknown')",
            arrayOf(SOURCE_PHONE, key, requestId.toString())) > 0
    }

    private fun expireReplies(now: Long) {
        // A lost acknowledgement is ambiguous. Prevent duplicate sends until a new message arrives.
        writableDatabase.update("inbox", ContentValues().apply { put("reply_state", "unknown") },
            "reply_state = 'pending' AND reply_requested_at <= ?", arrayOf((now - REPLY_TIMEOUT_MS).toString()))
    }

    @Synchronized fun markRead(id: Long) {
        writableDatabase.update("inbox", ContentValues().apply { put("is_read", 1) }, "_id = ?", arrayOf(id.toString()))
    }

    /** Dismisses only the saved watch copy; it does not dismiss the phone notification. */
    @Synchronized fun delete(id: Long): Boolean =
        writableDatabase.delete("inbox", "_id = ?", arrayOf(id.toString())) > 0

    /** An old card must not remove a newer message that reused its native notification key. */
    @Synchronized fun delete(displayed: NotificationInboxEntry): Boolean = writableDatabase.delete("inbox",
        "_id = ? AND source = ? AND notification_key = ? AND title = ? AND body = ? AND posted_at = ?",
        arrayOf(displayed.id.toString(), displayed.source, displayed.payload.notificationKey, displayed.payload.title,
            displayed.payload.text, displayed.payload.time.toString())) > 0

    /** Clear a snapshot, retaining any notification that arrived or changed during the operation. */
    @Synchronized fun deleteAll(displayed: List<NotificationInboxEntry>): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val removed = displayed.count { delete(it) }
            db.setTransactionSuccessful()
            return removed
        } finally { db.endTransaction() }
    }

    /** Android lockdown must remove mirrored notification contents too. */
    @Synchronized fun deleteWatchKey(packageName: String, key: String) {
        writableDatabase.delete("inbox", "source = ? AND package_name = ? AND notification_key = ?",
            arrayOf(SOURCE_WATCH, packageName, key))
    }

    @Synchronized fun markAllRead() {
        writableDatabase.update("inbox", ContentValues().apply { put("is_read", 1) }, "is_read = 0", null)
    }

    @Synchronized fun open(id: Long): NotificationInboxEntry? = get(id)?.also { markRead(id) }?.copy(isRead = true)

    /** Match the displayed revision too: a new message can reuse the same notification key. */
    @Synchronized fun markReplyRequested(displayed: NotificationInboxEntry) {
        if (displayed.source != SOURCE_PHONE) return
        writableDatabase.update("inbox", ContentValues().apply { put("reply_requested", 1) },
            "_id = ? AND notification_key = ? AND title = ? AND body = ? AND posted_at = ?",
            arrayOf(displayed.id.toString(), displayed.payload.notificationKey, displayed.payload.title,
                displayed.payload.text, displayed.payload.time.toString()))
    }

    @Synchronized fun clear() { writableDatabase.delete("inbox", null, null) }

    private fun prune(db: SQLiteDatabase, now: Long) {
        db.delete("inbox", "received_at < ?", arrayOf((now - RETENTION_MS).toString()))
        db.execSQL("DELETE FROM inbox WHERE _id NOT IN (SELECT _id FROM inbox ORDER BY received_at DESC, _id DESC LIMIT $LIMIT)")
    }

    private fun entry(c: Cursor): NotificationInboxEntry {
        fun string(name: String) = c.getString(c.getColumnIndexOrThrow(name))
        fun long(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
        return NotificationInboxEntry(long("_id"), NotificationPayload(string("package_name"), string("app_label"),
            string("title"), string("body"), time = long("posted_at"), canReply = long("can_reply") != 0L,
            notificationKey = string("notification_key"), category = string("category"), isOngoing = long("is_ongoing") != 0L,
            isSilent = long("is_silent") != 0L, channelName = string("channel_name"), conversationTitle = string("conversation_title"),
            isActive = long("is_active") != 0L), long("received_at"), long("is_read") != 0L, long("reply_requested") != 0L,
            string("source"), string("reply_state"), long("reply_request_id"), long("reply_requested_at"), string("reply_error"))
    }
}

internal fun notificationInboxIdentity(packageName: String, key: String, title: String, text: String, time: Long, source: String = "phone"): String {
    // Length delimiters avoid ambiguous joins, including titles containing separator characters.
    val contentFields = if (key.isNotBlank()) listOf(packageName, key) else listOf(packageName, title, text, time.toString())
    // Preserve v1 phone hashes during migration so the next phone update reuses its saved row.
    val fields = if (source == "phone") contentFields else listOf("watch") + contentFields
    val input = fields.joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal fun canReplyToInboxEntry(entry: NotificationInboxEntry, now: Long = System.currentTimeMillis()): Boolean =
    entry.source == "phone" && entry.payload.isActive && entry.payload.canReply && entry.payload.packageName.isNotBlank() && entry.payload.notificationKey.isNotBlank() &&
        !entry.replyRequested && now - entry.receivedAt in 0L..24 * 60 * 60 * 1000L

internal fun isCurrentInboxReplyTarget(displayed: NotificationInboxEntry, current: NotificationInboxEntry?, now: Long = System.currentTimeMillis()): Boolean =
    current != null && current.id == displayed.id && current.source == displayed.source && current.payload == displayed.payload && canReplyToInboxEntry(current, now)

internal const val REPLY_TIMEOUT_MS = 20_000L

internal fun sameNotificationContent(first: NotificationPayload, second: NotificationPayload): Boolean =
    first.packageName.orEmpty() == second.packageName.orEmpty() && first.notificationKey.orEmpty() == second.notificationKey.orEmpty() &&
        first.title.orEmpty() == second.title.orEmpty().take(1000) && first.text.orEmpty() == second.text.orEmpty().take(8000) &&
        first.conversationTitle.orEmpty() == second.conversationTitle.orEmpty().take(1000)

fun notificationReplyStatus(entry: NotificationInboxEntry): String = when (entry.replyState) {
    "pending" -> "Sending reply…"
    "sent" -> "Reply sent to app"
    "failed" -> entry.replyError.ifBlank { "Reply failed. Try again." }
    "unknown" -> "Reply not confirmed. Check your phone."
    else -> if (entry.replyRequested) "Reply requested" else ""
}

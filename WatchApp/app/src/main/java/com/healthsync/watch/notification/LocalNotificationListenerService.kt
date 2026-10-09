package com.healthsync.watch.notification

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.healthsync.watch.data.NotificationPayload
import com.healthsync.watch.data.WatchPreferences

/** Android binds this service only after the user enables watch notification access. */
class LocalNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "WatchNotifications"
        @Volatile private var instance: LocalNotificationListenerService? = null

        fun isConnected(): Boolean = instance != null
        fun accessGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun refreshTakeover(context: Context) {
            val listener = instance
            if (listener != null) listener.snapshot().sortedBy { it.postTime }.forEach { listener.capture(it, replay = true) }
            else if (Build.VERSION.SDK_INT >= 24 && accessGranted(context)) {
                runCatching { requestRebind(ComponentName(context, LocalNotificationListenerService::class.java)) }
            }
        }

        fun canOpen(entry: NotificationInboxEntry): Boolean = instance?.retained(entry)?.contentIntent != null
        fun canDismiss(entry: NotificationInboxEntry): Boolean = instance?.current(entry)?.isClearable == true

        /** Native PendingIntents stay in memory, including after an alert moves into the launcher. */
        fun open(entry: NotificationInboxEntry): Boolean {
            val listener = instance ?: return false
            val notification = listener.retained(entry) ?: return false
            val intent = notification.contentIntent ?: return false
            val opened = runCatching { intent.send(); true }.getOrDefault(false)
            if (opened && notification.autoCancel) dismiss(entry)
            return opened
        }

        fun canOpenApp(context: Context, entry: NotificationInboxEntry): Boolean =
            entry.source == NotificationInboxStore.SOURCE_WATCH && runCatching {
                context.packageManager.getLaunchIntentForPackage(entry.payload.packageName) != null
            }.getOrDefault(false)

        /** After a process restart, saved contents remain; the source app is a recovery path. */
        fun openApp(context: Context, entry: NotificationInboxEntry): Boolean {
            if (entry.source != NotificationInboxStore.SOURCE_WATCH) return false
            return runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(entry.payload.packageName) ?: return false
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }

        fun actions(entry: NotificationInboxEntry): List<WatchNotificationAction> =
            instance?.retained(entry)?.actions?.map { WatchNotificationAction(it.index, it.title) }.orEmpty()

        fun performAction(entry: NotificationInboxEntry, index: Int): Boolean {
            val listener = instance ?: return false
            val action = listener.retained(entry)?.actions?.firstOrNull { it.index == index } ?: return false
            if (action.authenticationRequired && listener.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return false
            return runCatching { action.intent.send(); true }.getOrDefault(false)
        }

        /** Called only after the user chooses Dismiss on a saved watch notification. */
        fun dismiss(entry: NotificationInboxEntry): Boolean {
            val listener = instance ?: return false
            if (entry.source != NotificationInboxStore.SOURCE_WATCH) return false
            synchronized(listener.captureLock) {
                val current = listener.current(entry)
                if (current != null && !current.isClearable) return false
                listener.dismissed.mark(entry.payload)
                if (listener.retainedNotifications[entry.payload.notificationKey]?.let {
                    matchesWatchNotificationRevision(entry, it.payload)
                } == true) listener.retainedNotifications.remove(entry.payload.notificationKey)
                return current != null && runCatching { listener.cancelNotification(current.key); true }.getOrDefault(false)
            }
        }

        /** Both inbox screens clear all filters while preserving protected watch alerts. */
        fun clearAll(context: Context): NotificationClearAllResult {
            val listener = instance
            val store = NotificationInboxStore.getInstance(context)
            if (listener == null) return NotificationClearAllResult(store.deleteAll(store.read()), 0)
            synchronized(listener.captureLock) {
                val active = listener.snapshot().filter { it.packageName != listener.packageName }
                val protected = active.filter { !it.isClearable || it.isOngoing }.map(listener::payload)
                val dismissible = active.filter { alert ->
                    alert.isClearable && !alert.isOngoing &&
                        (alert.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 ||
                            active.none { it.groupKey == alert.groupKey && (!it.isClearable || it.isOngoing) })
                }
                val entries = store.read()
                val removable = entries.filter { canClearInboxEntry(it, protected) }
                val removed = store.deleteAll(removable)
                // Block callbacks queued before this clear, without blocking new revisions.
                dismissible.forEach { listener.dismissed.mark(listener.payload(it)) }
                removable.filter { it.source == NotificationInboxStore.SOURCE_WATCH }.forEach {
                    listener.dismissed.mark(it.payload)
                    val retained = listener.retainedNotifications[it.payload.notificationKey]
                    if (retained != null && matchesWatchNotificationRevision(it, retained.payload))
                        listener.retainedNotifications.remove(it.payload.notificationKey)
                }
                // Leave our mandatory foreground-service notification and protected source alerts.
                val latest = listener.snapshot()
                dismissible.filter { original -> latest.any { current ->
                    current.key == original.key && current.packageName == original.packageName && current.postTime == original.postTime &&
                        current.isClearable && !current.isOngoing && listener.title(current.notification) == listener.title(original.notification) &&
                        listener.body(current.notification) == listener.body(original.notification)
                } }.map { it.key }.takeIf { it.isNotEmpty() }?.let {
                    runCatching { listener.cancelNotifications(it.toTypedArray()) }
                        .onFailure { Log.w(TAG, "Some source alerts could not be dismissed", it) }
                }
                listener.changed()
                return NotificationClearAllResult(removed, entries.size - removable.size)
            }
        }
    }

    private data class RetainedAction(val index: Int, val title: String, val intent: PendingIntent, val authenticationRequired: Boolean)
    private data class RetainedNotification(
        val payload: NotificationPayload,
        val contentIntent: PendingIntent?,
        val autoCancel: Boolean,
        val actions: List<RetainedAction>,
        val capturedAt: Long = System.currentTimeMillis(),
        var movedToLauncher: Boolean = false
    )

    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private val captureLock = Any()
    private val dismissed = NotificationDismissalGate()
    private val recent = object : LinkedHashMap<String, String>(100, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 100
    }
    private val retainedNotifications = object : LinkedHashMap<String, RetainedNotification>(100, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RetainedNotification>?) = size > 100
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("WatchNotificationInbox").apply { start() }
        worker = Handler(thread.looper)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        snapshot().sortedBy { it.postTime }.forEach { capture(it, replay = true) }
        changed()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        synchronized(captureLock) { retainedNotifications.clear() }
        changed()
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching { requestRebind(ComponentName(this, LocalNotificationListenerService::class.java)) }
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { sbn?.let(::capture) }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { removed(sbn, false) }
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        removed(sbn, Build.VERSION.SDK_INT >= 34 && reason == REASON_LOCKDOWN)
    }

    private fun removed(sbn: StatusBarNotification?, lockdown: Boolean) {
        if (sbn == null) return
        worker.post {
            synchronized(captureLock) {
                recent.remove(sbn.key)
                val retained = retainedNotifications[sbn.key]
                if (lockdown || retained?.let { !it.movedToLauncher && it.payload.time == sbn.postTime } == true)
                    retainedNotifications.remove(sbn.key)
                if (lockdown) {
                    dismissed.remove(sbn.packageName, sbn.key)
                    NotificationInboxStore.getInstance(applicationContext).deleteWatchKey(sbn.packageName, sbn.key)
                }
            }
            // History normally remains readable when the source app clears its alert.
            changed()
        }
    }

    private fun capture(sbn: StatusBarNotification, replay: Boolean = false) {
        if (sbn.packageName == packageName || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        worker.post {
            if (instance !== this) return@post
            runCatching {
                val originalPayload = payload(sbn)
                val p = if (replay) originalPayload.copy(isSilent = true) else originalPayload
                val signature = revision(p)
                var inboxId = -1L
                var contentChanged = false
                var moved = false
                synchronized(captureLock) {
                    if (dismissed.shouldIgnore(p)) return@runCatching
                    expireRetainedActions()
                    if (recent[sbn.key] != signature || !retainedNotifications.containsKey(sbn.key)) {
                        // Saving must succeed before removing the Android copy.
                        val delivery = NotificationInboxStore.getInstance(applicationContext).recordDelivery(p, source = NotificationInboxStore.SOURCE_WATCH)
                        inboxId = delivery.first
                        contentChanged = delivery.second
                        retainedNotifications[sbn.key] = retain(sbn, p)
                        recent[sbn.key] = signature
                    }
                    val notification = sbn.notification
                    if (notification.category != Notification.CATEGORY_CALL && canTakeOverWatchNotification(WatchPreferences(applicationContext).watchNotificationTakeoverEnabled,
                        sbn.isClearable, sbn.isOngoing, notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                        notification.deleteIntent != null, notification.actions.orEmpty().any(::hasRemoteInput), notification.fullScreenIntent != null,
                        title(notification).isNotBlank() || body(notification).isNotBlank()) &&
                        snapshot().any { it.key == sbn.key && it.packageName == sbn.packageName && it.postTime == sbn.postTime &&
                            title(it.notification) == title(sbn.notification) && body(it.notification) == body(sbn.notification) }) {
                        retainedNotifications[sbn.key]?.movedToLauncher = true
                        runCatching { cancelNotification(sbn.key) }.onFailure {
                            retainedNotifications[sbn.key]?.movedToLauncher = false
                            Log.w(TAG, "Android kept the source alert", it)
                        }
                        moved = retainedNotifications[sbn.key]?.movedToLauncher == true
                    }
                }
                changed()
                if (!replay && inboxId >= 0L) WatchNotificationAlerts.presentSaved(applicationContext, p, inboxId,
                    contentChanged, NotificationInboxStore.SOURCE_WATCH, nativeAlertStillVisible = !moved)
            }.onFailure { Log.w(TAG, "Watch notification could not be saved", it) }
        }
    }

    private fun retain(sbn: StatusBarNotification, p: NotificationPayload): RetainedNotification {
        val notification = sbn.notification
        val actions = notification.actions.orEmpty().mapIndexedNotNull { index, action ->
            val intent = action.actionIntent
            if (intent == null || action.title.isNullOrBlank() || hasRemoteInput(action)) null
            else RetainedAction(index, action.title.toString().take(80), intent,
                Build.VERSION.SDK_INT >= 31 && action.isAuthenticationRequired)
        }.take(6)
        return RetainedNotification(p, notification.contentIntent, notification.flags and Notification.FLAG_AUTO_CANCEL != 0, actions)
    }

    private fun retained(entry: NotificationInboxEntry): RetainedNotification? = synchronized(captureLock) {
        expireRetainedActions()
        val posted = snapshot()
        val active = current(entry, posted)
        if (active != null) return@synchronized retain(active, entry.payload)
        // A callback can be waiting on the worker while Android has already reused the key.
        if (posted.any { it.key == entry.payload.notificationKey && it.packageName == entry.payload.packageName }) return@synchronized null
        retainedNotifications[entry.payload.notificationKey]?.takeIf {
            retainedWatchActionIsUsable(entry, it.payload, it.capturedAt, System.currentTimeMillis()) && it.movedToLauncher
        }
    }

    private fun expireRetainedActions() {
        val now = System.currentTimeMillis()
        val iterator = retainedNotifications.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.capturedAt !in 0L..RETAINED_WATCH_ACTION_LIFETIME_MS) iterator.remove()
        }
    }

    private fun current(entry: NotificationInboxEntry, posted: List<StatusBarNotification> = snapshot()): StatusBarNotification? {
        if (entry.source != NotificationInboxStore.SOURCE_WATCH || entry.payload.notificationKey.isBlank()) return null
        return posted.firstOrNull { sbn ->
            sbn.key == entry.payload.notificationKey && sbn.packageName == entry.payload.packageName && sbn.postTime == entry.payload.time &&
                body(sbn.notification).take(8000) == entry.payload.text &&
                title(sbn.notification).let { it.isBlank() || it.take(1000) == entry.payload.title }
        }
    }

    private fun snapshot(): List<StatusBarNotification> = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())

    private fun payload(sbn: StatusBarNotification): NotificationPayload {
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val ranking = Ranking()
        val hasRanking = runCatching { currentRanking.getRanking(sbn.key, ranking) }.getOrDefault(false)
        val silent = hasRanking && (!ranking.matchesInterruptionFilter() ||
            (Build.VERSION.SDK_INT >= 24 && ranking.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT))
        return NotificationPayload(sbn.packageName, label.take(200), title(sbn.notification).ifBlank { label }.take(1000),
            body(sbn.notification).take(8000), time = sbn.postTime, canReply = false, notificationKey = sbn.key,
            category = sbn.notification.category.orEmpty(), isOngoing = sbn.isOngoing, isSilent = silent,
            channelName = if (Build.VERSION.SDK_INT >= 26 && hasRanking) ranking.channel?.name?.toString().orEmpty() else "",
            conversationTitle = sbn.notification.extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty())
    }

    private fun hasRemoteInput(action: Notification.Action): Boolean = !action.remoteInputs.isNullOrEmpty() ||
        (Build.VERSION.SDK_INT >= 26 && !action.dataOnlyRemoteInputs.isNullOrEmpty())

    private fun revision(payload: NotificationPayload): String = "${payload.title}\u0000${payload.text}\u0000${payload.time}"

    private fun title(notification: Notification): String =
        (notification.extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: notification.extras?.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()

    private fun body(notification: Notification): String =
        (notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: notification.extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            ?: notification.extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            ?: notification.tickerText?.toString().orEmpty()

    private fun changed() { sendBroadcast(Intent(NotificationInboxStore.ACTION_CHANGED).setPackage(packageName)) }

    override fun onDestroy() {
        if (instance === this) instance = null
        synchronized(captureLock) { retainedNotifications.clear() }
        thread.quitSafely()
        changed()
        super.onDestroy()
    }
}

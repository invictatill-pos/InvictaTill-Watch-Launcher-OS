package com.healthsync.phone.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.healthsync.phone.data.FitnessRepository
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.NotificationPayload
import com.healthsync.phone.data.model.ReplyResultPayload
import com.healthsync.phone.data.model.CallState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationRepositoryEntryPoint {
    fun repository(): FitnessRepository
}

/** The system owns this service; use an application entry point instead of Hilt service injection. */
class HealthNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "NotifListener"
        @Volatile var instance: HealthNotificationListenerService? = null
            private set

        fun activeNotificationPackages(): Set<String> {
            val listener = instance ?: return emptySet()
            return runCatching { listener.activeNotifications.orEmpty().map { it.packageName }
                .filter { it != listener.packageName }.toSet() }.getOrDefault(emptySet())
        }

        /** Restore the watch inbox after an offline period without waking or buzzing for old alerts. */
        fun resyncNotifications(context: Context) {
            val listener = instance ?: run {
                // Without notification access, old watch entries cannot offer a valid reply action.
                BluetoothSyncService.sendNotificationSnapshotToWatch(context, emptyList())
                return
            }
            listener.mainHandler.post {
                if (instance === listener) listener.reconcileAndReplay()
            }
        }

        fun sendReply(context: Context, packageName: String, replyText: String, title: String,
                      notificationKey: String = "", requestId: Long = 0L): ReplyResultPayload {
            return instance?.replyToNotification(packageName, replyText, title, notificationKey, requestId)
                ?: ReplyResultPayload(requestId, notificationKey, false, "Notification access is disconnected on the phone")
        }
    }

    private val phonePreferences by lazy { PhonePreferences(applicationContext) }
    private val filterPreferences by lazy { getSharedPreferences("health_sync_prefs", Context.MODE_PRIVATE) }
    private val historyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val repository by lazy {
        EntryPointAccessors.fromApplication(applicationContext, NotificationRepositoryEntryPoint::class.java).repository()
    }
    private val recentContent = object : LinkedHashMap<String, NotificationMirrorPolicy.Snapshot>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NotificationMirrorPolicy.Snapshot>?) = size > 100
    }
    private val appLabels = object : LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 100
    }
    private val pendingDialerCalls = mutableMapOf<String, Runnable>()
    private val refreshFilters = Runnable {
        if (instance === this && BluetoothSyncService.connectionState.value.isConnected) reconcileAndReplay()
    }
    private val filterListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("sync_notifications", "all_apps_enabled", "allowed_apps")) {
            mainHandler.removeCallbacks(refreshFilters)
            mainHandler.postDelayed(refreshFilters, 150L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        filterPreferences.registerOnSharedPreferenceChangeListener(filterListener)
    }

    override fun onListenerConnected() {
        instance = this
        runCatching { phonePreferences.recordNotificationSources(
            activeNotifications.orEmpty().map { it.packageName }.filter { it != packageName }.toSet()) }
            .onFailure { Log.w(TAG, "Could not discover active notification sources", it) }
        if (BluetoothSyncService.connectionState.value.isConnected) reconcileAndReplay()
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        if (instance == this) instance = null
        if (BluetoothSyncService.connectionState.value.isConnected)
            BluetoothSyncService.sendNotificationSnapshotToWatch(applicationContext, emptyList())
        Log.w(TAG, "Notification listener disconnected")
        runCatching { requestRebind(ComponentName(this, HealthNotificationListenerService::class.java)) }
            .onFailure { Log.w(TAG, "Notification listener rebind unavailable", it) }
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        filterPreferences.unregisterOnSharedPreferenceChangeListener(filterListener)
        mainHandler.removeCallbacksAndMessages(null)
        historyScope.cancel()
        super.onDestroy()
    }

    /** Success means Android accepted the app's reply action, rather than merely writing to Bluetooth. */
    fun replyToNotification(targetPackage: String, replyText: String, targetTitle: String = "",
                            notificationKey: String = "", requestId: Long = 0L): ReplyResultPayload {
        fun result(error: String? = null) = ReplyResultPayload(requestId, notificationKey, error == null, error)
        NotificationMirrorPolicy.replyError(targetPackage, replyText, targetTitle, notificationKey)?.let { return result(it) }
        if (!phonePreferences.isAppAllowed(targetPackage)) return result("Notifications from this app are disabled on the phone")
        return try {
            val matches = activeNotifications.orEmpty().filter { sbn ->
                sbn.packageName == targetPackage && if (notificationKey.isNotBlank())
                    sbn.key == notificationKey && (targetTitle.isBlank() || extractContent(sbn)?.title == targetTitle)
                else extractContent(sbn)?.title == targetTitle
            }
            // Legacy watches without a key must still identify exactly one conversation.
            if (matches.size != 1) return result("This notification has ended or the conversation is ambiguous")
            val action = replyActions(matches.single().notification).firstOrNull()
                ?: return result("This app no longer offers a reply action")
            val remoteInput = action.remoteInputs.first { it.allowFreeFormInput }
            val intent = Intent()
            val values = Bundle().apply { putCharSequence(remoteInput.resultKey, replyText) }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, values)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            action.actionIntent.send(this, 0, intent)
            result()
        } catch (_: PendingIntent.CanceledException) {
            result("The app's reply action expired; open the latest notification")
        } catch (_: SecurityException) {
            result("The phone denied access to this reply action")
        } catch (e: Exception) {
            Log.w(TAG, "Reply submission failed", e)
            result("Could not submit this reply on the phone")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        runCatching {
            if (sbn.packageName == packageName) return
            phonePreferences.recordNotificationSources(setOf(sbn.packageName))
            runCatching { enrichCaller(sbn) }.onFailure { Log.w(TAG, "Caller notification metadata unavailable", it) }
            if (phonePreferences.syncCalls && isDialerCall(sbn)) deferDialerCall(sbn)
            else mirror(sbn, replay = false)
        }.onFailure { Log.w(TAG, "Could not mirror notification", it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        pendingDialerCalls.remove(sbn.key)?.let(mainHandler::removeCallbacks)
        runCatching { CallMonitorService.clearNotificationCaller(applicationContext, sbn.key) }
        removeMirrored(sbn.key)
    }

    private fun reconcileAndReplay() {
        runCatching {
            val active = activeNotifications.orEmpty()
            phonePreferences.recordNotificationSources(active.map { it.packageName }.filter { it != packageName }.toSet())
            // Caller enrichment follows call sync even when this dialer's ordinary alerts are filtered.
            active.forEach { runCatching { enrichCaller(it) } }
            val allowed = active.asSequence().filter { it.packageName != packageName && phonePreferences.isAppAllowed(it.packageName) }
                .filter { shouldMirrorCallNotification(it) }
                .filter { runCatching { extractContent(it) }.getOrNull() != null }.sortedByDescending { it.postTime }
                .take(1_000).toList()
            // A single snapshot also retires notifications removed while the phone/watch were disconnected.
            BluetoothSyncService.sendNotificationSnapshotToWatch(applicationContext, allowed.map { it.key })
            val activeKeys = allowed.map { it.key }.toSet()
            NotificationMirrorPolicy.staleKeys(recentContent.keys.toSet(), activeKeys).forEach { recentContent.remove(it) }
            // Older notifications are sent first so the watch's newest entry remains newest.
            allowed.take(NotificationMirrorPolicy.MAX_ACTIVE).asReversed().forEach {
                runCatching { mirror(it, replay = true) }.onFailure { Log.w(TAG, "Active notification unavailable", it) }
            }
        }.onFailure { Log.w(TAG, "Could not reconcile active notifications", it) }
    }

    private fun mirror(sbn: StatusBarNotification, replay: Boolean) {
        if (!phonePreferences.isAppAllowed(sbn.packageName) || !shouldMirrorCallNotification(sbn)) {
            removeMirrored(sbn.key)
            return
        }
        val content = extractContent(sbn) ?: run { removeMirrored(sbn.key); return }
        val notification = sbn.notification
        val ranking = Ranking()
        val ranked = runCatching { currentRanking.getRanking(sbn.key, ranking) }.getOrDefault(false)
        val channelName = NotificationMirrorPolicy.bounded(if (ranked) ranking.channel?.name?.toString() else null, 200)
        val canReply = replyActions(notification).isNotEmpty()
        val ongoing = sbn.isOngoing
        val category = NotificationMirrorPolicy.bounded(notification.category, 100)
        val snapshot = NotificationMirrorPolicy.Snapshot(content.signature, "$canReply\u0000$ongoing\u0000$category\u0000$channelName")
        val sourceSilent = ranked && (ranking.isAmbient || !ranking.matchesInterruptionFilter() ||
            ranking.importance in 0..android.app.NotificationManager.IMPORTANCE_LOW)
        val decision = NotificationMirrorPolicy.decide(recentContent[sbn.key], snapshot, replay, sourceSilent,
            ongoing, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        recentContent[sbn.key] = snapshot
        if (!decision.forward) return
        val payload = NotificationPayload(
            packageName = sbn.packageName, appLabel = appLabel(sbn.packageName), title = content.title,
            text = content.text, ticker = NotificationMirrorPolicy.bounded(notification.tickerText?.toString(), 500),
            time = sbn.postTime, canReply = canReply, notificationKey = sbn.key, category = category,
            isOngoing = ongoing, isSilent = decision.silent, channelName = channelName,
            conversationTitle = content.conversationTitle, isActive = true)
        if (decision.saveHistory) historyScope.launch {
            runCatching { repository.saveNotification(payload) }.onFailure { Log.w(TAG, "Could not save alert history", it) }
        }
        BluetoothSyncService.sendNotificationToWatch(applicationContext, payload)
    }

    private fun removeMirrored(key: String) {
        recentContent.remove(key)
        BluetoothSyncService.sendNotificationRemovedToWatch(applicationContext, key)
    }

    private fun deferDialerCall(sbn: StatusBarNotification) {
        pendingDialerCalls.remove(sbn.key)?.let(mainHandler::removeCallbacks)
        val task = Runnable {
            pendingDialerCalls.remove(sbn.key)
            // Telecom/telephony may publish just after the dialer. Use its call screen once available,
            // and retain notification fallback on phones that have not granted call monitoring access.
            runCatching {
                val current = activeNotifications.orEmpty().firstOrNull { it.key == sbn.key }
                if (current != null) mirror(current, replay = false)
            }.onFailure { Log.w(TAG, "Dialer notification no longer available", it) }
        }
        pendingDialerCalls[sbn.key] = task
        mainHandler.postDelayed(task, 250L)
    }

    private fun isDialerCall(sbn: StatusBarNotification): Boolean =
        sbn.notification.category == Notification.CATEGORY_CALL && runCatching {
            sbn.packageName == (getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
        }.getOrDefault(false)

    private fun shouldMirrorCallNotification(sbn: StatusBarNotification): Boolean = NotificationMirrorPolicy.shouldMirrorDialerCall(
        phonePreferences.syncCalls, isDialerCall(sbn), WatchInCallService.hasCalls ||
            CallMonitorService.latestCallState in setOf(CallState.INCOMING, CallState.ANSWERED))

    @Suppress("DEPRECATION")
    private fun extractContent(sbn: StatusBarNotification): NotificationMirrorPolicy.Content? {
        val notification = sbn.notification
        val extras = notification.extras
        // AndroidX restores framework and compat MessagingStyle bundles on every supported phone API.
        val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages.orEmpty()
            .takeLast(12).map { message ->
                val sender = message.person?.name?.toString() ?: message.sender?.toString()
                NotificationMirrorPolicy.Message(message.text?.toString(), sender, !message.dataMimeType.isNullOrBlank())
            }
        return NotificationMirrorPolicy.extract(NotificationMirrorPolicy.Source(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.takeLast(12)?.map { it.toString() }.orEmpty(),
            ticker = notification.tickerText?.toString(),
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            messages = messages, groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0), appLabel(sbn.packageName))
    }

    private fun appLabel(source: String): String = synchronized(appLabels) {
        appLabels[source] ?: runCatching {
            NotificationMirrorPolicy.bounded(packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(source, PackageManager.GET_META_DATA)).toString(), 500)
        }.getOrDefault(source).also { appLabels[source] = it }
    }

    private fun replyActions(notification: Notification): List<Notification.Action> {
        val actions = notification.actions.orEmpty().toList() + Notification.WearableExtender(notification).actions
        return actions.filter { it.actionIntent != null && it.remoteInputs?.any { input -> input.allowFreeFormInput } == true }
            .sortedByDescending { Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
    }

    @Suppress("DEPRECATION")
    private fun enrichCaller(sbn: StatusBarNotification) {
        if (!isDialerCall(sbn)) return
        val extras = sbn.notification.extras
        val (personName, personUri) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val person = extras.getParcelable("android.callPerson") as? Person
            person?.name?.toString() to person?.uri
        } else null to null
        val name = personName.orEmpty().ifBlank { extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty() }
        val uri = personUri ?: extras.getStringArray(Notification.EXTRA_PEOPLE)?.firstOrNull { it.startsWith("tel:") }
        val number = uri?.takeIf { it.startsWith("tel:") }?.removePrefix("tel:").orEmpty()
        CallMonitorService.updateNotificationCaller(applicationContext, sbn.key,
            NotificationMirrorPolicy.bounded(name, 500), NotificationMirrorPolicy.bounded(number, 100))
    }
}

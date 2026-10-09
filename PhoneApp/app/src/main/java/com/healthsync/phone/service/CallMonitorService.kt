package com.healthsync.phone.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.CallPayload
import com.healthsync.phone.data.model.CallState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.UUID

/** Read-only fallback alerts until Android binds the associated watch's InCallService. */
@AndroidEntryPoint
class CallMonitorService : android.app.Service() {
    companion object {
        private const val TAG = "CallMonitor"
        private const val CHANNEL_ID = "call_monitor_channel"
        private const val NOTIF_ID = 1002
        @Volatile var latestCallPayload = CallPayload(CallState.ENDED, "", "")
            private set
        val latestCallState: CallState get() = latestCallPayload.state
        @Volatile var legacyCallAmbiguous = false
            private set
        private data class NotificationCaller(val key: String, val name: String, val number: String,
                                              val seenAt: Long = System.currentTimeMillis())
        @Volatile private var notificationCaller: NotificationCaller? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var instance: CallMonitorService? = null

        fun publish(context: Context, payload: CallPayload) {
            if (latestCallPayload == payload) return
            latestCallPayload = payload
            LegacyCallController.changed(payload)
            if (PhonePreferences(context).syncCalls || payload.state == CallState.ENDED || payload.state == CallState.MISSED)
                BluetoothSyncService.sendCallToWatch(context, payload)
        }

        fun updateNotificationCaller(context: Context, key: String, name: String, number: String) {
            if (name.isBlank() && number.isBlank()) return
            val metadata = NotificationCaller(key, name.trim().take(120), number.trim().take(80))
            notificationCaller = metadata
            mainHandler.post {
                if (!WatchInCallService.hasCalls) instance?.enrichCaller(metadata)
            }
        }

        fun clearNotificationCaller(context: Context, key: String) {
            if (notificationCaller?.key == key) notificationCaller = null
        }
    }

    private var telephonyManager: TelephonyManager? = null
    private var callbackApi31: Any? = null
    @Suppress("DEPRECATION") private var listenerLegacy: PhoneStateListener? = null
    private var previousState = TelephonyManager.CALL_STATE_IDLE
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            createChannel()
            startForeground(NOTIF_ID, buildNotif())
            telephonyManager = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) registerCallbackApi31()
            else registerLegacyListener()
        } catch (e: Exception) { Log.e(TAG, "Call monitoring unavailable", e); stopSelf() }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerCallbackApi31() {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = handleState(state)
        }
        callbackApi31 = callback
        telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyListener() {
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleState(state, phoneNumber.orEmpty())
        }
        listenerLegacy = listener
        telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun handleState(state: Int, phoneNumber: String = "") {
        val prior = previousState
        previousState = state
        if (WatchInCallService.hasCalls) return
        if (state == TelephonyManager.CALL_STATE_RINGING && prior == TelephonyManager.CALL_STATE_OFFHOOK)
            legacyCallAmbiguous = true
        if (state == TelephonyManager.CALL_STATE_IDLE) legacyCallAmbiguous = false
        if (state == prior) {
            if (phoneNumber.isNotBlank()) enrichCaller(NotificationCaller("telephony", "", phoneNumber))
            return
        }
        val old = latestCallPayload
        val callState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> CallState.INCOMING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallState.ANSWERED
            TelephonyManager.CALL_STATE_IDLE -> if (prior == TelephonyManager.CALL_STATE_RINGING && !LegacyCallController.rejected(old.callId)) CallState.MISSED else CallState.ENDED
            else -> return
        }
        val newCall = state == TelephonyManager.CALL_STATE_RINGING || prior == TelephonyManager.CALL_STATE_IDLE
        val legacyControls = Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !legacyCallAmbiguous && LegacyCallController.permission(this)
        val payload = CallPayload(state = callState,
            number = if (newCall) phoneNumber else phoneNumber.ifBlank { old.number },
            callerName = if (newCall) phoneNumber.ifBlank { "Unknown caller" } else old.callerName,
            callId = if (newCall) UUID.randomUUID().toString() else old.callId,
            canAnswer = legacyControls && callState == CallState.INCOMING,
            canEnd = legacyControls && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && state != TelephonyManager.CALL_STATE_IDLE,
            startedAt = when {
                newCall && callState == CallState.ANSWERED -> System.currentTimeMillis()
                newCall -> 0L
                callState == CallState.ANSWERED -> old.startedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                else -> old.startedAt
            },
            statusText = when {
                state == TelephonyManager.CALL_STATE_IDLE -> ""
                legacyCallAmbiguous -> "Multiple calls are present. Use the phone call screen"
                legacyControls -> "Audio stays on phone. Choose watch Calls in the phone Bluetooth call screen"
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "Allow Phone call permission in the phone Settings"
                else -> "Enable watch call controls in the phone Settings"
            })
        publish(applicationContext, if (legacyCallAmbiguous && state == TelephonyManager.CALL_STATE_OFFHOOK)
            payload.copy(callerName = "Multiple calls", number = "") else payload)
        if (state != TelephonyManager.CALL_STATE_IDLE) {
            val metadata = notificationCaller?.takeIf { System.currentTimeMillis() - it.seenAt < 15_000L }
                ?: NotificationCaller("telephony", "", phoneNumber)
            enrichCaller(metadata)
        } else notificationCaller = null
    }

    private fun enrichCaller(metadata: NotificationCaller) {
        val current = latestCallPayload
        if (current.state != CallState.INCOMING && current.state != CallState.ANSWERED) return
        val number = metadata.number.ifBlank { current.number }
        scope.launch {
            val name = withContext(Dispatchers.IO) { CallerIdentity.name(applicationContext, number, metadata.name) }
            val latest = latestCallPayload
            if (!WatchInCallService.hasCalls && latest.callId == current.callId &&
                (latest.state == CallState.INCOMING || latest.state == CallState.ANSWERED)) {
                publish(applicationContext, latest.copy(number = number, callerName = name))
            }
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (callbackApi31 as? TelephonyCallback)?.let { telephonyManager?.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                listenerLegacy?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (e: Exception) { Log.w(TAG, "Call monitor cleanup", e) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Call alerts", NotificationManager.IMPORTANCE_LOW))
    }
    private fun buildNotif() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_call).setContentTitle("HealthSync")
        .setContentText("Watching for phone calls").setOngoing(true).build()
}

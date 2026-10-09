package com.healthsync.watch.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.app.NotificationManager
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.healthsync.watch.R
import com.healthsync.watch.data.CallState
import com.healthsync.watch.ui.calls.WatchCallBridge
import com.healthsync.watch.ui.calls.WatchCallPolicy
import com.healthsync.watch.ui.calls.WatchCallAlerts

class CallAlertActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_NUMBER = "number"
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = render()
    }
    private val handler = Handler(Looper.getMainLooper())
    private var ringingSince = 0L
    private val reminder = object : Runnable {
        override fun run() {
            val state = WatchCallBridge.snapshot
            val manager = getSystemService(NotificationManager::class.java)
            val vibrationAllowed = manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL &&
                (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(WatchCallAlerts.CHANNEL_ID)?.shouldVibrate() == true)
            if (state.call?.state == CallState.INCOMING && state.connected && state.pendingId == 0L && vibrationAllowed &&
                SystemClock.elapsedRealtime() - ringingSince < 90_000L) runCatching {
                val vibrator = getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1))
                else { @Suppress("DEPRECATION") vibrator?.vibrate(longArrayOf(0, 180, 100, 180), -1) }
            }
            handler.postDelayed(this, 3_000L)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContentView(R.layout.activity_call_alert)
        ContextCompat.registerReceiver(this, receiver, IntentFilter(WatchCallBridge.ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        findViewById<View>(R.id.btnAnswer).setOnClickListener { WatchCallBridge.send("ANSWER"); render() }
        findViewById<View>(R.id.btnDecline).setOnClickListener { WatchCallBridge.send("REJECT"); render() }
        render()
    }
    private fun render() {
        val state = WatchCallBridge.snapshot
        val call = state.call
        if (call == null || !state.connected || call.state != CallState.INCOMING) { finish(); return }
        findViewById<TextView>(R.id.tvCallerName).text = WatchCallPolicy.callerLabel(call)
        findViewById<TextView>(R.id.tvCallerNumber).text = call.number.takeUnless { it == WatchCallPolicy.callerLabel(call) }.orEmpty()
        findViewById<TextView>(R.id.tvCallState).text = state.error ?: when {
            state.pendingAction == "ANSWER" -> "Waiting for phone to answer…"
            state.pendingAction == "REJECT" -> "Waiting for phone to decline…"
            !call.canAnswer -> call.statusText.ifBlank { "Enable watch call controls in the phone app" }
            else -> "Incoming phone call"
        }
        findViewById<TextView>(R.id.tvCallAudioHint).text = when {
            !call.canAnswer -> "Call controls need phone setup. Caller alerts remain available."
            call.bluetoothAudioAvailable -> "Watch call audio available after answering"
            else -> "Answer here · audio on phone. Watch audio needs Bluetooth call support."
        }
        enable(R.id.btnAnswer, WatchCallPolicy.canSend(call, state.connected, state.pendingId != 0L, "ANSWER"))
        enable(R.id.btnDecline, WatchCallPolicy.canSend(call, state.connected, state.pendingId != 0L, "REJECT"))
    }
    private fun enable(id: Int, enabled: Boolean) { findViewById<View>(id).apply { isEnabled = enabled; alpha = if (enabled) 1f else .45f } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); render() }
    override fun onResume() {
        super.onResume(); render()
        if (ringingSince == 0L) ringingSince = SystemClock.elapsedRealtime()
        handler.removeCallbacks(reminder); handler.postDelayed(reminder, 1500L)
    }
    override fun onPause() { handler.removeCallbacks(reminder); getSystemService(Vibrator::class.java)?.cancel(); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); runCatching { unregisterReceiver(receiver) }; super.onDestroy() }
}

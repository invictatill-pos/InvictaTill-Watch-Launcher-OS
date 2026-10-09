package com.healthsync.watch.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.healthsync.watch.R
import com.healthsync.watch.data.CallState
import com.healthsync.watch.ui.calls.WatchCallBridge
import com.healthsync.watch.ui.calls.WatchCallPolicy
import java.util.Locale

/** The phone owns the call, microphone and audio route; labels reflect its confirmed state. */
open class InCallActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_NUMBER = "number"
        const val ACTION_CALL_ENDED = "com.healthsync.watch.CALL_ENDED"
        const val ACTION_CALL_ANSWERED = "com.healthsync.watch.CALL_ANSWERED"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) = render() }
    private val tick = object : Runnable { override fun run() { render(); handler.postDelayed(this, 1000L) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContentView(R.layout.activity_in_call)
        ContextCompat.registerReceiver(this, receiver, IntentFilter(WatchCallBridge.ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        findViewById<View>(R.id.btnEndCall).setOnClickListener { WatchCallBridge.send("END"); render() }
        findViewById<View>(R.id.btnMute).setOnClickListener {
            WatchCallBridge.send(if (WatchCallBridge.snapshot.call?.muted == true) "UNMUTE" else "MUTE"); render()
        }
        findViewById<View>(R.id.btnSpeaker).setOnClickListener {
            WatchCallBridge.send(if (WatchCallBridge.snapshot.call?.audioRoute == "SPEAKER") "AUDIO_PHONE" else "SPEAKER_ON"); render()
        }
        findViewById<View>(R.id.btnWatchAudio).setOnClickListener {
            WatchCallBridge.send(if (WatchCallBridge.snapshot.call?.audioRoute == "BLUETOOTH") "AUDIO_PHONE" else "AUDIO_BLUETOOTH"); render()
        }
        render()
    }
    private fun render() {
        val state = WatchCallBridge.snapshot
        val call = state.call
        if (call == null || !state.connected || call.state != CallState.ANSWERED) { finish(); return }
        val pending = state.pendingId != 0L
        findViewById<TextView>(R.id.tvCallerName).text = WatchCallPolicy.callerLabel(call)
        findViewById<TextView>(R.id.tvCallStatus).text = state.error ?: when {
            pending -> "Waiting for phone confirmation…"
            !call.canEnd -> call.statusText.ifBlank { "Enable watch call controls on phone" }
            call.statusText == "Calling…" || call.statusText == "On hold" -> call.statusText
            else -> "CALL ACTIVE"
        }
        val seconds = WatchCallPolicy.durationSeconds(call.startedAt, System.currentTimeMillis())
        findViewById<TextView>(R.id.tvCallTimer).text = if (call.startedAt <= 0L) "Active" else
            if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
        findViewById<TextView>(R.id.tvAudioRoute).text = when (call.audioRoute) {
            "BLUETOOTH" -> "Audio: watch Bluetooth call connection"
            "SPEAKER" -> "Audio: phone speaker"
            "BLUETOOTH_OTHER" -> "Audio: another Bluetooth device"
            else -> if (call.bluetoothAudioAvailable) "Audio: phone · Watch audio available" else "Audio: phone · Watch call audio unavailable"
        }
        findViewById<TextView>(R.id.tvMuteIcon).text = if (call.muted) "🔇" else "🎤"
        findViewById<TextView>(R.id.tvMuteLabel).text = if (call.muted) "Unmute" else "Mute"
        findViewById<CardView>(R.id.btnMute).setCardBackgroundColor(if (call.muted) 0xFF32131C.toInt() else 0xFF14231C.toInt())
        findViewById<TextView>(R.id.tvSpeakerLabel).text = if (call.audioRoute == "SPEAKER") "To phone" else "Speaker"
        findViewById<TextView>(R.id.tvWatchAudioLabel).text = if (call.audioRoute == "BLUETOOTH") "Use phone audio" else "Use watch audio"
        enable(R.id.btnEndCall, WatchCallPolicy.canSend(call, state.connected, pending, "END"))
        enable(R.id.btnMute, WatchCallPolicy.canSend(call, state.connected, pending, "MUTE"))
        enable(R.id.btnSpeaker, WatchCallPolicy.canSend(call, state.connected, pending, "SPEAKER_ON"))
        enable(R.id.btnWatchAudio, WatchCallPolicy.canSend(call, state.connected, pending,
            if (call.audioRoute == "BLUETOOTH") "AUDIO_PHONE" else "AUDIO_BLUETOOTH"))
    }
    private fun enable(id: Int, enabled: Boolean) { findViewById<View>(id).apply { isEnabled = enabled; alpha = if (enabled) 1f else .45f } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); render() }
    override fun onResume() { super.onResume(); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); runCatching { unregisterReceiver(receiver) }; super.onDestroy() }
}

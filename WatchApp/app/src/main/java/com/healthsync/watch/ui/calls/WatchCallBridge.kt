package com.healthsync.watch.ui.calls

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.healthsync.watch.data.*
import com.healthsync.watch.service.BluetoothClientService
import java.util.concurrent.atomic.AtomicLong

data class WatchCallSnapshot(val call: CallPayload? = null, val connected: Boolean = false,
    val pendingId: Long = 0L, val pendingAction: String = "", val error: String? = null)

/** State comes from Telecom on the phone. Sending a request never changes a control's state. */
object WatchCallBridge {
    const val ACTION_CHANGED = "com.healthsync.watch.CALL_STATE_CHANGED"
    private val handler = Handler(Looper.getMainLooper())
    private val ids = AtomicLong(System.currentTimeMillis())
    private var context: Context? = null
    private var timeout: Runnable? = null
    @Volatile var snapshot = WatchCallSnapshot()
        private set
    fun isCallActive() = snapshot.connected && WatchCallPolicy.active(snapshot.call)

    fun receive(context: Context, call: CallPayload) {
        this.context = context.applicationContext
        if (!WatchCallPolicy.accepts(snapshot.call, call)) return
        val sanitized = call.copy(callerName = call.callerName.orEmpty().take(120), number = call.number.orEmpty().take(80),
            callId = call.callId.orEmpty().take(160), audioRoute = call.audioRoute.orEmpty().take(30), statusText = call.statusText.orEmpty().take(240))
        val transitioned = snapshot.call?.callId != sanitized.callId || snapshot.call?.state != sanitized.state
        if (transitioned) { timeout?.let(handler::removeCallbacks) }
        snapshot = snapshot.copy(call = sanitized, connected = BluetoothClientService.isConnected,
            pendingId = if (transitioned) 0L else snapshot.pendingId,
            pendingAction = if (transitioned) "" else snapshot.pendingAction, error = if (transitioned) null else snapshot.error)
        publish()
    }

    fun receiveResult(result: CallControlStatePayload) = main {
        val current = snapshot
        if (result.callId != current.call?.callId || result.requestId == 0L || result.requestId != current.pendingId) return@main
        timeout?.let(handler::removeCallbacks)
        snapshot = current.copy(pendingId = 0L, pendingAction = "",
            error = if (result.success) null else result.error?.take(240) ?: "Phone could not apply this control",
            call = current.call?.copy(muted = result.muted, audioRoute = result.audioRoute.orEmpty().take(30),
                bluetoothAudioAvailable = result.bluetoothAudioAvailable))
        publish()
    }

    fun send(action: String): Boolean {
        val current = snapshot
        if (!WatchCallPolicy.canSend(current.call, current.connected, current.pendingId != 0L, action)) return false
        val requestId = ids.incrementAndGet()
        val sent = BluetoothClientService.sendRawMsg(SyncMessage(MessageType.CALL_ACTION,
            payload = Gson().toJson(CallActionPayload(action, current.call?.number.orEmpty(), current.call?.callId.orEmpty(), requestId))))
        if (!sent) { snapshot = current.copy(error = "Phone disconnected. Use the phone to control this call"); publish(); return false }
        snapshot = current.copy(pendingId = requestId, pendingAction = action, error = null)
        timeout?.let(handler::removeCallbacks)
        timeout = Runnable {
            if (snapshot.pendingId == requestId) {
                snapshot = snapshot.copy(pendingId = 0L, pendingAction = "", error = "Phone did not confirm the request. Check the phone")
                publish()
            }
        }.also { handler.postDelayed(it, 8_000L) }
        publish()
        return true
    }

    fun connectionChanged(context: Context, connected: Boolean) = main {
        this.context = context.applicationContext
        timeout?.let(handler::removeCallbacks)
        snapshot = WatchCallSnapshot(connected = connected, error = if (connected) null else "Phone disconnected")
        if (!connected) WatchCallAlerts.cancel(context)
        publish()
    }
    private fun publish() { context?.let { it.sendBroadcast(Intent(ACTION_CHANGED).setPackage(it.packageName)) } }
    internal fun main(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() } }
}

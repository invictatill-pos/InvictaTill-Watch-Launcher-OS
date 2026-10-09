package com.healthsync.watch.ui.shell

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.healthsync.watch.data.MessageType
import com.healthsync.watch.data.PhoneControlPayload
import com.healthsync.watch.data.PhoneControlStatePayload
import com.healthsync.watch.data.SyncMessage
import com.healthsync.watch.service.BluetoothClientService
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class PhoneControlSnapshot(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val available: Boolean = false,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val lastUpdated: Long = 0L,
    val error: String? = null,
    val findingPhone: Boolean = false,
    val pending: Boolean = false
)

/** Queuing a command never claims that the phone performed it. Only the phone's response updates state. */
object PhoneControlsBridge {
    const val ACTION_CHANGED = "com.healthsync.watch.PHONE_CONTROLS_CHANGED"
    private val handler = Handler(Looper.getMainLooper())
    private val ids = AtomicLong(System.currentTimeMillis())
    private val gson = Gson()
    private var context: Context? = null
    private var pendingId = 0L
    private var timeout: Runnable? = null
    private var previousConnection: Boolean? = null
    @Volatile var snapshot: PhoneControlSnapshot = PhoneControlSnapshot()
        private set

    fun initialize(context: Context) { this.context = context.applicationContext }

    fun sendMedia(action: String): Boolean {
        val normalized = action.trim().uppercase(Locale.ROOT)
        if (normalized !in setOf("PLAY", "PAUSE", "PLAY_PAUSE", "NEXT", "PREVIOUS")) return false
        return send(normalized)
    }
    fun findPhone(start: Boolean): Boolean = send(if (start) "FIND_START" else "FIND_STOP")
    fun requestState(): Boolean {
        // Polling must not keep an unanswered request alive indefinitely.
        if (snapshot.pending) return BluetoothClientService.isConnected
        return send("STATE")
    }

    private fun send(action: String): Boolean {
        val requestId = ids.incrementAndGet()
        val sent = BluetoothClientService.sendRawMsg(SyncMessage(MessageType.PHONE_CONTROL,
            payload = gson.toJson(PhoneControlPayload(requestId, action))))
        main {
            timeout?.let { handler.removeCallbacks(it) }
            if (!sent) {
                pendingId = 0L
                snapshot = snapshot.copy(pending = false, error = "Connect your phone first")
                publish()
                return@main
            }
            pendingId = requestId
            snapshot = snapshot.copy(pending = true, error = null)
            publish()
            timeout = Runnable {
                if (pendingId == requestId) {
                    pendingId = 0L
                    snapshot = snapshot.copy(pending = false, available = false, findingPhone = false,
                        error = if (snapshot.lastUpdated == 0L) "Phone controls need the updated phone app" else "Phone did not respond. Try again")
                    publish()
                }
            }.also { handler.postDelayed(it, 5_000L) }
        }
        return sent
    }

    fun receive(state: PhoneControlStatePayload) = main {
        val completed = pendingId != 0L && state.requestId == pendingId
        if (completed) { timeout?.let { handler.removeCallbacks(it) }; pendingId = 0L }
        snapshot = PhoneControlSnapshot(
            title = state.title.orEmpty().take(120), artist = state.artist.orEmpty().take(120),
            playing = state.playing && state.available, available = state.available,
            batteryPercent = state.batteryPercent.takeIf { it in 0..100 } ?: -1,
            charging = state.charging, lastUpdated = System.currentTimeMillis(),
            error = state.error?.take(180)?.takeIf { it.isNotBlank() }, findingPhone = state.findingPhone,
            pending = pendingId != 0L
        )
        publish()
    }

    fun connectionChanged(context: Context, connected: Boolean) {
        initialize(context)
        main {
            if (previousConnection == connected) return@main
            previousConnection = connected
            timeout?.let { handler.removeCallbacks(it) }
            pendingId = 0L
            snapshot = PhoneControlSnapshot(error = if (connected) null else "Phone disconnected")
            publish()
        }
    }

    private fun publish() { context?.let { it.sendBroadcast(Intent(ACTION_CHANGED).setPackage(it.packageName)) } }
    private fun main(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
}

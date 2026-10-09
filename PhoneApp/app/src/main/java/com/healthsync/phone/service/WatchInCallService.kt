package com.healthsync.phone.service

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.CallActionPayload
import com.healthsync.phone.data.model.CallControlStatePayload
import com.healthsync.phone.data.model.CallPayload
import com.healthsync.phone.data.model.CallState
import kotlinx.coroutines.*
import java.util.IdentityHashMap
import java.util.UUID

/** Android binds this companion service after the user associates their physical watch. */
@Suppress("DEPRECATION")
class WatchInCallService : InCallService() {
    companion object {
        @Volatile private var instance: WatchInCallService? = null
        val hasCalls: Boolean get() = instance?.tracked?.isNotEmpty() == true

        fun refresh() { instance?.handler?.post { instance?.publishCurrent() } }
        fun handle(context: Context, request: CallActionPayload, address: String,
                   reply: (CallControlStatePayload) -> Unit) {
            val service = instance
            if (service == null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    LegacyCallController.handle(context, request, address, reply); return
                }
                val snapshot = CallMonitorService.latestCallPayload
                reply(CallControlStatePayload(requestId = request.requestId, callId = request.callId,
                    action = request.action, error = "Enable watch call controls in the phone Settings",
                    muted = snapshot.muted, audioRoute = snapshot.audioRoute))
                return
            }
            service.handler.post { service.execute(request, address, reply) }
        }
    }

    private data class Entry(val id: String = UUID.randomUUID().toString(), var startedAt: Long = 0L,
                             var name: String = "", var nameLookupNumber: String? = null)
    private data class Pending(val request: CallActionPayload, val address: String,
                               val reply: (CallControlStatePayload) -> Unit)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracked = IdentityHashMap<Call, Entry>()
    private val callbacks = IdentityHashMap<Call, Call.Callback>()
    private val pending = linkedMapOf<Long, Pending>()
    private val completed = linkedMapOf<Long, CallControlStatePayload>()
    private var audio: CallAudioState? = null

    override fun onCreate() { super.onCreate(); instance = this }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        tracked[call] = Entry()
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = changed(call)
            override fun onDetailsChanged(call: Call, details: Call.Details) = changed(call)
        }
        callbacks[call] = callback
        call.registerCallback(callback, handler)
        audio = callAudioState
        changed(call)
    }

    private fun changed(call: Call) {
        val entry = tracked[call] ?: return
        if (call.state == Call.STATE_ACTIVE && entry.startedAt == 0L)
            entry.startedAt = call.details.connectTimeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val number = number(call)
        if (entry.nameLookupNumber != number) {
            entry.nameLookupNumber = number
            scope.launch {
                val name = withContext(Dispatchers.IO) { CallerIdentity.name(applicationContext, number, call.details.callerDisplayName.orEmpty()) }
                if (tracked[call] === entry && number(call) == number) { entry.name = name; publishCurrent() }
            }
        }
        if (call.state == Call.STATE_DISCONNECTED) {
            val terminal = snapshot(call, entry, terminal = true)
            CallMonitorService.publish(applicationContext, terminal)
            settle(terminal)
        }
        publishCurrent()
    }

    override fun onCallRemoved(call: Call) {
        val entry = tracked.remove(call)
        callbacks.remove(call)?.let(call::unregisterCallback)
        if (entry != null) {
            val terminal = snapshot(call, entry, terminal = true)
            CallMonitorService.publish(applicationContext, terminal)
            settle(terminal)
        }
        publishCurrent()
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audio = audioState
        publishCurrent()
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        // Keep the authoritative Telecom state, including the current Bluetooth device.
        audio = callAudioState
        publishCurrent()
    }

    private fun currentCall(): Call? = tracked.keys.firstOrNull { it.state == Call.STATE_RINGING }
        ?: tracked.keys.firstOrNull { it.state == Call.STATE_ACTIVE }
        ?: tracked.keys.firstOrNull { it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING }

    private fun number(call: Call): String = if (call.details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED)
        call.details.handle?.schemeSpecificPart.orEmpty().take(80) else ""

    private fun watchAddress(): String = BluetoothSyncService.connectionState.value.deviceAddress.orEmpty()
    private fun supportedWatch(address: String) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try { audio?.supportedBluetoothDevices?.firstOrNull { CallControlPolicy.sameAddress(it.address, address) } }
        catch (_: SecurityException) { null }
    } else null

    private fun activeBluetoothAddress(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try { audio?.activeBluetoothDevice?.address } catch (_: SecurityException) { null }
    } else null

    private fun snapshot(call: Call, entry: Entry, terminal: Boolean = false): CallPayload {
        val ended = terminal || call.state == Call.STATE_DISCONNECTED
        val missed = ended && call.details.disconnectCause.code == DisconnectCause.MISSED
        val state = when { missed -> CallState.MISSED; ended -> CallState.ENDED
            call.state == Call.STATE_RINGING -> CallState.INCOMING; else -> CallState.ANSWERED }
        val available = supportedWatch(watchAddress()) != null
        val associated = WatchCallSetup.associated(this, watchAddress())
        val route = when (audio?.route) {
            CallAudioState.ROUTE_BLUETOOTH -> if (CallControlPolicy.sameAddress(activeBluetoothAddress(), watchAddress())) "BLUETOOTH" else "BLUETOOTH_OTHER"
            CallAudioState.ROUTE_SPEAKER -> "SPEAKER"
            else -> "PHONE"
        }
        val live = call.state == Call.STATE_ACTIVE || call.state == Call.STATE_HOLDING || call.state == Call.STATE_RINGING
        val status = when {
            ended -> ""
            !associated -> "Enable call controls for this connected watch in the phone Settings"
            call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING -> "Calling…"
            call.state == Call.STATE_HOLDING -> "On hold"
            route == "BLUETOOTH_OTHER" -> "Audio on another Bluetooth device"
            available -> "Watch call audio ready"
            else -> "Audio stays on phone. Enable Calls for this watch in phone Bluetooth settings"
        }
        return CallPayload(state = state, number = number(call), callerName = entry.name.ifBlank {
            call.details.callerDisplayName.orEmpty().ifBlank { number(call).ifBlank { "Unknown caller" } }
        }, callId = entry.id, canAnswer = associated && call.state == Call.STATE_RINGING,
            canEnd = associated && !ended && live,
            canControlAudio = associated && !ended && audio != null && (call.state == Call.STATE_ACTIVE || call.state == Call.STATE_HOLDING),
            startedAt = entry.startedAt, muted = audio?.isMuted == true,
            audioRoute = route, bluetoothAudioAvailable = available, statusText = status)
    }

    private fun publishCurrent() {
        val call = currentCall() ?: return
        val snapshot = snapshot(call, tracked[call] ?: return)
        CallMonitorService.publish(applicationContext, snapshot)
        settle(snapshot)
    }

    private fun execute(request: CallActionPayload, address: String, reply: (CallControlStatePayload) -> Unit) {
        completed[request.requestId]?.let { saved ->
            if (saved.callId == request.callId && saved.action == request.action) reply(saved)
            else reply(result(request, false, "Request ID has already been used"))
            return
        }
        if (pending.containsKey(request.requestId)) return
        val current = CallMonitorService.latestCallPayload
        if (CallControlPolicy.action(request.action) == "STATE") {
            publishCurrent()
            reply(result(request, true, null))
            return
        }
        val call = tracked.keys.firstOrNull { tracked[it]?.id == request.callId }
        if (!WatchCallSetup.associated(this, address)) {
            reply(result(request, false, "Enable call controls for this connected watch in the phone Settings")); return
        }
        val error = CallControlPolicy.rejection(request, current, address,
            PhonePreferences(this).syncCalls, call != null)
        if (error != null) { reply(result(request, false, error)); return }
        if (pending.values.any { it.request.callId == request.callId }) {
            reply(result(request, false, "Wait for the phone to confirm the previous action")); return
        }
        val action = CallControlPolicy.action(request.action)
        if (CallControlPolicy.confirmed(action, current, activeBluetoothAddress(), address)) {
            reply(result(request, true, null)); return
        }
        pending[request.requestId] = Pending(request, address, reply)
        try {
            when (action) {
                "ANSWER" -> call!!.answer(VideoProfile.STATE_AUDIO_ONLY)
                "REJECT" -> call!!.reject(false, null)
                "END" -> call!!.disconnect()
                "MUTE" -> setMuted(true)
                "UNMUTE" -> setMuted(false)
                "AUDIO_PHONE" -> {
                    val route = if ((audio?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_EARPIECE != 0)
                        CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_WIRED_HEADSET
                    if ((audio?.supportedRouteMask ?: 0) and route == 0) error("Phone audio is unavailable")
                    setAudioRoute(route)
                }
                "AUDIO_SPEAKER" -> {
                    if ((audio?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_SPEAKER == 0) error("Speaker audio is unavailable")
                    setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                }
                "AUDIO_BLUETOOTH" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    requestBluetoothAudio(supportedWatch(address) ?: error("Watch call audio is disconnected"))
            }
            handler.postDelayed({
                pending.remove(request.requestId)?.let { finish(it, false, "Phone did not confirm the action. Check the phone call screen") }
            }, 8_000L)
        } catch (e: Exception) {
            Log.w("WatchInCall", "Call action rejected", e)
            pending.remove(request.requestId)?.let { finish(it, false, e.message ?: "Phone rejected this call action") }
        }
    }

    private fun settle(snapshot: CallPayload) {
        pending.values.toList().filter { it.request.callId == snapshot.callId }.forEach {
            if (CallControlPolicy.confirmed(it.request.action, snapshot, activeBluetoothAddress(), it.address)) {
                pending.remove(it.request.requestId); finish(it, true, null)
            } else if (snapshot.state == CallState.ENDED || snapshot.state == CallState.MISSED) {
                pending.remove(it.request.requestId); finish(it, false, "Call ended before the action was confirmed")
            }
        }
    }

    private fun result(request: CallActionPayload, success: Boolean, error: String?): CallControlStatePayload {
        val state = CallMonitorService.latestCallPayload
        return CallControlStatePayload(request.requestId, request.callId, request.action, success, error,
            state.muted, state.audioRoute, state.bluetoothAudioAvailable)
    }

    private fun finish(command: Pending, success: Boolean, error: String?) {
        val response = result(command.request, success, error)
        completed[command.request.requestId] = response
        while (completed.size > 32) completed.remove(completed.keys.first())
        command.reply(response)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        callbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        pending.values.toList().forEach { finish(it, false, "Phone call service disconnected") }
        pending.clear(); tracked.clear(); callbacks.clear()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        // Telecom owns call routes and mute. Never clear a user's route when RFCOMM disconnects.
        super.onDestroy()
    }
}

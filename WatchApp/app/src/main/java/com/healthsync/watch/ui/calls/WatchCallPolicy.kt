package com.healthsync.watch.ui.calls

import com.healthsync.watch.data.CallPayload
import com.healthsync.watch.data.CallState

/** Call identities keep delayed events and command results away from a newer call. */
object WatchCallPolicy {
    fun active(call: CallPayload?) = call?.state == CallState.INCOMING || call?.state == CallState.ANSWERED
    fun accepts(current: CallPayload?, incoming: CallPayload): Boolean {
        if (!active(current) || incoming.callId.orEmpty().isBlank() || current?.callId.isNullOrBlank()) return true
        if (incoming.callId == current?.callId) return true
        return incoming.state == CallState.INCOMING || incoming.state == CallState.ANSWERED
    }
    fun callerLabel(call: CallPayload): String = call.callerName.orEmpty().trim().take(120)
        .takeUnless { it.isBlank() || it.equals("Unknown", true) || it.equals("Private", true) }
        ?: call.number.orEmpty().trim().take(80).ifBlank { "Unknown caller" }
    fun durationSeconds(startedAt: Long, now: Long): Long =
        if (startedAt <= 0L || startedAt > now) 0L else ((now - startedAt) / 1000L).coerceAtMost(86_400L)
    fun shouldRouteAnsweredCallToWatch(previous: CallPayload?, pendingAction: String, incoming: CallPayload): Boolean =
        pendingAction == "ANSWER" && previous?.callId == incoming.callId && previous?.state == CallState.INCOMING &&
            incoming.state == CallState.ANSWERED && incoming.canControlAudio && incoming.bluetoothAudioAvailable && incoming.audioRoute != "BLUETOOTH"
    fun canSend(call: CallPayload?, connected: Boolean, pending: Boolean, action: String): Boolean {
        if (!connected || pending || !active(call) || call == null || call.callId.orEmpty().isBlank()) return false
        return when (action) {
            "ANSWER" -> call.state == CallState.INCOMING && call.canAnswer
            "REJECT" -> call.state == CallState.INCOMING && call.canEnd
            "END" -> call.state == CallState.ANSWERED && call.canEnd
            "MUTE", "UNMUTE", "SPEAKER_ON", "SPEAKER_OFF", "AUDIO_PHONE" -> call.state == CallState.ANSWERED && call.canControlAudio
            "AUDIO_BLUETOOTH" -> call.state == CallState.ANSWERED && call.canControlAudio && call.bluetoothAudioAvailable
            "STATE" -> true
            else -> false
        }
    }
}

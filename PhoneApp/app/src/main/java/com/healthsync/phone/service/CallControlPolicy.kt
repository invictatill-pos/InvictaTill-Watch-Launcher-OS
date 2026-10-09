package com.healthsync.phone.service

import com.healthsync.phone.data.model.CallActionPayload
import com.healthsync.phone.data.model.CallPayload
import com.healthsync.phone.data.model.CallState
import java.util.Locale

/** Decisions are made against the phone's current call, never a watch's optimistic state. */
object CallControlPolicy {
    fun action(raw: String): String = when (val value = raw.uppercase(Locale.ROOT)) {
        "PICK" -> "ANSWER"
        "DECLINE" -> "REJECT"
        "SCO_ON" -> "AUDIO_BLUETOOTH"
        "SCO_OFF", "SPEAKER_OFF" -> "AUDIO_PHONE"
        "SPEAKER_ON" -> "AUDIO_SPEAKER"
        else -> value
    }

    fun rejection(request: CallActionPayload, current: CallPayload, connectedAddress: String,
                  enabled: Boolean, controllable: Boolean): String? {
        if (!enabled) return "Call controls are disabled on the phone"
        if (request.requestId <= 0L || request.callId.isBlank()) return "Refresh the watch call screen and try again"
        if (request.callId != current.callId) return "This call has already changed"
        if (connectedAddress.isBlank()) return "Watch is disconnected"
        if (request.deviceAddress.isNotBlank() && !sameAddress(request.deviceAddress, connectedAddress))
            return "This request belongs to another watch"
        if (!controllable) return "Enable watch call controls in the phone Settings"
        return when (action(request.action)) {
            "ANSWER" -> if (current.state != CallState.INCOMING || !current.canAnswer) "This call cannot be answered" else null
            "REJECT" -> if (current.state != CallState.INCOMING || !current.canEnd) "This call is no longer ringing" else null
            "END" -> if (current.state != CallState.ANSWERED || !current.canEnd) "There is no active call to end" else null
            "MUTE", "UNMUTE", "AUDIO_PHONE", "AUDIO_SPEAKER" ->
                when {
                    current.state != CallState.ANSWERED -> "Answer the call before changing its audio"
                    !current.canControlAudio -> "Use the phone call screen to change audio"
                    else -> null
                }
            "AUDIO_BLUETOOTH" -> when {
                current.state != CallState.ANSWERED -> "Answer the call before changing its audio"
                !current.canControlAudio -> "Use the phone call screen to change audio"
                !current.bluetoothAudioAvailable -> "Watch call audio is unavailable. Enable Calls for the watch in phone Bluetooth settings"
                else -> null
            }
            else -> "Unsupported call action"
        }
    }

    fun sameAddress(first: String?, second: String?): Boolean =
        !first.isNullOrBlank() && !second.isNullOrBlank() && first.equals(second, ignoreCase = true)

    fun confirmed(action: String, current: CallPayload, activeBluetoothAddress: String?, watchAddress: String): Boolean =
        when (action(action)) {
            "ANSWER" -> current.state == CallState.ANSWERED
            "REJECT", "END" -> current.state == CallState.ENDED || current.state == CallState.MISSED
            "MUTE" -> current.muted
            "UNMUTE" -> !current.muted
            "AUDIO_PHONE" -> current.audioRoute == "PHONE"
            "AUDIO_SPEAKER" -> current.audioRoute == "SPEAKER"
            "AUDIO_BLUETOOTH" -> current.audioRoute == "BLUETOOTH" && sameAddress(activeBluetoothAddress, watchAddress)
            else -> false
        }
}

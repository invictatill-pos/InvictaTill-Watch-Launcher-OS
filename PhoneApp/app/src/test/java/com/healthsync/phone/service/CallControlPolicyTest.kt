package com.healthsync.phone.service

import com.healthsync.phone.data.model.CallActionPayload
import com.healthsync.phone.data.model.CallPayload
import com.healthsync.phone.data.model.CallState
import org.junit.Assert.*
import org.junit.Test

class CallControlPolicyTest {
    private val watch = "AA:BB:CC:DD:EE:FF"
    private val ringing = CallPayload(CallState.INCOMING, "+911234567890", "Caller", "call-1", canAnswer = true, canEnd = true)
    private val active = ringing.copy(state = CallState.ANSWERED, canAnswer = false, canControlAudio = true, bluetoothAudioAvailable = true)
    private fun request(action: String = "ANSWER", id: String = "call-1") = CallActionPayload(action, callId = id, requestId = 41L)
    private fun rejection(request: CallActionPayload, current: CallPayload = ringing, enabled: Boolean = true,
                          control: Boolean = true, address: String = watch) =
        CallControlPolicy.rejection(request, current, address, enabled, control)

    @Test fun stalePopupCannotAnswerDifferentCaller() { assertNotNull(rejection(request(id = "old-call"))) }
    @Test fun idLessLegacyActionCannotControlTheCurrentCall() { assertNotNull(rejection(request().copy(callId = ""))) }
    @Test fun untrackedRequestCannotControlCall() { assertNotNull(rejection(request().copy(requestId = 0L))) }
    @Test fun disconnectedWatchCannotControlCall() { assertNotNull(rejection(request(), address = "")) }
    @Test fun wrongWatchCannotRouteCallAudio() {
        assertNotNull(rejection(request("AUDIO_BLUETOOTH").copy(deviceAddress = "11:22:33:44:55:66"), active))
    }
    @Test fun callerCapabilitiesRequireAndroidCallAccess() {
        assertNotNull(rejection(request(), control = false))
        assertNotNull(rejection(request(), ringing.copy(canAnswer = false)))
    }
    @Test fun disabledMirroringDisablesCommandsIncludingEnd() {
        assertNotNull(rejection(request("END"), active, enabled = false))
    }
    @Test fun onlyRingingCallsCanBeAnsweredOrRejected() {
        assertNull(rejection(request("ANSWER")))
        assertNull(rejection(request("REJECT")))
        assertNotNull(rejection(request("ANSWER"), active))
        assertNotNull(rejection(request("REJECT"), active))
        assertNotNull(rejection(request("END"), ringing))
    }
    @Test fun audioRequiresActiveCallAndHfpSupport() {
        assertNotNull(rejection(request("MUTE"), ringing))
        assertNotNull(rejection(request("MUTE"), active.copy(canControlAudio = false)))
        assertNotNull(rejection(request("AUDIO_BLUETOOTH"), active.copy(bluetoothAudioAvailable = false)))
        assertNull(rejection(request("AUDIO_BLUETOOTH"), active))
    }
    @Test fun answerIsConfirmedOnlyByActivePhoneState() {
        assertFalse(CallControlPolicy.confirmed("ANSWER", ringing, null, watch))
        assertTrue(CallControlPolicy.confirmed("ANSWER", active, null, watch))
    }
    @Test fun genericBluetoothRouteNeverConfirmsWrongHeadset() {
        val bluetooth = active.copy(audioRoute = "BLUETOOTH")
        assertFalse(CallControlPolicy.confirmed("AUDIO_BLUETOOTH", bluetooth, "11:22:33:44:55:66", watch))
        assertFalse(CallControlPolicy.confirmed("AUDIO_BLUETOOTH", bluetooth, null, watch))
        assertTrue(CallControlPolicy.confirmed("AUDIO_BLUETOOTH", bluetooth, watch.lowercase(), watch))
    }
    @Test fun muteAndRouteButtonsFollowPhoneResults() {
        assertFalse(CallControlPolicy.confirmed("MUTE", active, null, watch))
        assertTrue(CallControlPolicy.confirmed("MUTE", active.copy(muted = true), null, watch))
        assertFalse(CallControlPolicy.confirmed("SPEAKER_ON", active, null, watch))
        assertTrue(CallControlPolicy.confirmed("SPEAKER_ON", active.copy(audioRoute = "SPEAKER"), null, watch))
    }
    @Test fun malformedOrUnknownActionIsRejected() {
        assertNotNull(rejection(request("START_SOMETHING")))
        assertNotNull(rejection(request("")))
        assertEquals("AUDIO_BLUETOOTH", CallControlPolicy.action("sco_on"))
    }
}

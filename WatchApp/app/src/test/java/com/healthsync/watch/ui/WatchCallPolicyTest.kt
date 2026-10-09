package com.healthsync.watch.ui

import com.healthsync.watch.data.*
import com.healthsync.watch.ui.calls.WatchCallPolicy
import org.junit.Assert.*
import org.junit.Test
import com.google.gson.Gson

class WatchCallPolicyTest {
    private fun call(state: CallState = CallState.INCOMING, id: String = "new") =
        CallPayload(state, "123", "Alice", callId = id, canAnswer = true, canEnd = true, canControlAudio = true)
    @Test fun oldCallTerminationCannotCloseNewCall() { assertFalse(WatchCallPolicy.accepts(call(), call(CallState.ENDED, "old"))) }
    @Test fun stateUpdatesForCurrentCallAreAccepted() { assertTrue(WatchCallPolicy.accepts(call(), call(CallState.ANSWERED))) }
    @Test fun missingIdentityDisablesRemoteActions() { assertFalse(WatchCallPolicy.canSend(call(id = ""), true, false, "ANSWER")) }
    @Test fun pendingAndOfflineDisableControls() {
        assertFalse(WatchCallPolicy.canSend(call(), true, true, "ANSWER"))
        assertFalse(WatchCallPolicy.canSend(call(), false, false, "ANSWER"))
    }
    @Test fun answerRequiresRingingAndCapability() {
        assertTrue(WatchCallPolicy.canSend(call(), true, false, "ANSWER"))
        assertFalse(WatchCallPolicy.canSend(call().copy(canAnswer = false), true, false, "ANSWER"))
        assertFalse(WatchCallPolicy.canSend(call(CallState.ANSWERED), true, false, "ANSWER"))
    }
    @Test fun watchAudioRequiresConfirmedHfpAvailability() {
        assertFalse(WatchCallPolicy.canSend(call(CallState.ANSWERED), true, false, "AUDIO_BLUETOOTH"))
        assertTrue(WatchCallPolicy.canSend(call(CallState.ANSWERED).copy(bluetoothAudioAvailable = true), true, false, "AUDIO_BLUETOOTH"))
    }
    @Test fun legacyAnswerEndCapabilityDoesNotEnableAudioControls() {
        val legacy = call(CallState.ANSWERED).copy(canControlAudio = false)
        assertTrue(WatchCallPolicy.canSend(legacy, true, false, "END"))
        assertFalse(WatchCallPolicy.canSend(legacy, true, false, "MUTE"))
        assertFalse(WatchCallPolicy.canSend(legacy, true, false, "SPEAKER_ON"))
    }
    @Test fun watchAnswerPrefersAvailableWatchAudioOnlyAfterPhoneConfirms() {
        val incoming = call()
        val active = call(CallState.ANSWERED).copy(bluetoothAudioAvailable = true)
        assertTrue(WatchCallPolicy.shouldRouteAnsweredCallToWatch(incoming, "ANSWER", active))
        assertFalse(WatchCallPolicy.shouldRouteAnsweredCallToWatch(incoming, "", active))
        assertFalse(WatchCallPolicy.shouldRouteAnsweredCallToWatch(incoming, "ANSWER", active.copy(bluetoothAudioAvailable = false)))
        assertFalse(WatchCallPolicy.shouldRouteAnsweredCallToWatch(incoming, "ANSWER", active.copy(callId = "other")))
        assertFalse(WatchCallPolicy.shouldRouteAnsweredCallToWatch(incoming, "ANSWER", active.copy(canControlAudio = false)))
    }
    @Test fun callerFallsBackToNumber() { assertEquals("123", WatchCallPolicy.callerLabel(call().copy(callerName = "Unknown"))) }
    @Test fun legacyCallWireCanUpdateAndClearWithoutAddedIdentity() {
        val legacy = Gson().fromJson("""{"state":"ENDED","number":"123"}""", CallPayload::class.java)
        assertTrue(WatchCallPolicy.accepts(call(), legacy))
        assertEquals("123", WatchCallPolicy.callerLabel(legacy))
        assertFalse(WatchCallPolicy.canSend(legacy, true, false, "ANSWER"))
    }
    @Test fun invalidOrFutureDurationNeverNegative() {
        assertEquals(0L, WatchCallPolicy.durationSeconds(0L, 3000L))
        assertEquals(0L, WatchCallPolicy.durationSeconds(5000L, 3000L))
        assertEquals(2L, WatchCallPolicy.durationSeconds(1000L, 3000L))
    }
}

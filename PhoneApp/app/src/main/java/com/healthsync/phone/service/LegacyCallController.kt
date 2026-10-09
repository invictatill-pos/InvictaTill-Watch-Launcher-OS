package com.healthsync.phone.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.CallActionPayload
import com.healthsync.phone.data.model.CallControlStatePayload
import com.healthsync.phone.data.model.CallPayload
import com.healthsync.phone.data.model.CallState

/** Android 8–11 compatibility only. Audio stays with Telecom; media-button hacks are never used. */
@Suppress("DEPRECATION")
internal object LegacyCallController {
    private val handler = Handler(Looper.getMainLooper())
    private data class Pending(val request: CallActionPayload, val reply: (CallControlStatePayload) -> Unit)
    private var pending: Pending? = null
    private val completed = linkedMapOf<Long, CallControlStatePayload>()

    fun permission(context: Context): Boolean = ContextCompat.checkSelfPermission(context,
        Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    fun rejected(callId: String): Boolean = pending?.request?.let {
        it.callId == callId && CallControlPolicy.action(it.action) == "REJECT"
    } == true

    fun handle(context: Context, request: CallActionPayload, address: String, reply: (CallControlStatePayload) -> Unit) {
        handler.post {
            val current = CallMonitorService.latestCallPayload
            if (CallControlPolicy.action(request.action) == "STATE") { reply(result(request, true, null)); return@post }
            completed[request.requestId]?.let {
                if (it.callId == request.callId && it.action == request.action) reply(it)
                else reply(result(request, false, "Request ID has already been used"))
                return@post
            }
            val action = CallControlPolicy.action(request.action)
            val error = CallControlPolicy.rejection(request, current, address,
                PhonePreferences(context).syncCalls, permission(context))
                ?: when {
                    action !in setOf("ANSWER", "REJECT", "END") -> "Call audio controls require Android 12 or newer. Use the phone call screen"
                    action != "ANSWER" && Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> "Ending calls from the watch requires Android 9 or newer"
                    CallMonitorService.legacyCallAmbiguous -> "Multiple calls are present. Use the phone call screen"
                    else -> null
                }
            if (error != null) { reply(result(request, false, error)); return@post }
            if (pending != null) {
                if (pending?.request == request) return@post
                reply(result(request, false, "Wait for the phone to confirm the previous action")); return@post
            }
            val command = Pending(request, reply)
            pending = command
            try {
                // Recheck at the operation: a runtime permission can be revoked after validation.
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    finish(command, false, "Allow Phone call permission in the phone Settings"); return@post
                }
                val telecom = context.getSystemService(TelecomManager::class.java) ?: error("Phone call service is unavailable")
                when (action) {
                    "ANSWER" -> telecom.acceptRingingCall()
                    "REJECT", "END" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !telecom.endCall())
                        error("Phone could not end the call")
                }
                handler.postDelayed({
                    if (pending === command) finish(command, false, "Phone did not confirm the action. Check the phone call screen")
                }, 8_000L)
            } catch (_: SecurityException) {
                finish(command, false, "Phone call access was revoked. Allow Phone call permission in the phone Settings")
            } catch (e: Exception) { finish(command, false, e.message ?: "Phone rejected the call action") }
        }
    }

    fun changed(current: CallPayload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val command = pending ?: return
        if (command.request.callId != current.callId) { finish(command, false, "This call has changed"); return }
        if (CallControlPolicy.confirmed(command.request.action, current, null, "")) finish(command, true, null)
        else if (current.state == CallState.ENDED || current.state == CallState.MISSED)
            finish(command, false, "Call ended before the action was confirmed")
    }

    private fun result(request: CallActionPayload, success: Boolean, error: String?) =
        CallControlStatePayload(requestId = request.requestId, callId = request.callId, action = request.action,
            success = success, error = error, audioRoute = "PHONE")

    private fun finish(command: Pending, success: Boolean, error: String?) {
        if (pending === command) pending = null
        val state = result(command.request, success, error)
        completed[command.request.requestId] = state
        while (completed.size > 32) completed.remove(completed.keys.first())
        command.reply(state)
    }
}

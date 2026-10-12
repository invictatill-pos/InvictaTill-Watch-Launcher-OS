package com.healthsync.phone.data.watchface

import com.google.gson.Gson
import com.healthsync.phone.data.model.WatchFaceInstallPayload
import com.healthsync.phone.service.BluetoothSyncService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.healthsync.phone.data.model.WatchFaceAckPayload
import java.util.UUID

internal fun faceAcknowledgementMatches(faceId: String, requestId: String, watchCode: Int?, acknowledgement: WatchFaceAckPayload?): Boolean =
    acknowledgement != null && acknowledgement.id == faceId &&
        (acknowledgement.requestId == requestId || watchCode != null && watchCode in 1..25 && acknowledgement.requestId.isNullOrBlank())

/**
 * Handles serializing and transmitting .hswf dynamic watch faces
 * to the connected smartwatch over Bluetooth RFCOMM.
 */
object WatchFaceSender {
    private val gson = Gson()
    private val transferMutex = Mutex()

    suspend fun sendFaceToWatch(pkg: HswfPackage, setActive: Boolean = true): Boolean = transferMutex.withLock {
        val requestId = UUID.randomUUID().toString()
        val watchCode = BluetoothSyncService.connectedWatchVersionCode.value
        val json = gson.toJson(pkg)
        val payload = WatchFaceInstallPayload(
            id = pkg.id,
            name = pkg.name,
            jsonContent = json,
            setActive = setActive,
            requestId = requestId
        )
        val sent = BluetoothSyncService.sendWatchFaceToWatch(payload)
        if (!sent) return@withLock false

        // Await confirmation ACK from watch if available
        val confirmed = withTimeoutOrNull(10_000) {
            while (true) {
                if (!BluetoothSyncService.connectionState.value.isConnected) return@withTimeoutOrNull false
                val ack = BluetoothSyncService.watchFaceAck.value
                if (faceAcknowledgementMatches(pkg.id, requestId, watchCode, ack)) {
                    return@withTimeoutOrNull ack!!.success
                }
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        confirmed ?: false // A queued frame is not proof that the face installed.
    }
}

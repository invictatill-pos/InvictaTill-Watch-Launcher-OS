package com.healthsync.phone.data.watchface

import com.google.gson.Gson
import com.healthsync.phone.data.model.WatchFaceInstallPayload
import com.healthsync.phone.service.BluetoothSyncService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles serializing and transmitting .hswf dynamic watch faces
 * to the connected smartwatch over Bluetooth RFCOMM.
 */
object WatchFaceSender {
    private val gson = Gson()

    suspend fun sendFaceToWatch(pkg: HswfPackage, setActive: Boolean = true): Boolean {
        val json = gson.toJson(pkg)
        val payload = WatchFaceInstallPayload(
            id = pkg.id,
            name = pkg.name,
            jsonContent = json,
            setActive = setActive
        )
        val sent = BluetoothSyncService.sendWatchFaceToWatch(payload)
        if (!sent) return false

        // Await confirmation ACK from watch if available
        val confirmed = withTimeoutOrNull(3500) {
            while (true) {
                val ack = BluetoothSyncService.watchFaceAck.value
                if (ack?.id == pkg.id) {
                    return@withTimeoutOrNull ack.success
                }
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        return confirmed ?: true // Fallback to true if frame was queued and sent
    }
}

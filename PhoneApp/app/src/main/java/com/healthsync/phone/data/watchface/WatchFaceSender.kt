package com.healthsync.phone.data.watchface

import com.google.gson.Gson
import com.healthsync.phone.data.model.WatchFaceInstallPayload
import com.healthsync.phone.service.BluetoothSyncService

/**
 * Handles serializing and transmitting .hswf dynamic watch faces
 * to the connected smartwatch over Bluetooth RFCOMM.
 */
object WatchFaceSender {
    private val gson = Gson()

    fun sendFaceToWatch(pkg: HswfPackage, setActive: Boolean = true): Boolean {
        val json = gson.toJson(pkg)
        val payload = WatchFaceInstallPayload(
            id = pkg.id,
            name = pkg.name,
            jsonContent = json,
            setActive = setActive
        )
        return BluetoothSyncService.sendWatchFaceToWatch(payload)
    }
}

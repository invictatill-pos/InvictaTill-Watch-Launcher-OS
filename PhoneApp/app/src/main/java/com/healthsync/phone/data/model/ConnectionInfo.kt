package com.healthsync.phone.data.model

import com.healthsync.phone.data.InternetStatusPayload

/** Live connection state shared between the service and UI via StateFlow */
data class ConnectionInfo(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val isListening: Boolean = false,  // true when RFCOMM server socket is open
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val powerSaveMode: String = "NORMAL",
    val storageFreeMb: Long = -1L,
    val storageTotalMb: Long = -1L,
    val androidVersion: String = "",
    val manufacturer: String = "",
    val hasHeartRateSensor: Boolean = true,
    val hasSpO2Sensor: Boolean = false,
    val hasStepSensor: Boolean = true,
    val lastUpdated: Long = 0L,
    val watchInternetStatus: InternetStatusPayload? = null,
    // Local monotonic receipt time, independent of the watch's clock.
    val watchInternetReceivedAt: Long = 0L,
    val supportsPhoneScreenShare: Boolean = false
)

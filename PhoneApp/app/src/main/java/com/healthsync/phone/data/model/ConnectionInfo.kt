package com.healthsync.phone.data.model

/** Live connection state shared between the service and UI via StateFlow */
data class ConnectionInfo(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val isListening: Boolean = false   // true when RFCOMM server socket is open
)

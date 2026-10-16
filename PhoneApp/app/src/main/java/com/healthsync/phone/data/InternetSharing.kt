package com.healthsync.phone.data

/** A snapshot of a device's default network; HealthSync's Bluetooth socket is separate. */
data class InternetStatusPayload(
    val transport: String = "unknown",
    val hasInternet: Boolean = false,
    val validated: Boolean = false
)

object InternetSharingPolicy {
    fun classify(
        hasDefaultNetwork: Boolean,
        readable: Boolean = true,
        bluetooth: Boolean = false,
        wifi: Boolean = false,
        cellular: Boolean = false,
        ethernet: Boolean = false,
        vpn: Boolean = false,
        hasInternet: Boolean = false,
        validated: Boolean = false
    ): InternetStatusPayload {
        if (!readable) return InternetStatusPayload()
        if (!hasDefaultNetwork) return InternetStatusPayload(transport = "none")
        val transport = when {
            // A VPN may expose its underlying transports. Do not claim direct Bluetooth sharing.
            vpn -> "vpn"
            bluetooth && !wifi && !cellular && !ethernet -> "bluetooth"
            wifi && !bluetooth && !cellular && !ethernet -> "wifi"
            cellular && !bluetooth && !wifi && !ethernet -> "cellular"
            ethernet && !bluetooth && !wifi && !cellular -> "ethernet"
            else -> "other"
        }
        return InternetStatusPayload(transport, hasInternet, validated)
    }

    fun isBluetoothInternet(status: InternetStatusPayload?): Boolean =
        status?.transport == "bluetooth" && status.hasInternet && status.validated

    fun statusText(status: InternetStatusPayload?): String {
        if (status == null || status.transport == "unknown") return "Internet status unavailable"
        if (status.transport == "none") return "No default internet connection"
        if (status.transport == "vpn") return "VPN active · Bluetooth sharing not confirmed"
        val connection = when (status.transport) {
            "bluetooth" -> "Bluetooth"
            "wifi" -> "Wi-Fi"
            "cellular" -> "mobile data"
            "ethernet" -> "Ethernet"
            else -> "another network"
        }
        return if (status.hasInternet && status.validated) "Internet via $connection"
        else "$connection · internet not verified"
    }
}

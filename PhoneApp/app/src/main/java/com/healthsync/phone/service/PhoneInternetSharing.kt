package com.healthsync.phone.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.healthsync.phone.data.InternetSharingPolicy
import com.healthsync.phone.data.InternetStatusPayload

/** Reads the selected default route without changing or binding any application's network. */
object PhoneInternetSharing {
    enum class SettingsDestination { TETHERING, NETWORK, SETTINGS, UNAVAILABLE }

    /** Opens Android's controls; this never changes tethering or reports it enabled. */
    fun openTetheringSettings(context: Context): SettingsDestination {
        val destinations = listOf(
            // Exported by AOSP Settings, but not guaranteed on every manufacturer's phone.
            "android.settings.TETHER_SETTINGS" to SettingsDestination.TETHERING,
            Settings.ACTION_WIRELESS_SETTINGS to SettingsDestination.NETWORK,
            Settings.ACTION_SETTINGS to SettingsDestination.SETTINGS
        )
        for ((action, destination) in destinations) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return destination
            } catch (_: android.content.ActivityNotFoundException) {
                // Try the next system settings route on devices without this screen.
            } catch (_: SecurityException) {
                // A vendor may restrict a settings screen even when it is installed.
            }
        }
        return SettingsDestination.UNAVAILABLE
    }

    fun readStatus(context: Context): InternetStatusPayload = try {
        val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) InternetStatusPayload()
        else {
            val network = manager.activeNetwork
            if (network == null) InternetSharingPolicy.classify(hasDefaultNetwork = false)
            else {
                val capabilities = manager.getNetworkCapabilities(network)
                if (capabilities == null) InternetStatusPayload()
                else InternetSharingPolicy.classify(
                    hasDefaultNetwork = true,
                    bluetooth = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH),
                    wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                    vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
        }
    } catch (_: Exception) {
        // Revoked access, missing vendor services and races are unknown, never a success.
        InternetStatusPayload()
    }
}

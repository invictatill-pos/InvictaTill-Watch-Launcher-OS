package com.healthsync.phone.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.healthsync.phone.data.InternetSharingPolicy
import com.healthsync.phone.data.InternetStatusPayload

/** Reads the selected default route without changing or binding any application's network. */
object PhoneInternetSharing {
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

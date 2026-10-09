package com.healthsync.phone.service

import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Build

object WatchCallSetup {
    fun associated(context: Context, address: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || address.isBlank()) return false
        return try {
            val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.myAssociations.any {
                    CallControlPolicy.sameAddress(it.deviceMacAddress?.toString(), address) &&
                        it.deviceProfile == android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
                }
            } else {
                @Suppress("DEPRECATION")
                manager.associations.any { CallControlPolicy.sameAddress(it, address) }
            }
        } catch (_: Exception) { false }
    }
}

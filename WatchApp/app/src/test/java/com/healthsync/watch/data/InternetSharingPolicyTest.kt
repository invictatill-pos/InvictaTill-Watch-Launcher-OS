package com.healthsync.watch.data

import org.junit.Assert.*
import org.junit.Test

class InternetSharingPolicyTest {
    @Test fun bluetoothNeedsInternetCapabilityAndSystemValidation() {
        val verified = InternetSharingPolicy.classify(true, bluetooth = true, hasInternet = true, validated = true)
        assertTrue(InternetSharingPolicy.isBluetoothInternet(verified))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(verified.copy(validated = false)))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(verified.copy(hasInternet = false)))
        assertEquals("Internet via Bluetooth", InternetSharingPolicy.statusText(verified))
    }

    @Test fun wifiAndCellularInternetNeverConfirmBluetoothSharing() {
        val wifi = InternetSharingPolicy.classify(true, wifi = true, hasInternet = true, validated = true)
        val mobile = InternetSharingPolicy.classify(true, cellular = true, hasInternet = true, validated = true)
        assertEquals("wifi", wifi.transport)
        assertEquals("cellular", mobile.transport)
        assertFalse(InternetSharingPolicy.isBluetoothInternet(wifi))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(mobile))
        assertEquals("Internet via Wi-Fi", InternetSharingPolicy.statusText(wifi))
    }

    @Test fun vpnWithUnderlyingBluetoothDoesNotClaimDirectSharing() {
        val status = InternetSharingPolicy.classify(true, vpn = true, bluetooth = true, hasInternet = true, validated = true)
        assertEquals("vpn", status.transport)
        assertFalse(InternetSharingPolicy.isBluetoothInternet(status))
        assertEquals("VPN active · Bluetooth sharing not confirmed", InternetSharingPolicy.statusText(status))
    }

    @Test fun missingAndUnreadableDefaultNetworksRemainDistinct() {
        assertEquals("none", InternetSharingPolicy.classify(false).transport)
        assertEquals("unknown", InternetSharingPolicy.classify(true, readable = false).transport)
        assertEquals("No default internet connection", InternetSharingPolicy.statusText(InternetSharingPolicy.classify(false)))
        assertEquals("Internet status unavailable", InternetSharingPolicy.statusText(null))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(null))
    }

    @Test fun localBluetoothAndMixedTransportCannotProduceSharingSuccess() {
        val local = InternetSharingPolicy.classify(true, bluetooth = true)
        val mixed = InternetSharingPolicy.classify(true, bluetooth = true, wifi = true, hasInternet = true, validated = true)
        assertEquals("Bluetooth · internet not verified", InternetSharingPolicy.statusText(local))
        assertEquals("other", mixed.transport)
        assertFalse(InternetSharingPolicy.isBluetoothInternet(local))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(mixed))
    }
}

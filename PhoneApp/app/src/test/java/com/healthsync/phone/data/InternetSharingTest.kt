package com.healthsync.phone.data

import com.google.gson.Gson
import com.healthsync.phone.data.model.DeviceInfoPayload
import org.junit.Assert.*
import org.junit.Test

class InternetSharingTest {
    private val bluetooth = InternetStatusPayload("bluetooth", hasInternet = true, validated = true)

    @Test fun successRequiresValidatedBluetoothDefaultRoute() {
        assertTrue(InternetSharingPolicy.isBluetoothInternet(bluetooth))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(bluetooth.copy(validated = false)))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(bluetooth.copy(hasInternet = false)))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(bluetooth.copy(transport = "wifi")))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(bluetooth.copy(transport = "vpn")))
        assertFalse(InternetSharingPolicy.isBluetoothInternet(null))
    }

    @Test fun ambiguousAndVpnRoutesDoNotReportBluetoothSharing() {
        assertEquals("other", InternetSharingPolicy.classify(true, bluetooth = true, wifi = true).transport)
        assertEquals("vpn", InternetSharingPolicy.classify(true, vpn = true, bluetooth = true).transport)
        assertEquals("unknown", InternetSharingPolicy.classify(true, readable = false).transport)
        assertEquals("none", InternetSharingPolicy.classify(false).transport)
    }

    @Test fun reportsExpireAfterNinetySecondsAndDisconnectClearsSuccess() {
        assertTrue(WatchInternetReport.isFresh(true, bluetooth, 1000L, 91_000L))
        assertFalse(WatchInternetReport.isFresh(true, bluetooth, 1000L, 91_001L))
        assertFalse(WatchInternetReport.isFresh(false, bluetooth, 1000L, 2000L))
        assertFalse(WatchInternetReport.isFresh(true, bluetooth, 2000L, 1000L))
        assertFalse(WatchInternetReport.isFresh(true, bluetooth, 0L, 1000L))
        assertFalse(WatchInternetReport.isFresh(true, null, 1000L, 2000L))
        assertEquals("Connect HealthSync to view watch status", WatchInternetReport.statusText(false, bluetooth, 1000L, 2000L))
        assertEquals("Watch status expired · refresh to check again", WatchInternetReport.statusText(true, bluetooth, 1000L, 91_001L))
    }

    @Test fun legacyWatchTelemetryCannotImplyInternetSharing() {
        val legacy = Gson().fromJson("""{"versionName":"2.9.2","versionCode":36}""", DeviceInfoPayload::class.java)
        assertNull(legacy.internetStatus)
        assertFalse(WatchInternetReport.isFresh(true, legacy.internetStatus, 1000L, 2000L))
        assertEquals("Watch internet status not reported · update both apps", WatchInternetReport.statusText(true, null, 0L, 2000L))
    }

    @Test fun deviceInfoRoundTripRetainsNetworkFlags() {
        val original = DeviceInfoPayload("2.9.3", 37, internetStatus = bluetooth)
        val restored = Gson().fromJson(Gson().toJson(original), DeviceInfoPayload::class.java)
        assertEquals(bluetooth, restored.internetStatus)
        assertEquals("Internet via Bluetooth", WatchInternetReport.statusText(true, restored.internetStatus, 1000L, 2000L))
    }
}

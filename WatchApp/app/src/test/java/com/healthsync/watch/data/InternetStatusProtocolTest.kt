package com.healthsync.watch.data

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class InternetStatusProtocolTest {
    @Test fun optionalNetworkStatusPreservesLegacyProtocol() {
        val gson = Gson()
        val legacy = gson.fromJson("""{"versionName":"2.9.2","versionCode":36}""", DeviceInfoPayload::class.java)
        assertNull(legacy.internetStatus)
        val current = DeviceInfoPayload("2.9.3", 37, internetStatus = InternetStatusPayload("bluetooth", true, true))
        assertEquals(current, gson.fromJson(gson.toJson(current), DeviceInfoPayload::class.java))
    }
}

package com.healthsync.phone.data

/** Reports are refreshed every minute. Never present an old snapshot as current. */
object WatchInternetReport {
    const val MAX_AGE_MS = 90_000L

    fun isFresh(connected: Boolean, status: InternetStatusPayload?, receivedAt: Long, now: Long): Boolean =
        connected && status != null && receivedAt > 0 && now >= receivedAt && now - receivedAt <= MAX_AGE_MS

    fun statusText(connected: Boolean, status: InternetStatusPayload?, receivedAt: Long, now: Long): String = when {
        !connected -> "Connect HealthSync to view watch status"
        status == null -> "Watch internet status not reported · update both apps"
        !isFresh(connected, status, receivedAt, now) -> "Watch status expired · refresh to check again"
        else -> InternetSharingPolicy.statusText(status)
    }
}

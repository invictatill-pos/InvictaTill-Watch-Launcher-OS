package com.healthsync.watch.data

import com.google.gson.Gson

// ─── Shared sync protocol (mirrors PhoneApp models) ──────────────────────────

data class SyncMessage(
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: String
)

enum class MessageType {
    NOTIFICATION, CALL_EVENT,
    HEART_RATE, HEART_RATE_STATUS, STEPS, SPO2, SLEEP, FALL_DETECTED,
    ACK, PING, SETTINGS_UPDATE, SENSOR_INTERVALS,
    FORCE_MEASURE_HR,
    FORCE_MEASURE_SPO2,
    WORKOUT_SESSION,
    BATCHED_SYNC,
    SYNC_ACK,
    REPLY_MESSAGE,
    CALL_ACTION,
    CALL_ANSWERED,  // phone → watch: call was answered, show active-call screen
    PHONE_CONTROL,
    PHONE_CONTROL_STATE,
    CALL_CONTROL_STATE,
    NOTIFICATION_REMOVED,
    REPLY_RESULT,
    NOTIFICATION_SNAPSHOT,
    OTA_START,
    OTA_CHUNK,
    OTA_COMPLETE,
    OTA_PROGRESS,
    WATCH_FACE_INSTALL,
    WATCH_FACE_ACK,
    DEVICE_INFO
}

data class DeviceInfoPayload(
    val versionName: String,
    val versionCode: Int,
    val model: String = "Kolabee U8 Ultra",
    val batteryPercent: Int = -1
)

data class WatchFaceInstallPayload(
    val id: String,
    val name: String,
    val jsonContent: String,
    val setActive: Boolean = true
)

data class WatchFaceAckPayload(
    val id: String,
    val success: Boolean,
    val message: String = ""
)

data class OtaStartPayload(
    val versionName: String,
    val versionCode: Int,
    val fileSize: Long,
    val totalChunks: Int,
    val sha256: String = ""
)

data class OtaChunkPayload(
    val chunkIndex: Int,
    val totalChunks: Int,
    val dataBase64: String,
    val offset: Long = 0L,
    val chunkSize: Int = 8192
)

data class OtaCompletePayload(
    val success: Boolean,
    val sha256: String = ""
)

data class OtaProgressPayload(
    val receivedChunks: Int,
    val totalChunks: Int,
    val percent: Int
)

data class PhoneControlPayload(val requestId: Long = 0L, val action: String = "STATE")
data class PhoneControlStatePayload(
    val requestId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val available: Boolean = false,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val findingPhone: Boolean = false,
    val error: String? = null
)

data class NotificationPayload(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val ticker: String = "",
    val time: Long = System.currentTimeMillis(),
    val canReply: Boolean = false,
    val notificationKey: String = "",
    val category: String = "",
    val isOngoing: Boolean = false,
    val isSilent: Boolean = false,
    val channelName: String = "",
    val conversationTitle: String = "",
    val isActive: Boolean = true
)

data class ReplyMessagePayload(
    val packageName: String,
    val replyText: String,
    val title: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val notificationKey: String = "",
    val requestId: Long = 0L
)

data class NotificationRemovedPayload(val notificationKey: String)
data class NotificationSnapshotPayload(val activeKeys: List<String>)
data class ReplyResultPayload(val requestId: Long = 0L, val notificationKey: String = "",
                              val success: Boolean = false, val error: String? = null)

data class CallActionPayload(
    val action: String, // "ANSWER" or "REJECT"
    val number: String = "",
    val callId: String = "",
    val requestId: Long = 0L,
    val deviceAddress: String = ""
)

data class CallPayload(
    val state: CallState,
    val number: String,
    val callerName: String = "Unknown",
    val callId: String = "",
    val canAnswer: Boolean = false,
    val canEnd: Boolean = false,
    val canControlAudio: Boolean = false,
    val startedAt: Long = 0L,
    val muted: Boolean = false,
    val audioRoute: String = "PHONE",
    val bluetoothAudioAvailable: Boolean = false,
    val statusText: String = ""
)

data class CallControlStatePayload(val requestId: Long = 0L, val callId: String = "",
    val action: String = "", val success: Boolean = false, val error: String? = null,
    val muted: Boolean = false, val audioRoute: String = "PHONE", val bluetoothAudioAvailable: Boolean = false)

data class ActiveCallPayload(
    val callerName: String = "Unknown",
    val number: String = ""
)

enum class CallState { INCOMING, ANSWERED, ENDED, MISSED }

data class HeartRatePayload(val bpm: Int, val accuracy: Int)
data class HeartRateStatusPayload(val status: String, val bpm: Int = -1, val readingTime: Long = 0L,
                                 val diagnosticReport: String? = null,
                                 val sensorBpm: Int = -1, val sensorReadingTime: Long = 0L)
data class StepsPayload(val steps: Int, val calories: Double)
data class SpO2Payload(val percentage: Float)
data class SleepPayload(
    val startTime: Long,
    val endTime: Long,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int
)
data class FallDetectedPayload(val severity: String)

data class LatLngPoint(
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    val segmentStart: Boolean = false
)

data class WorkoutPayload(
    val activityType: String,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Float,
    val calories: Double,
    val avgHeartRate: Int,
    val steps: Int,
    val swings: Int = 0,
    val route: List<LatLngPoint> = emptyList(),
    val durationSeconds: Int = 0
)

// ─── Batched Sync Protocol Models ──────────────────────────────────────────────

data class DailyStepBucketPayload(
    val timestamp: Long,
    val steps: Int,
    val calories: Double
)

data class WorkoutSessionPayload(
    val session_id: String,
    val activity_type: String, // "WALK", "RUN", "HOME_WORKOUT", "BADMINTON"
    val start_time: Long,
    val end_time: Long,
    val duration_sec: Int,
    val distance_m: Float,
    val calories: Double,
    val step_count: Int,
    val swings: Int = 0,
    val avg_hr: Int,
    val route_json: String = "[]" // Serialized List<LatLngPoint>
)

data class BatchedSyncPayload(
    val device_id: String = "WATCH_HEALTHSYNC",
    val sync_timestamp: Long = System.currentTimeMillis(),
    val daily_steps: List<DailyStepBucketPayload> = emptyList(),
    val workouts: List<WorkoutSessionPayload> = emptyList()
)

data class SyncAckPayload(
    val session_ids: List<String> = emptyList(),
    val step_timestamps: List<Long> = emptyList()
)

/** Sent from phone to watch to configure sensor intervals */
data class WatchSettingsPayload(
    val hrIntervalMs: Long,    // e.g. 60_000 = 1 min; -1 = disabled
    val spo2IntervalMs: Long,   // e.g. 300_000 = 5 min; -1 = disabled
    val stepGoal: Int = 10_000,
    val userWeightKg: Double = 70.0,
    val userHeightCm: Int = 170,
    val sensorSettingsRevision: Long = 0L
)

/** Bidirectional interval snapshot; profile settings remain phone-owned. */
data class SensorIntervalsPayload(val hrIntervalMs: Long, val spo2IntervalMs: Long, val revision: Long)

// ─── Helper extension ─────────────────────────────────────────────────────────
inline fun <reified T> SyncMessage.parsePayload(gson: Gson): T = gson.fromJson(payload, T::class.java)


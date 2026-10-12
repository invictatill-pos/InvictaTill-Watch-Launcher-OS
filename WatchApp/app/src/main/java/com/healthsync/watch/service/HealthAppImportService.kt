package com.healthsync.watch.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.healthsync.watch.sensor.HealthAppMetric
import com.healthsync.watch.sensor.InstalledHealthAppFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A bounded public-app import, independent of BODY_SENSORS and ACTIVITY_RECOGNITION access.
 * This service never starts a sensor, another foreground service, or a health acquisition.
 */
class HealthAppImportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val pending = linkedSetOf<HealthAppMetric>()
    private val requested = mutableSetOf<HealthAppMetric>()
    private var activeMetric: HealthAppMetric? = null
    private var worker: Job? = null
    private var foregroundStarted = false
    private var latestStartId = 0
    private val deadline = Runnable {
        reportOutstanding("Health app data check timed out. Open HealthSync and retry.")
        scope.cancel()
        finishService()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Health app imports", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val metric = when (intent?.action) {
            ACTION_HEART_RATE -> HealthAppMetric.HEART_RATE
            ACTION_OXYGEN -> HealthAppMetric.OXYGEN
            else -> { stopSelf(startId); return START_NOT_STICKY }
        }
        if (!foregroundStarted) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("HealthSync")
                .setContentText("Checking readings shared by watch health apps")
                .setOngoing(true).setSilent(true).build()
            try {
                if (Build.VERSION.SDK_INT >= 34)
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                else startForeground(NOTIFICATION_ID, notification)
                foregroundStarted = true
                // At most one five-second read per metric, with a lifetime well below three minutes.
                handler.postDelayed(deadline, SERVICE_DEADLINE_MS)
            } catch (error: RuntimeException) {
                publishUnavailable(this, metric, "Health app data check could not start. Open HealthSync and retry.")
                Log.w(TAG, "Public health app import could not enter foreground", error)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        // Coalesce rapid button taps and serialize the two metrics without an unbounded backlog.
        if (requested.add(metric)) pending += metric
        if (worker?.isActive != true) {
            worker = scope.launch {
                try {
                    while (pending.isNotEmpty()) {
                        val next = pending.first()
                        pending.remove(next)
                        activeMetric = next
                        try {
                            SensorCollectorService.publishAppFallbackStatus(this@HealthAppImportService,
                                next, "checking_health_apps")
                            val result = withContext(Dispatchers.IO) {
                                InstalledHealthAppFallback(this@HealthAppImportService).readLatest(next)
                            }
                            val accepted = result.reading?.let {
                                SensorCollectorService.importHealthAppReading(this@HealthAppImportService, it)
                            } == true
                            if (accepted) {
                                SensorCollectorService.publishAppFallbackStatus(this@HealthAppImportService,
                                    next, "app_reading")
                            } else {
                                val message = if (result.reading != null)
                                    "The shared app reading is unavailable or older than the current capture."
                                else result.message
                                publishUnavailable(this@HealthAppImportService, next, message)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            publishUnavailable(this@HealthAppImportService, next,
                                "Health app data check failed. Open the original health app and retry.")
                            Log.w(TAG, "Public health app import failed", error)
                        } finally {
                            activeMetric = null
                        }
                    }
                } finally {
                    worker = null
                    if (pending.isNotEmpty()) reportOutstanding("Health app data check was interrupted. Open HealthSync and retry.")
                    finishService()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** API 34's shortService callback; newer two-argument callbacks aren't needed for this type. */
    override fun onTimeout(startId: Int) {
        reportOutstanding("Health app data check timed out. Open HealthSync and retry.")
        scope.cancel()
        finishService()
    }

    private fun reportOutstanding(message: String) {
        val outstanding = pending.toMutableSet().apply { activeMetric?.let { add(it) } }
        pending.clear()
        activeMetric = null
        outstanding.forEach { publishUnavailable(this, it, message) }
    }

    @Suppress("DEPRECATION")
    private fun finishService() {
        handler.removeCallbacks(deadline)
        if (foregroundStarted) {
            stopForeground(true)
            foregroundStarted = false
        }
        stopSelfResult(latestStartId)
    }

    override fun onDestroy() {
        reportOutstanding("Health app data check was interrupted. Open HealthSync and retry.")
        handler.removeCallbacks(deadline)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_HEART_RATE = "com.healthsync.watch.IMPORT_APP_HEART_RATE"
        const val ACTION_OXYGEN = "com.healthsync.watch.IMPORT_APP_OXYGEN"
        private const val TAG = "HealthAppImport"
        private const val CHANNEL_ID = "health_app_import"
        private const val NOTIFICATION_ID = 2008
        private const val SERVICE_DEADLINE_MS = 15_000L

        /** Call from a visible app action or another permitted foreground-service start context. */
        fun start(context: Context, metric: HealthAppMetric): Boolean = try {
            val action = if (metric == HealthAppMetric.HEART_RATE) ACTION_HEART_RATE else ACTION_OXYGEN
            ContextCompat.startForegroundService(context, Intent(context, HealthAppImportService::class.java).setAction(action))
            true
        } catch (error: RuntimeException) {
            publishUnavailable(context, metric, "Health app data check could not start. Open HealthSync and retry.")
            Log.w(TAG, "Public health app import launch failed", error)
            false
        }

        private fun publishUnavailable(context: Context, metric: HealthAppMetric, message: String) {
            SensorCollectorService.publishAppFallbackStatus(context, metric, "permission_required",
                "Allow sensor access in App permissions. $message")
        }
    }
}

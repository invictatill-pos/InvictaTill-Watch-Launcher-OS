package com.healthsync.watch.service

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.healthsync.watch.data.WatchPreferences
import com.healthsync.watch.ui.BezelGeometry
import com.healthsync.watch.ui.WatchOptionsActivity

/** User-enabled round software bezel. It never consumes touches or takes focus. */
class BezelOverlayService : Service() {
    companion object {
        private const val CHANNEL = "software_bezel"
        private const val NOTIFICATION_ID = 2201
        private const val ACTION_STOP = "com.healthsync.watch.STOP_BEZEL"
        @Volatile var isRunning = false
            private set

        /** Retry from a visible Activity or permitted boot event; never assume a grant. */
        fun reconcile(context: Context): Boolean {
            if (!WatchPreferences(context).bezelEnabled || !Settings.canDrawOverlays(context)) {
                context.stopService(Intent(context, BezelOverlayService::class.java))
                return false
            }
            return runCatching {
                ContextCompat.startForegroundService(context, Intent(context, BezelOverlayService::class.java))
                true
            }.onFailure { Log.w("SoftwareBezel", "Overlay unavailable; reopen display settings", it) }.getOrDefault(false)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var mask: MaskView? = null
    private var appOps: AppOpsManager? = null
    private val permissionListener = AppOpsManager.OnOpChangedListener { op, pkg ->
        if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && pkg == packageName) handler.post {
            if (!Settings.canDrawOverlays(this)) stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        appOps = getSystemService(APP_OPS_SERVICE) as? AppOpsManager
        appOps?.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, permissionListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = WatchPreferences(this)
        if (intent?.action == ACTION_STOP) prefs.bezelEnabled = false
        if (!prefs.bezelEnabled || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Software bezel", NotificationManager.IMPORTANCE_LOW))
            val settings = Intent(this, WatchOptionsActivity::class.java)
                .putExtra(WatchOptionsActivity.EXTRA_SCREEN, WatchOptionsActivity.SCREEN_BEZEL)
            val open = PendingIntent.getActivity(this, NOTIFICATION_ID, settings,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, NOTIFICATION_ID,
                Intent(this, BezelOverlayService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_crop).setContentTitle("Round software bezel")
                .setContentText("Active over apps · tap for settings").setContentIntent(open)
                .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn off", stop).build())
            if (mask == null) {
                val view = MaskView(this)
                windows.addView(view, overlayParams())
                mask = view
            }
            mask?.diameterPercent = prefs.bezelDiameterPercent
            mask?.invalidate()
            isRunning = true
        } catch (error: Exception) {
            Log.w("SoftwareBezel", "Unable to attach bezel", error)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION") // TYPE_PHONE is required on Android 6 and 7.
    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val maximum = if (Build.VERSION.SDK_INT >= 31)
            (getSystemService(INPUT_SERVICE) as InputManager).maximumObscuringOpacityForTouch else 1f
        alpha = BezelGeometry.windowAlpha(Build.VERSION.SDK_INT, maximum)
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mask?.let { runCatching { windows.updateViewLayout(it, overlayParams()); it.invalidate() } }
    }

    override fun onDestroy() {
        mask?.let { runCatching { windows.removeViewImmediate(it) } }
        mask = null
        isRunning = false
        appOps?.stopWatchingMode(permissionListener)
        handler.removeCallbacksAndMessages(null)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class MaskView(context: Context) : View(context) {
        var diameterPercent = 100
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val path = Path()
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            path.addCircle(width / 2f, height / 2f, BezelGeometry.radius(width, height, diameterPercent), Path.Direction.CW)
            canvas.drawPath(path, paint)
        }
    }
}

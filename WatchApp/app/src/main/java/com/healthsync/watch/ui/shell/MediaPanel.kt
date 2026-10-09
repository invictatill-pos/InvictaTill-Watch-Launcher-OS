package com.healthsync.watch.ui.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.WatchFaceActivity
import kotlin.math.min
import kotlin.math.roundToInt

/** Now playing and phone finding use phone-confirmed state, never optimistic success. */
class MediaPanel(
    private val activity: AppCompatActivity,
    private val onClose: () -> Unit,
    private val onSettings: () -> Unit
) {
    private val accent = 0xFF8CE7C8.toInt()
    private val muted = 0xFFA8B9B1.toInt()
    private val surface = 0xFF17211D.toInt()
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val diameter = min(activity.resources.displayMetrics.widthPixels, activity.resources.displayMetrics.heightPixels)
        val side = (diameter * .14f).roundToInt()
        val end = (diameter * .16f).roundToInt().coerceAtLeast(dp(32))
        setPadding(side, end, side, end)
    }
    private val scroll = RoundScrollView(activity).apply {
        setBackgroundColor(Color.BLACK)
        isFillViewport = true
        clipToPadding = false
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    val view: View = scroll
    private val battery = label("Phone —", 11f, muted)
    private val title = label("Now playing", 19f, Color.WHITE).apply {
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
    }
    private val artist = label("", 12f, muted).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val status = label("", 11f, muted)
    private val findStatus = label("", 11f, muted)
    private val previous = MediaIcon(activity, "PREVIOUS")
    private val playPause = MediaIcon(activity, "PLAY_PAUSE")
    private val next = MediaIcon(activity, "NEXT")
    private lateinit var findButton: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var registered = false
    private var lastConnected = false
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!resumed) return
            val connected = BluetoothClientService.isConnected
            if (connected && !lastConnected) PhoneControlsBridge.requestState()
            lastConnected = connected
            render()
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (BluetoothClientService.isConnected) PhoneControlsBridge.requestState()
            render()
            handler.postDelayed(this, 6_000L)
        }
    }

    init {
        PhoneControlsBridge.initialize(activity)
        content.addView(label("Media", 24f, Color.WHITE), fullWidth())
        content.addView(battery, fullWidth().apply { topMargin = dp(5); bottomMargin = dp(17) })
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(16), dp(12), dp(14))
            clipChildren = false
            clipToPadding = false
            background = shape(surface, 25)
        }
        card.addView(title, fullWidth())
        card.addView(artist, fullWidth().apply { topMargin = dp(6); bottomMargin = dp(10) })
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(previous, playPause, next).forEach { button ->
            row.addView(button, LinearLayout.LayoutParams(0, dp(50), 1f))
            button.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (BluetoothClientService.isConnected && PhoneControlsBridge.snapshot.available && !PhoneControlsBridge.snapshot.pending) {
                    PhoneControlsBridge.sendMedia(button.action)
                    render()
                }
            }
        }
        val diameter = min(activity.resources.displayMetrics.widthPixels, activity.resources.displayMetrics.heightPixels)
        card.addView(row, LinearLayout.LayoutParams(maxOf(dp(144), (diameter * .72f).roundToInt() - dp(24)), dp(50)))
        card.addView(status, fullWidth().apply { topMargin = dp(10) })
        content.addView(card, fullWidth())
        findButton = action("Find phone") {
            val state = PhoneControlsBridge.snapshot
            if (BluetoothClientService.isConnected && !state.pending) {
                PhoneControlsBridge.findPhone(!state.findingPhone)
                render()
            }
        }
        content.addView(findButton, fullWidth().apply { topMargin = dp(13) })
        content.addView(findStatus, fullWidth().apply { topMargin = dp(6); bottomMargin = dp(8) })
        content.addView(action("Connection settings", onSettings), fullWidth().apply { topMargin = dp(5) })
        content.addView(action("Back", onClose), fullWidth().apply { topMargin = dp(6) })
        render()
    }

    fun onResume() {
        if (resumed) return
        resumed = true
        lastConnected = BluetoothClientService.isConnected
        val filter = IntentFilter().apply {
            addAction(PhoneControlsBridge.ACTION_CHANGED)
            addAction(WatchFaceActivity.ACTION_BT_STATUS)
        }
        registered = runCatching {
            ContextCompat.registerReceiver(activity, changes, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            true
        }.getOrDefault(false)
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    fun onPause() {
        resumed = false
        handler.removeCallbacksAndMessages(null)
        if (registered) runCatching { activity.unregisterReceiver(changes) }
        registered = false
    }

    fun destroy() = onPause()

    private fun render() {
        val connected = BluetoothClientService.isConnected
        val state = PhoneControlsBridge.snapshot
        battery.text = when {
            !connected -> "Phone offline"
            state.batteryPercent in 0..100 && state.lastUpdated > 0L -> "Phone ${state.batteryPercent}%${if (state.charging) " · charging" else ""}"
            else -> "Phone connected · battery —"
        }
        title.text = when {
            !connected -> "Connect your phone"
            state.available -> state.title.ifBlank { "Phone media" }
            state.pending && state.lastUpdated == 0L -> "Checking phone…"
            else -> "Nothing playing"
        }
        artist.text = if (connected && state.available) state.artist else "Start music on your phone"
        val enabled = connected && state.available && !state.pending
        previous.isEnabled = enabled
        playPause.isEnabled = enabled
        next.isEnabled = enabled
        previous.alpha = if (enabled) 1f else .35f
        playPause.alpha = if (enabled) 1f else .35f
        next.alpha = if (enabled) 1f else .35f
        previous.contentDescription = "Previous track"
        next.contentDescription = "Next track"
        playPause.playing = state.playing && state.available
        playPause.contentDescription = if (state.playing && state.available) "Pause media" else "Play media"
        status.text = when {
            !connected -> "Open connection settings to connect"
            state.pending -> "Updating…"
            state.error != null -> state.error
            !state.available -> "No active media player"
            state.playing -> "Playing on phone"
            else -> "Paused on phone"
        }
        findButton.text = if (connected && state.findingPhone) "Stop ringing" else "Find phone"
        findButton.contentDescription = if (connected && state.findingPhone) "Stop ringing your phone" else "Ring your connected phone"
        findButton.isEnabled = connected && !state.pending
        findButton.alpha = if (findButton.isEnabled) 1f else .4f
        findStatus.text = when {
            !connected -> "Phone connection required"
            state.pending -> "Waiting for phone…"
            state.findingPhone -> "Phone is ringing"
            state.error != null -> state.error
            else -> "Ring your phone to locate it nearby"
        }
    }

    private fun label(value: String, size: Float, color: Int) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private fun action(value: String, run: () -> Unit) = label(value, 12f, accent).apply {
        minimumHeight = dp(48)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x334DE0AE), shape(surface, 28), null)
        setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); run() }
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()

    private inner class MediaIcon(context: Context, val action: String) : View(context) {
        var playing = false
            set(value) { if (field != value) { field = value; invalidate() } }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        init {
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x334DE0AE), shape(Color.TRANSPARENT, 30), null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = min(width, height).toFloat()
            val cx = width / 2f
            val cy = height / 2f
            val primary = action == "PLAY_PAUSE"
            paint.color = if (primary) accent else 0xFF26352D.toInt()
            canvas.drawCircle(cx, cy, size / 2f - dp(3), paint)
            canvas.save()
            val scale = size / 49f
            canvas.translate(cx - 12 * scale, cy - 12 * scale)
            canvas.scale(scale, scale)
            paint.color = if (primary) Color.BLACK else Color.WHITE
            path.reset()
            when (action) {
                "PLAY_PAUSE" -> if (playing) {
                    canvas.drawRoundRect(6f, 5f, 10f, 19f, 1f, 1f, paint)
                    canvas.drawRoundRect(14f, 5f, 18f, 19f, 1f, 1f, paint)
                } else {
                    path.moveTo(8f, 4f); path.lineTo(20f, 12f); path.lineTo(8f, 20f); path.close()
                    canvas.drawPath(path, paint)
                }
                "NEXT" -> {
                    path.moveTo(5f, 5f); path.lineTo(16f, 12f); path.lineTo(5f, 19f); path.close()
                    canvas.drawPath(path, paint); canvas.drawRoundRect(17f, 5f, 20f, 19f, 1f, 1f, paint)
                }
                "PREVIOUS" -> {
                    path.moveTo(19f, 5f); path.lineTo(8f, 12f); path.lineTo(19f, 19f); path.close()
                    canvas.drawPath(path, paint); canvas.drawRoundRect(4f, 5f, 7f, 19f, 1f, 1f, paint)
                }
            }
            canvas.restore()
        }
    }
}

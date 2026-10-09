package com.healthsync.watch.ui.shell

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.healthsync.watch.data.WatchPreferences
import com.healthsync.watch.service.BluetoothClientService
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.launcher.LauncherBrightness
import java.util.Date
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** An embedded watch shade: actual state, circular touch targets, and in-place sound controls. */
class QuickControlsPanel(
    private val activity: AppCompatActivity,
    private val onClose: () -> Unit,
    private val onSettings: () -> Unit,
    private val onUtilities: () -> Unit
) {
    private val accent = 0xFF8CE7C8.toInt()
    private val secondary = 0xFFA8B9B1.toInt()
    private val surface = 0xFF17211D.toInt()
    private val controls = WatchSystemControls(activity)
    private val preferences = WatchPreferences(activity)
    private val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
    val view: View = root

    /** The clock shell can immediately reapply its AOD/interactive brightness policy. */
    var onDisplayChanged: () -> Unit = {}
    private val clock = text("", 25f, Color.WHITE)
    private val battery = text("", 12f, accent)
    private val connection = text("", 10f, secondary)
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var registered = false
    private var detail: View? = null
    private var torchBrightness: Float? = null
    val isFlashlightActive: Boolean get() = torchBrightness != null
    private var torchHadKeepScreenOn = false
    private val tiles = mutableMapOf<Glyph, Tile>()
    private val volumeRows = mutableListOf<VolumeRow>()
    private var adjustingBrightness = false
    private lateinit var brightness: SeekBar
    private lateinit var brightnessLabel: TextView

    private data class Tile(val title: String, val container: LinearLayout, val icon: GlyphView, val label: TextView)
    private data class VolumeRow(val name: String, val stream: Int, val slider: SeekBar, val value: TextView, var tracking: Boolean = false)
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (resumed) refresh()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            refresh()
            handler.postDelayed(this, 10_000L)
        }
    }
    private val delayedRefresh = Runnable { if (resumed) refresh() }

    init {
        val content = column()
        val scroll = RoundScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(Color.BLACK)
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(clock, fullWidth())
        content.addView(battery, fullWidth().apply { bottomMargin = dp(3) })
        content.addView(connection, fullWidth().apply { bottomMargin = dp(15) })
        createGrid(content)
        createBrightness(content)
        content.addView(pill("Tools", Glyph.TOOLS) { onUtilities() }, fullWidth().apply { topMargin = dp(8) })
        content.addView(pill("Back", Glyph.DOWN) { onClose() }, fullWidth().apply { topMargin = dp(6) })
        refresh()
    }

    fun onResume() {
        if (resumed) return
        resumed = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        registered = runCatching {
            // Some protected Bluetooth broadcasts come from a privileged process, not system UID.
            // Broadcast payloads are ignored; all state is read directly from Android services.
            ContextCompat.registerReceiver(activity, stateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            true
        }.getOrDefault(false)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun onPause() {
        resumed = false
        handler.removeCallbacksAndMessages(null)
        if (registered) runCatching { activity.unregisterReceiver(stateReceiver) }
        registered = false
        if (torchBrightness != null) closeDetail()
    }

    fun destroy() {
        onPause()
        closeDetail()
        onDisplayChanged = {}
    }

    /** Back closes a sound or flashlight surface before the shell dismisses the shade. */
    fun handleBack(): Boolean {
        if (detail == null) return false
        closeDetail()
        return true
    }

    private fun createGrid(content: LinearLayout) {
        val metrics = activity.resources.displayMetrics
        val diameter = min(metrics.widthPixels, metrics.heightPixels)
        val safeWidth = diameter * .72f / metrics.density
        val fontScale = activity.resources.configuration.fontScale.coerceAtLeast(1f)
        val columns = when {
            safeWidth >= 180f * fontScale -> 3
            safeWidth >= 120f * fontScale -> 2
            else -> 1
        }
        val specs: List<Triple<Glyph, String, () -> Unit>> = listOf(
            Triple(Glyph.WIFI, "Wi-Fi", { controls.toggleWifi(); afterControl() }),
            Triple(Glyph.BLUETOOTH, "Bluetooth", { controls.toggleBluetooth(); afterControl() }),
            Triple(Glyph.MOON, "DND", { controls.toggleDnd(); afterControl() }),
            Triple(Glyph.SOUND, "Sound", { showVolume() }),
            Triple(Glyph.TORCH, "Light", { showFlashlight() }),
            Triple(Glyph.WATCH, "AOD", {
                preferences.alwaysOnDisplayEnabled = !preferences.alwaysOnDisplayEnabled
                onDisplayChanged()
                refresh()
            }),
            Triple(Glyph.BATTERY, "Saver", { controls.openSaverSettings() }),
            Triple(Glyph.AIRPLANE, "Airplane", { controls.openAirplaneSettings() }),
            Triple(Glyph.GEAR, "Settings", { onSettings() })
        )
        specs.chunked(columns).forEach { group ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            group.forEach { (glyph, title, action) ->
                val tile = makeTile(glyph, title, action)
                tiles[glyph] = tile
                row.addView(tile.container, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            repeat(columns - group.size) { row.addView(View(activity), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
            content.addView(row, fullWidth().apply { bottomMargin = dp(4) })
        }
        tiles[Glyph.WIFI]?.container?.setOnLongClickListener { haptic(it, true); controls.openWifiSettings(); true }
        tiles[Glyph.BLUETOOTH]?.container?.setOnLongClickListener { haptic(it, true); controls.openBluetoothSettings(); true }
        tiles[Glyph.MOON]?.container?.setOnLongClickListener { haptic(it, true); controls.openSoundSettings(); true }
        tiles[Glyph.GEAR]?.container?.setOnLongClickListener { haptic(it, true); controls.openAndroidSettings(); true }
        tiles[Glyph.WATCH]?.container?.setOnLongClickListener { haptic(it, true); onSettings(); true }
    }

    private fun makeTile(glyph: Glyph, title: String, action: () -> Unit): Tile {
        val tile = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(48)
            minimumHeight = dp(79)
            setPadding(dp(3), dp(3), dp(3), dp(7))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x334DE0AE), rounded(Color.TRANSPARENT, 18), null)
            setOnClickListener { haptic(it); action() }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val icon = GlyphView(activity, glyph)
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        tile.addView(icon, LinearLayout.LayoutParams(dp(49), dp(49)))
        val label = text(title, 10f, Color.WHITE).apply {
            maxLines = 2
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, dp(5), 0, 0)
        }
        tile.addView(label, fullWidth())
        tile.contentDescription = title
        return Tile(title, tile, icon, label)
    }

    private fun createBrightness(content: LinearLayout) {
        brightnessLabel = text("Brightness", 11f, secondary)
        content.addView(brightnessLabel, fullWidth().apply { topMargin = dp(9) })
        brightness = SeekBar(activity).apply {
            max = 90
            minimumHeight = dp(48)
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            progress = ((LauncherBrightness.current(activity) ?: .6f) * 100f).roundToInt().coerceIn(10, 100) - 10
            contentDescription = "HealthSync foreground brightness"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val percent = progress + 10
                    LauncherBrightness.set(activity, percent)
                    LauncherBrightness.apply(activity.window, activity)
                    brightnessLabel.text = "Brightness · $percent%"
                    contentDescription = "HealthSync foreground brightness, $percent percent"
                    onDisplayChanged()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { adjustingBrightness = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { adjustingBrightness = false; haptic(this@apply); refresh() }
            })
        }
        content.addView(brightness, fullWidth())
        content.addView(text("System brightness", 11f, accent).apply {
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x334DE0AE), rounded(Color.TRANSPARENT, 20), null)
            contentDescription = "Restore Android system brightness for HealthSync"
            setOnClickListener {
                haptic(it)
                LauncherBrightness.set(activity, null)
                LauncherBrightness.apply(activity.window, activity)
                onDisplayChanged()
                refresh()
            }
        }, fullWidth())
    }

    private fun refresh() {
        clock.text = DateFormat.getTimeFormat(activity).format(Date())
        battery.text = controls.battery()
        connection.text = if (BluetoothClientService.isConnected) "Phone connected" else "Phone offline"
        update(Glyph.WIFI, "Wi-Fi", controls.wifiState(), if (Build.VERSION.SDK_INT >= 29) "Tap for settings" else "Tap to toggle. Hold for settings")
        update(Glyph.BLUETOOTH, "Bluetooth", controls.bluetoothState(), if (Build.VERSION.SDK_INT >= 33) "Tap for settings" else "Tap to toggle. Hold for settings")
        update(Glyph.MOON, "Do not disturb", controls.dndState(), "Tap to toggle; Android may ask for access")
        update(Glyph.SOUND, "Sound", WatchSystemControls.State(null, controls.mediaVolumePercent()), "Adjust media, alarm, and ring volume")
        update(Glyph.TORCH, "Flashlight", WatchSystemControls.State(false, "Off"), "Turn the display white")
        update(Glyph.WATCH, "Foreground always-on display",
            WatchSystemControls.State(preferences.alwaysOnDisplayEnabled, if (preferences.alwaysOnDisplayEnabled) "On" else "Off"),
            "Tap to toggle the foreground dim clock. Uses more battery")
        update(Glyph.BATTERY, "Battery saver", controls.saverState(), "Open Android battery controls")
        update(Glyph.AIRPLANE, "Airplane mode", controls.airplaneState(), "Open Android airplane mode controls")
        update(Glyph.GEAR, "Watch settings", WatchSystemControls.State(null, ""), "Tap for watch settings. Hold for Android settings")
        if (::brightness.isInitialized && !adjustingBrightness) {
            val value = LauncherBrightness.current(activity)
            brightness.progress = ((value ?: .6f) * 100f).roundToInt().coerceIn(10, 100) - 10
            brightnessLabel.text = if (value == null) "Brightness · system" else "Brightness · ${(value * 100f).roundToInt()}%"
        }
        volumeRows.forEach { row ->
            if (!row.tracking) row.slider.progress = controls.volume(row.stream)
            row.value.text = "${row.name} · ${volumeLabel(row.stream)}"
        }
    }

    private fun update(glyph: Glyph, title: String, state: WatchSystemControls.State, instruction: String) {
        val tile = tiles[glyph] ?: return
        tile.icon.active = state.enabled == true
        tile.icon.alpha = if (state.changing) .6f else 1f
        tile.label.text = if (state.detail.isBlank()) tile.title else "${tile.title}\n${state.detail}"
        tile.container.contentDescription = "$title${if (state.detail.isBlank()) "" else ", ${state.detail}"}. $instruction"
        if (Build.VERSION.SDK_INT >= 30) tile.container.stateDescription = state.detail
    }

    private fun afterControl() {
        refresh()
        handler.removeCallbacks(delayedRefresh)
        handler.postDelayed(delayedRefresh, 700L)
        handler.postDelayed(delayedRefresh, 2_500L)
    }

    private fun showVolume() {
        closeDetail()
        val content = column()
        content.addView(text("Sound", 23f, Color.WHITE), fullWidth().apply { bottomMargin = dp(12) })
        listOf(AudioManager.STREAM_MUSIC to "Media", AudioManager.STREAM_ALARM to "Alarm", AudioManager.STREAM_RING to "Ring").forEach { (stream, name) ->
            val label = text("$name · ${volumeLabel(stream)}", 12f, secondary)
            content.addView(label, fullWidth().apply { topMargin = dp(8) })
            val slider = SeekBar(activity).apply {
                max = controls.maxVolume(stream).coerceAtLeast(1)
                progress = controls.volume(stream)
                isEnabled = controls.maxVolume(stream) > 0
                minimumHeight = dp(48)
                progressTintList = ColorStateList.valueOf(accent)
                thumbTintList = ColorStateList.valueOf(accent)
                contentDescription = "$name volume"
            }
            val row = VolumeRow(name, stream, slider, label)
            volumeRows.add(row)
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    if (!controls.setVolume(stream, progress)) slider.progress = controls.volume(stream)
                    label.text = "$name · ${volumeLabel(stream)}"
                    slider.contentDescription = "$name volume, ${volumeLabel(stream)}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { row.tracking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { row.tracking = false; haptic(slider) }
            })
            content.addView(slider, fullWidth())
        }
        content.addView(pill("Android sound", Glyph.GEAR) { controls.openSoundSettings() }, fullWidth().apply { topMargin = dp(10) })
        content.addView(pill("Back", Glyph.DOWN) { closeDetail() }, fullWidth().apply { topMargin = dp(6) })
        val scroll = RoundScrollView(activity).apply { setBackgroundColor(Color.BLACK); isFillViewport = true }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        detail = scroll
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun volumeLabel(stream: Int): String {
        val maximum = controls.maxVolume(stream)
        return if (maximum == 0) "Unavailable" else "${(controls.volume(stream) * 100f / maximum).roundToInt()}%"
    }

    private fun showFlashlight() {
        closeDetail()
        torchBrightness = activity.window.attributes.screenBrightness
        torchHadKeepScreenOn = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = 1f }
        val light = FrameLayout(activity).apply {
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            contentDescription = "Flashlight on. Tap to turn off"
            setOnClickListener { haptic(it); closeDetail() }
        }
        val hint = text("Tap to turn off", 14f, Color.BLACK).apply { isClickable = false }
        light.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM).apply {
            bottomMargin = (activity.resources.displayMetrics.heightPixels * .15f).roundToInt()
        })
        detail = light
        root.addView(light, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        light.announceForAccessibility("Flashlight on. Tap to turn off")
    }

    private fun closeDetail() {
        detail?.let { root.removeView(it) }
        detail = null
        volumeRows.clear()
        torchBrightness?.let { previous ->
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = previous }
            if (!torchHadKeepScreenOn) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        torchBrightness = null
    }

    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val metrics = activity.resources.displayMetrics
        val safeSide = (min(metrics.widthPixels, metrics.heightPixels) * .14f).roundToInt()
        val safeEnd = (min(metrics.widthPixels, metrics.heightPixels) * .14f).roundToInt().coerceAtLeast(dp(30))
        setPadding(safeSide, safeEnd, safeSide, safeEnd)
    }

    private fun text(value: String, size: Float, color: Int) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun pill(title: String, glyph: Glyph, action: () -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            contentDescription = title
            background = RippleDrawable(ColorStateList.valueOf(0x334DE0AE), rounded(surface, 30), null)
            setOnClickListener { haptic(it); action() }
        }
        row.addView(GlyphView(activity, glyph).apply {
            showCircle = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        row.addView(text(title, 12f, Color.WHITE).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        return row
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun haptic(view: View, long: Boolean = false) { view.performHapticFeedback(if (long) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY) }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()

    private enum class Glyph { WIFI, BLUETOOTH, MOON, SOUND, TORCH, WATCH, BATTERY, AIRPLANE, GEAR, TOOLS, DOWN }

    /** Code-native icons stay sharp on watches with unusual resolutions and display densities. */
    private inner class GlyphView(context: Context, private val glyph: Glyph) : View(context) {
        var active = false
            set(value) { if (field != value) { field = value; invalidate() } }
        var showCircle = true
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        private val path = Path()
        private val bounds = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = min(width, height).toFloat()
            val cx = width / 2f
            val cy = height / 2f
            if (showCircle) {
                paint.style = Paint.Style.FILL
                paint.color = if (active) accent else surface
                canvas.drawCircle(cx, cy, size / 2f - 1f, paint)
            }
            canvas.save()
            val scale = if (showCircle) size / 43f else size / 25f
            canvas.translate(cx - 12f * scale, cy - 12f * scale)
            canvas.scale(scale, scale)
            paint.style = Paint.Style.STROKE
            paint.color = if (active && showCircle) Color.BLACK else Color.WHITE
            paint.strokeWidth = 1.8f
            path.reset()
            when (glyph) {
                Glyph.WIFI -> {
                    bounds.set(1f, 4f, 23f, 26f); canvas.drawArc(bounds, 222f, 96f, false, paint)
                    bounds.set(5f, 8f, 19f, 22f); canvas.drawArc(bounds, 222f, 96f, false, paint)
                    bounds.set(9f, 12f, 15f, 18f); canvas.drawArc(bounds, 222f, 96f, false, paint)
                    paint.style = Paint.Style.FILL; canvas.drawCircle(12f, 19f, 1.5f, paint)
                }
                Glyph.BLUETOOTH -> {
                    path.moveTo(7f, 7f); path.lineTo(17f, 17f); path.lineTo(12f, 22f); path.lineTo(12f, 2f)
                    path.lineTo(17f, 7f); path.lineTo(7f, 17f); canvas.drawPath(path, paint)
                }
                Glyph.MOON -> {
                    path.moveTo(16f, 3f); path.cubicTo(7f, 0f, 1f, 8f, 5f, 16f)
                    path.cubicTo(9f, 24f, 20f, 23f, 22f, 14f)
                    path.cubicTo(13f, 18f, 9f, 9f, 16f, 3f); canvas.drawPath(path, paint)
                }
                Glyph.SOUND -> {
                    path.moveTo(3f, 9f); path.lineTo(7f, 9f); path.lineTo(12f, 5f); path.lineTo(12f, 19f)
                    path.lineTo(7f, 15f); path.lineTo(3f, 15f); path.close(); canvas.drawPath(path, paint)
                    bounds.set(9f, 4f, 22f, 20f); canvas.drawArc(bounds, -55f, 110f, false, paint)
                    bounds.set(12f, 8f, 18f, 16f); canvas.drawArc(bounds, -55f, 110f, false, paint)
                }
                Glyph.TORCH -> {
                    path.moveTo(5f, 3f); path.lineTo(19f, 3f); path.lineTo(19f, 7f); path.lineTo(15f, 12f)
                    path.lineTo(15f, 22f); path.lineTo(9f, 22f); path.lineTo(9f, 12f); path.lineTo(5f, 7f); path.close()
                    canvas.drawPath(path, paint); canvas.drawLine(5f, 7f, 19f, 7f, paint); canvas.drawLine(9f, 12f, 15f, 12f, paint)
                }
                Glyph.WATCH -> {
                    canvas.drawRoundRect(5f, 5f, 19f, 19f, 4f, 4f, paint)
                    canvas.drawLine(9f, 1f, 15f, 1f, paint); canvas.drawLine(9f, 23f, 15f, 23f, paint)
                    canvas.drawLine(12f, 8f, 12f, 12f, paint); canvas.drawLine(12f, 12f, 15f, 14f, paint)
                }
                Glyph.BATTERY -> {
                    canvas.drawRoundRect(5f, 4f, 19f, 22f, 2f, 2f, paint); canvas.drawLine(10f, 1f, 14f, 1f, paint)
                    path.moveTo(13f, 8f); path.lineTo(9f, 13f); path.lineTo(14f, 13f); path.lineTo(11f, 18f); canvas.drawPath(path, paint)
                }
                Glyph.AIRPLANE -> {
                    path.moveTo(12f, 2f); path.lineTo(14f, 10f); path.lineTo(22f, 14f); path.lineTo(22f, 16f)
                    path.lineTo(14f, 14f); path.lineTo(14f, 19f); path.lineTo(17f, 21f); path.lineTo(17f, 22f)
                    path.lineTo(12f, 21f); path.lineTo(7f, 22f); path.lineTo(7f, 21f); path.lineTo(10f, 19f)
                    path.lineTo(10f, 14f); path.lineTo(2f, 16f); path.lineTo(2f, 14f); path.lineTo(10f, 10f); path.close()
                    canvas.drawPath(path, paint)
                }
                Glyph.GEAR -> {
                    canvas.drawCircle(12f, 12f, 7.5f, paint); canvas.drawCircle(12f, 12f, 3f, paint)
                    repeat(8) { index ->
                        val radians = index * Math.PI / 4
                        canvas.drawLine(12f + cos(radians).toFloat() * 8f, 12f + sin(radians).toFloat() * 8f,
                            12f + cos(radians).toFloat() * 10f, 12f + sin(radians).toFloat() * 10f, paint)
                    }
                }
                Glyph.TOOLS -> {
                    canvas.drawCircle(12f, 13f, 8f, paint); canvas.drawLine(10f, 2f, 14f, 2f, paint)
                    canvas.drawLine(12f, 2f, 12f, 5f, paint); canvas.drawLine(12f, 8f, 12f, 13f, paint)
                    canvas.drawLine(12f, 13f, 16f, 15f, paint)
                }
                Glyph.DOWN -> { path.moveTo(5f, 9f); path.lineTo(12f, 16f); path.lineTo(19f, 9f); canvas.drawPath(path, paint) }
            }
            canvas.restore()
        }
    }
}

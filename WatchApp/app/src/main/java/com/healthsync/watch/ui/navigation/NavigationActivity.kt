package com.healthsync.watch.ui.navigation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.WatchFaceActivity
import com.healthsync.watch.ui.WatchVisualStyle
import kotlin.math.min
import kotlin.math.roundToInt

/** Wrist-sized guidance from the phone's actual navigation notification, without screen capture. */
class NavigationActivity : AppCompatActivity() {
    companion object {
        fun intentFor(context: Context) = Intent(context, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private lateinit var scroll: RoundScrollView
    private lateinit var source: TextView
    private lateinit var guidanceRow: LinearLayout
    private lateinit var symbol: FrameLayout
    private lateinit var sourceIcon: ImageView
    private lateinit var glyph: TextView
    private lateinit var distance: TextView
    private lateinit var instruction: TextView
    private lateinit var road: TextView
    private lateinit var summary: TextView
    private var visible = false
    private var displayedInstruction = ""
    private val handler = Handler(Looper.getMainLooper())
    private val observer: () -> Unit = { render() }
    private val tick = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 500L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Route information should not be retained in recents or screenshots.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        fullscreen()
        val metrics = resources.displayMetrics
        val side = min(metrics.widthPixels, metrics.heightPixels)
        val scale = (side / (metrics.density * 320f)).coerceIn(.65f, 1.3f)
        val layout = navigationLayout(metrics.widthPixels, metrics.heightPixels, dp(44))
        val root = FrameLayout(this).apply { background = WatchVisualStyle.screenBackground() }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = WatchVisualStyle.surface(this@NavigationActivity)
        }
        toolbar.addView(button("‹ Back") { finish() }, LinearLayout.LayoutParams(0, -1, 1f))
        toolbar.addView(button("Clock") {
            startActivity(Intent(this, WatchFaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }, LinearLayout.LayoutParams(0, -1, 1f))
        root.place(toolbar, layout.toolbar)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(3), dp(3), dp(3), dp(8))
        }
        source = label("Google Maps", 12f * scale, WatchVisualStyle.ACCENT)
        content.addView(source, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        symbol = FrameLayout(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
        glyph = label("?", 62f * scale, WatchVisualStyle.ACCENT).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        sourceIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Keep the source's colours and aspect ratio; no inferred arrow replaces its icon.
            adjustViewBounds = true
        }
        symbol.addView(glyph, FrameLayout.LayoutParams(-1, -1))
        symbol.addView(sourceIcon, FrameLayout.LayoutParams(-1, -1).apply { gravity = Gravity.CENTER })
        val symbolSize = (side * .18f).roundToInt().coerceAtLeast(1)
        glyph.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, symbolSize * .82f)
        guidanceRow = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        guidanceRow.addView(symbol, LinearLayout.LayoutParams(symbolSize, symbolSize))
        distance = label("", 34f * scale, Color.WHITE).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) }
        guidanceRow.addView(distance, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(guidanceRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        instruction = label("", 19f * scale, WatchVisualStyle.TEXT).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        content.addView(instruction, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        road = label("", 16f * scale, WatchVisualStyle.TEXT)
        content.addView(road, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        summary = label("", 13f * scale, WatchVisualStyle.MUTED)
        content.addView(summary, LinearLayout.LayoutParams(-1, -2))
        scroll = RoundScrollView(this).apply {
            isFillViewport = true
            contentDescription = "Navigation instructions. Swipe or rotate the crown to read all details."
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        root.place(scroll, layout.content)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        fullscreen()
        NavigationBridge.observe(observer)
        NavigationBridge.requestCurrentState()
        handler.post(tick)
    }

    override fun onPause() {
        visible = false
        handler.removeCallbacks(tick)
        NavigationBridge.removeObserver(observer)
        sourceIcon.setImageDrawable(null)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullscreen()
    }

    private fun render() {
        if (!visible) return
        val current = NavigationBridge.state()
        val active = current.isFresh()
        val p = current.payload?.takeIf { active }
        val hasSymbol = active && (current.icon != null || (p != null && p.maneuver != "UNKNOWN"))
        symbol.visibility = if (hasSymbol) View.VISIBLE else View.GONE
        guidanceRow.visibility = if (hasSymbol || p?.distance?.isNotBlank() == true) View.VISIBLE else View.GONE
        sourceIcon.setImageBitmap(if (active) current.icon else null)
        sourceIcon.visibility = if (active && current.icon != null) View.VISIBLE else View.GONE
        glyph.visibility = if (active && current.icon == null) View.VISIBLE else View.GONE
        if (p != null) {
            source.setIfChanged("Google Maps · Live")
            glyph.setIfChanged(navigationManeuverGlyph(p.maneuver))
            symbol.contentDescription = if (current.icon != null) "Navigation symbol from Google Maps"
                else navigationManeuverDescription(p.maneuver)
            distance.setIfChanged(p.distance)
            distance.visibility = if (p.distance.isBlank()) View.GONE else View.VISIBLE
            instruction.setIfChanged(p.instruction)
            road.setIfChanged(p.road)
            road.visibility = if (p.road.isBlank() || p.road == p.instruction) View.GONE else View.VISIBLE
            summary.setIfChanged(p.summary)
            summary.visibility = if (p.summary.isBlank()) View.GONE else View.VISIBLE
        } else {
            source.setIfChanged("Google Maps navigation")
            distance.setIfChanged("")
            distance.visibility = View.GONE
            glyph.setIfChanged("?")
            summary.setIfChanged("")
            summary.visibility = View.GONE
            road.visibility = View.VISIBLE
            val (title, explanation) = when (current.status) {
                "DISCONNECTED" -> "Phone disconnected" to "Reconnect your phone to resume navigation."
                "WAITING" -> "Waiting for phone" to "On your phone, enable Watch navigation in HealthSync and start a Google Maps route."
                "IDLE" -> "No active route" to "Start turn-by-turn navigation in Google Maps on your phone."
                "DISABLED" -> "Navigation is off" to "On your phone: HealthSync → Device → Watch navigation."
                "UNAVAILABLE" -> "Navigation unavailable" to "On your phone, allow HealthSync notification access and check Google Maps navigation notifications."
                else -> "Updates paused" to "Guidance is hidden because the phone stopped updating. Check Bluetooth and Google Maps on your phone."
            }
            instruction.setIfChanged(title)
            road.setIfChanged(current.payload?.message?.takeIf { it.isNotBlank() } ?: explanation)
        }
        // Return to the new instruction when a maneuver changes, while heartbeats allow scrolling.
        val key = listOf(current.status, p?.sessionId.orEmpty(), p?.instruction.orEmpty(), p?.road.orEmpty()).joinToString("\n")
        if (key != displayedInstruction) {
            displayedInstruction = key
            scroll.scrollTo(0, 0)
        }
        if (active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun TextView.setIfChanged(value: String) { if (text.toString() != value) text = value }

    private fun FrameLayout.place(view: View, bounds: NavigationBounds) = addView(view,
        FrameLayout.LayoutParams(bounds.width, bounds.height).apply { leftMargin = bounds.left; topMargin = bounds.top })

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
        // Text remains complete at large accessibility font sizes; the viewport scrolls.
        isSingleLine = false
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 12f
        isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(2), 0, dp(2), 0)
        setTextColor(WatchVisualStyle.TEXT)
        backgroundTintList = null
        background = WatchVisualStyle.button(this@NavigationActivity)
        maxLines = 1
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 10, 12, 1,
            android.util.TypedValue.COMPLEX_UNIT_SP)
        contentDescription = if (value == "Clock") "Return to watch clock" else "Back"
        setOnClickListener { action() }
    }

    @Suppress("DEPRECATION")
    private fun fullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

/** Only explicit source maneuvers get arrows. Unknown guidance always remains neutral. */
internal fun navigationManeuverGlyph(maneuver: String): String = when (maneuver) {
    "LEFT" -> "←"
    "RIGHT" -> "→"
    "SLIGHT_LEFT", "FORK_LEFT" -> "↖"
    "SLIGHT_RIGHT", "FORK_RIGHT" -> "↗"
    "SHARP_LEFT" -> "↙"
    "SHARP_RIGHT" -> "↘"
    "STRAIGHT" -> "↑"
    "UTURN_LEFT" -> "↶"
    "UTURN_RIGHT" -> "↷"
    "ROUNDABOUT" -> "○"
    "ARRIVE" -> "⚑"
    "MERGE" -> "⇧"
    else -> "?"
}

internal fun navigationManeuverDescription(maneuver: String): String = when (maneuver) {
    "LEFT" -> "Turn left"
    "RIGHT" -> "Turn right"
    "SLIGHT_LEFT" -> "Slight left"
    "SLIGHT_RIGHT" -> "Slight right"
    "SHARP_LEFT" -> "Sharp left"
    "SHARP_RIGHT" -> "Sharp right"
    "STRAIGHT" -> "Continue straight"
    "UTURN_LEFT" -> "Make a left U-turn"
    "UTURN_RIGHT" -> "Make a right U-turn"
    "ROUNDABOUT" -> "Roundabout. Read the source instruction for the exit."
    "ARRIVE" -> "Arrival"
    "MERGE" -> "Merge. Read the source instruction."
    "FORK_LEFT" -> "Keep left at the fork"
    "FORK_RIGHT" -> "Keep right at the fork"
    else -> "Direction symbol unavailable. Read the Google Maps instruction."
}

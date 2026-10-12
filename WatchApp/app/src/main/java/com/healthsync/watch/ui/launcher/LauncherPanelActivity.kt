package com.healthsync.watch.ui.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.WatchFaceActivity
import com.healthsync.watch.ui.WatchVisualStyle
import kotlin.math.roundToInt

/** Brightness for HealthSync's foreground windows; the Android setting is untouched. */
object LauncherBrightness {
    private const val PREFERENCES = "healthsync_launcher_display"
    private const val KEY_BRIGHTNESS = "interactive_brightness_percent"

    fun current(context: Context): Float? {
        val percent = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(KEY_BRIGHTNESS, -1)
        return if (percent in 10..100) percent / 100f else null
    }

    /** null restores the system brightness. A minimum of 10% keeps controls reachable. */
    fun set(context: Context, percent: Int?) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().apply {
            if (percent == null) remove(KEY_BRIGHTNESS)
            else putInt(KEY_BRIGHTNESS, percent.coerceIn(10, 100))
        }.apply()
    }

    fun apply(window: Window, context: Context) {
        window.attributes = window.attributes.apply {
            screenBrightness = current(context) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
}

/** Shared round-screen layout and guarded navigation for foreground launcher panels. */
abstract class LauncherPanelActivity : AppCompatActivity() {
    protected val accent = WatchVisualStyle.ACCENT
    protected val muted = WatchVisualStyle.MUTED
    protected val panelColor = WatchVisualStyle.SURFACE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFullscreen()
    }

    override fun onResume() {
        super.onResume()
        setFullscreen()
        LauncherBrightness.apply(window, this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setFullscreen()
    }

    @Suppress("DEPRECATION")
    private fun setFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    protected fun roundContent(): LinearLayout {
        val scroll = RoundScrollView(this).apply {
            background = WatchVisualStyle.screenBackground()
            isFillViewport = true
            clipToPadding = false
        }
        val side = (resources.displayMetrics.widthPixels * 0.14f).roundToInt()
        val end = (resources.displayMetrics.heightPixels * 0.16f).roundToInt().coerceAtLeast(dp(36))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(side, end, side, end)
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
        return content
    }

    protected fun heading(content: LinearLayout, title: String, subtitle: String) {
        label(content, title, 23f, WatchVisualStyle.TEXT, bold = true)
        label(content, subtitle, 12f)
    }

    /** Visible Back returns to the originating panel; Home always resets to the clock. */
    protected fun navigationHeader(content: LinearLayout, title: String) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        fun navigationButton(value: String, description: String, click: () -> Unit) = Button(this).apply {
            text = value; contentDescription = description; isAllCaps = false
            textSize = 12f; setTextColor(muted); minWidth = 0; minimumWidth = 0
            minHeight = dp(44); minimumHeight = dp(44); setPadding(dp(4), 0, dp(4), 0)
            backgroundTintList = null; background = WatchVisualStyle.button(this@LauncherPanelActivity)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); click() }
        }
        row.addView(navigationButton("‹ Back", "Back to previous screen") { onBackPressedDispatcher.onBackPressed() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        row.addView(navigationButton("Clock", "Return to clock") { backToClock() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        label(content, title, 23f, WatchVisualStyle.TEXT, bold = true)
    }

    protected fun card(content: LinearLayout): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(12), dp(12), dp(12), dp(12)); background = WatchVisualStyle.surface(this@LauncherPanelActivity)
        content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })
    }

    protected fun primaryAction(content: LinearLayout, text: String, click: () -> Unit): Button = action(content, text, click).apply {
        setTextColor(WatchVisualStyle.primaryTextColor()); background = WatchVisualStyle.button(this@LauncherPanelActivity, primary = true)
    }

    protected fun openAppPermissions() = launchFirst(
        listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))),
        "App permissions could not open. Open Android Settings, Apps, HealthSync Watch, Permissions."
    )

    protected fun openHealthReadings() = launchFirst(
        listOf(Intent(this, com.healthsync.watch.ui.WatchOptionsActivity::class.java)
            .putExtra(com.healthsync.watch.ui.WatchOptionsActivity.EXTRA_SCREEN,
                com.healthsync.watch.ui.WatchOptionsActivity.SCREEN_HEALTH)),
        "Health readings could not open."
    )

    protected fun label(
        content: LinearLayout,
        text: String,
        size: Float = 12f,
        color: Int = muted,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(dp(2), dp(4), dp(2), dp(4))
        content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4)
        })
    }

    protected fun action(content: LinearLayout, text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(WatchVisualStyle.TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = 0; minimumWidth = 0
        setPadding(dp(10), dp(10), dp(10), dp(10))
        backgroundTintList = null
        background = buttonBackground()
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
        content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })
    }

    protected fun shape(color: Int, radius: Int = 22): GradientDrawable = WatchVisualStyle.surface(this, color, radius)

    protected fun buttonBackground(): StateListDrawable = WatchVisualStyle.button(this)

    /** Attempt actual launches: vendor firmware may hide a resolver despite an available screen. */
    protected fun launchFirst(intents: List<Intent>, failureMessage: String): Boolean {
        for (intent in intents) {
            try {
                startActivity(intent)
                return true
            } catch (_: RuntimeException) {
                // Removed apps, restricted screens and vendor-only settings remain recoverable.
            }
        }
        message(failureMessage)
        return false
    }

    protected fun openSystemSettings() = launchFirst(
        listOf(Intent(Settings.ACTION_SETTINGS)),
        "Android Settings is unavailable on this watch."
    )

    protected fun backToClock() {
        startActivity(Intent(this, WatchFaceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    protected fun message(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}

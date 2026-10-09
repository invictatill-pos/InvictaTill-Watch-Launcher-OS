package com.healthsync.watch.ui.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
    protected val accent = 0xFF8CE7C8.toInt()
    protected val muted = 0xFFA8B9B1.toInt()
    protected val panelColor = 0xFF14221C.toInt()

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
            setBackgroundColor(Color.BLACK)
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
        label(content, title, 23f, Color.WHITE, bold = true)
        label(content, subtitle, 12f)
    }

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
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(4), dp(6), dp(4), dp(6))
        content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(5)
        })
    }

    protected fun action(content: LinearLayout, text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        minHeight = dp(50)
        minimumHeight = dp(50)
        setPadding(dp(10), dp(12), dp(10), dp(12))
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

    protected fun shape(color: Int, radius: Int = 20): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    protected fun buttonBackground(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(0xFF294839.toInt()))
        addState(intArrayOf(), shape(panelColor))
    }

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

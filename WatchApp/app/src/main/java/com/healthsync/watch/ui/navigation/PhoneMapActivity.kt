package com.healthsync.watch.ui.navigation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.WatchFaceActivity
import com.healthsync.watch.ui.WatchVisualStyle
import kotlin.math.max
import kotlin.math.roundToInt

/** The actual phone frame fills the watch; local controls rest along safe circular chords. */
class PhoneMapActivity : AppCompatActivity() {
    companion object {
        fun intentFor(context: Context) = Intent(context, PhoneMapActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private lateinit var root: FrameLayout
    private lateinit var image: PhoneFrameView
    private lateinit var status: TextView
    private lateinit var waiting: FrameLayout
    private lateinit var waitingTitle: TextView
    private lateinit var waitingBody: TextView
    private lateinit var stop: Button
    private lateinit var toolbar: LinearLayout
    private lateinit var actions: LinearLayout
    private lateinit var geometry: PhoneMapLayout
    private var help: View? = null
    private var visible = false
    private var controlsVisible = true
    private var wasFresh = false
    private val handler = Handler(Looper.getMainLooper())
    private val observer: () -> Unit = { render() }
    private val hideControls = Runnable {
        if (visible && help == null && PhoneScreenBridge.state().isFresh()) showControls(false)
    }
    private val tick = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 500L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recents and screenshots must not persist received private phone pixels.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        fullscreen()
        val metrics = resources.displayMetrics
        geometry = phoneMapLayout(metrics.widthPixels, metrics.heightPixels, dp(44))
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        image = PhoneFrameView(this).apply {
            setOnClickListener { showControls(!controlsVisible) }
        }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))

        waiting = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val message = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        waitingTitle = text("Phone map", 18f, WatchVisualStyle.ACCENT)
        message.addView(waitingTitle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        waitingBody = text("", 12f, WatchVisualStyle.MUTED).apply { setLineSpacing(dp(2).toFloat(), 1f) }
        message.addView(waitingBody, LinearLayout.LayoutParams(-1, -2))
        val messageScroll = RoundScrollView(this).apply {
            isFillViewport = true
            addView(message, FrameLayout.LayoutParams(-1, -2))
        }
        waiting.place(messageScroll, geometry.message)
        root.addView(waiting, FrameLayout.LayoutParams(-1, -1))

        toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = overlaySurface()
        }
        toolbar.addView(button("‹ Back") {
            if (help != null) closeHelp() else finish()
        }, LinearLayout.LayoutParams(0, -1, 1f))
        toolbar.addView(button("Clock") {
            startActivity(Intent(this, WatchFaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }, LinearLayout.LayoutParams(0, -1, 1f))
        root.place(toolbar, geometry.toolbar)

        status = text("", 11f, WatchVisualStyle.ACCENT).apply {
            maxLines = 1
            background = overlaySurface()
            setPadding(dp(5), 0, dp(5), 0)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 8, 11, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP)
            contentDescription = "Phone map freshness. Tap to show map controls."
            setOnClickListener { showControls(true) }
        }
        root.place(status, geometry.status)

        actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = overlaySurface()
        }
        actions.addView(button("Fit") { image.resetZoom(); showControls(true) },
            LinearLayout.LayoutParams(0, -1, 1f))
        actions.addView(button("Help") { showHelp() }, LinearLayout.LayoutParams(0, -1, 1f))
        stop = button("Stop") {
            PhoneScreenBridge.stop(this)
            showControls(true)
            render()
        }.apply { setTextColor(0xFFF0B6AB.toInt()) }
        actions.addView(stop, LinearLayout.LayoutParams(0, -1, 1f))
        root.place(actions, geometry.actions)

        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (help != null) closeHelp() else finish() }
        })
        if (savedInstanceState?.getBoolean("map_help") == true) showHelp()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        fullscreen()
        PhoneScreenBridge.observe(observer)
        PhoneScreenBridge.requestCurrentSession()
        handler.post(tick)
        showControls(true)
    }

    override fun onPause() {
        visible = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(hideControls)
        PhoneScreenBridge.removeObserver(observer)
        image.setFrame(null)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("map_help", help != null)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullscreen()
    }

    private fun render() {
        if (!visible) return
        val state = PhoneScreenBridge.state()
        val now = SystemClock.elapsedRealtime()
        val fresh = state.isFresh(now)
        val age = state.receivedAt?.let { ((now - it).coerceAtLeast(0) / 1_000) }
        status.text = when {
            fresh -> "Live · " + (age ?: 0) + "s ago"
            state.sessionId != null && state.receivedAt != null -> "Paused · " + (age ?: 0) + "s ago"
            state.sessionId != null -> "Waiting for phone"
            else -> if (state.connected) "Phone connected" else "Disconnected"
        }
        status.setTextColor(if (fresh) WatchVisualStyle.ACCENT else WatchVisualStyle.MUTED)
        status.contentDescription = status.text.toString() + ". Tap to show map controls."
        status.visibility = if (fresh) View.VISIBLE else View.GONE
        // Old positions disappear entirely instead of continuing to resemble live navigation.
        image.setFrame(if (fresh) state.bitmap else null)
        waiting.visibility = if (fresh) View.GONE else View.VISIBLE
        waitingTitle.text = when {
            state.sessionId != null && state.receivedAt != null -> "View paused"
            state.sessionId != null -> "Connecting…"
            else -> "Phone map"
        }
        waitingBody.text = when {
            state.sessionId != null && state.receivedAt != null ->
                "Last update " + (age ?: 0) + "s ago.\nCheck your phone and Bluetooth."
            state.sessionId != null ->
                "Waiting for the phone screen.\nKeep your phone unlocked and open Google Maps."
            else -> state.message
        }
        stop.isEnabled = state.sessionId != null
        stop.alpha = if (stop.isEnabled) 1f else .35f
        if (!fresh || (fresh && !wasFresh)) showControls(true)
        wasFresh = fresh
        if (fresh) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Single taps toggle chrome; a pan or pinch never hides controls accidentally. */
    private fun showControls(show: Boolean) {
        controlsVisible = show
        toolbar.visibility = if (show) View.VISIBLE else View.GONE
        actions.visibility = if (show) View.VISIBLE else View.GONE
        status.layoutParams = overlayParams(if (show) geometry.status else geometry.quietStatus)
        handler.removeCallbacks(hideControls)
        if (show && visible && help == null && PhoneScreenBridge.state().isFresh())
            handler.postDelayed(hideControls, 4_000L)
    }

    private fun showHelp() {
        if (help != null) return
        showControls(true)
        handler.removeCallbacks(hideControls)
        val sheet = FrameLayout(this).apply {
            setBackgroundColor(0xFA030806.toInt())
            isClickable = true
            isFocusable = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        content.addView(text("Live phone map", 18f, WatchVisualStyle.ACCENT),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        content.addView(text("Pinch to zoom · Drag to move\nDouble tap to zoom or fit\nTap once to show controls", 12f),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        content.addView(text("Fit shows the complete phone screen.\nView only: use Google Maps on your phone.\nUpdates arrive over Bluetooth.", 11f, WatchVisualStyle.MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        content.addView(button("Got it") { closeHelp() }.apply { background = overlaySurface() },
            LinearLayout.LayoutParams(-1, dp(44)))
        val scroll = RoundScrollView(this).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        sheet.place(scroll, geometry.help)
        help = sheet
        root.addView(sheet, FrameLayout.LayoutParams(-1, -1))
    }

    private fun closeHelp() {
        help?.let(root::removeView)
        help = null
        showControls(true)
    }

    private fun FrameLayout.place(view: View, bounds: MapOverlayBounds) =
        addView(view, overlayParams(bounds))

    private fun overlayParams(bounds: MapOverlayBounds) =
        FrameLayout.LayoutParams(bounds.width, bounds.height).apply {
            leftMargin = bounds.left; topMargin = bounds.top
        }

    private fun overlaySurface() = WatchVisualStyle.surface(this, 0xDC0B1510.toInt(), 24)

    @Suppress("DEPRECATION")
    private fun fullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 12f; isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(2), 0, dp(2), 0)
        setTextColor(WatchVisualStyle.TEXT); backgroundTintList = null
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), WatchVisualStyle.surface(this@PhoneMapActivity, 0xFF294839.toInt(), 24))
            addState(intArrayOf(android.R.attr.state_focused), WatchVisualStyle.surface(this@PhoneMapActivity, 0xFF213D30.toInt(), 24))
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        maxLines = 1
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 10, 12, 1,
            android.util.TypedValue.COMPLEX_UNIT_SP)
        contentDescription = when (value) {
            "Fit" -> "Fit the complete phone screen inside the round watch"
            "Stop" -> "Stop sharing the phone screen"
            "Help" -> "Phone map gestures and help"
            else -> value
        }
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

/** All gestures affect this local view only; no touch event is sent to the phone. */
internal class PhoneFrameView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null
    private var bitmapWidth = 0
    private var bitmapHeight = 0
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val target = RectF()
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            changeZoom((zoom * detector.scaleFactor).coerceIn(1f, 8f), detector.focusX, detector.focusY)
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { performClick(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (zoom > 1.05f) resetZoom() else changeZoom(3f, e.x, e.y)
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaler.isInProgress) {
                offsetX -= distanceX; offsetY -= distanceY; clamp(); invalidate()
            }
            return true
        }
    })

    init { contentDescription = "Live phone screen. Pinch to zoom, drag to move, double tap to zoom or fit." }

    fun setFrame(value: Bitmap?) {
        if (bitmap === value) return
        if (value != null && (bitmapWidth != value.width || bitmapHeight != value.height)) {
            bitmapWidth = value.width; bitmapHeight = value.height
            zoom = 1f; offsetX = 0f; offsetY = 0f
        }
        bitmap = value
        clamp()
        invalidate()
    }

    fun resetZoom() { zoom = 1f; offsetX = 0f; offsetY = 0f; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = bitmap ?: return
        val fit = circlePhoneFrameScale(width, height, current.width, current.height)
        val drawWidth = current.width * fit * zoom
        val drawHeight = current.height * fit * zoom
        val left = (width - drawWidth) / 2f + offsetX
        val top = (height - drawHeight) / 2f + offsetY
        target.set(left, top, left + drawWidth, top + drawHeight)
        val saved = canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawBitmap(current, null, target, paint)
        canvas.restoreToCount(saved)
    }

    // GestureDetector.onSingleTapConfirmed calls performClick(); pans, pinches and double taps do not.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun changeZoom(value: Float, x: Float, y: Float) {
        if (!value.isFinite() || zoom <= 0) return
        val ratio = value / zoom
        offsetX = (offsetX - (x - width / 2f)) * ratio + (x - width / 2f)
        offsetY = (offsetY - (y - height / 2f)) * ratio + (y - height / 2f)
        zoom = value
        clamp(); invalidate()
    }

    private fun clamp() {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || width <= 0 || height <= 0) return
        val fit = circlePhoneFrameScale(width, height, bitmapWidth, bitmapHeight)
        val limitX = max(0f, (bitmapWidth * fit * zoom - width) / 2f)
        val limitY = max(0f, (bitmapHeight * fit * zoom - height) / 2f)
        offsetX = offsetX.coerceIn(-limitX, limitX)
        offsetY = offsetY.coerceIn(-limitY, limitY)
    }
}

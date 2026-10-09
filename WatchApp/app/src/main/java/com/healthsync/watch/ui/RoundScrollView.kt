package com.healthsync.watch.ui

import android.content.Context
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.os.Build

/** Scrollable watch content also responds to a rotating crown or bezel. */
class RoundScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.scrollViewStyle
) : ScrollView(context, attrs, defStyleAttr) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        overScrollMode = OVER_SCROLL_NEVER
        // Keep the constructor/XML scrollbar state. Android 8's enable setter only changes
        // flags; it does not create the drawable needed by non-fading scrollbars.
        isScrollbarFadingEnabled = false
        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        scrollBarSize = (2f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 48f * resources.displayMetrics.density
            scrollBy(0, delta.toInt())
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }
}

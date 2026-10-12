package com.healthsync.watch.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import kotlin.math.roundToInt

/** Shared visual vocabulary for interactive watch screens. Clock faces retain their own palettes. */
internal object WatchVisualStyle {
    const val TEXT = 0xFFEAF7F0.toInt()
    const val MUTED = 0xFFA7BDB2.toInt()
    const val ACCENT = 0xFF8CE7C8.toInt()
    const val SURFACE = 0xFF11201B.toInt()
    const val OUTLINE = 0xFF294339.toInt()

    fun screenBackground() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xFF091A12.toInt(), 0xFF030806.toInt(), 0xFF030806.toInt()))

    fun primaryTextColor() = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),
        intArrayOf(MUTED,0xFF06110B.toInt()))

    fun surface(context: Context, color: Int = SURFACE, radius: Int = 22) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * context.resources.displayMetrics.density
        setStroke(context.resources.displayMetrics.density.roundToInt().coerceAtLeast(1), OUTLINE)
    }

    fun button(context: Context, primary: Boolean = false): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), surface(context, 0xFF112019.toInt()))
        addState(intArrayOf(android.R.attr.state_pressed), surface(context, if (primary) 0xFFB5F2D8.toInt() else 0xFF294839.toInt()))
        addState(intArrayOf(android.R.attr.state_focused), surface(context, if (primary) ACCENT else 0xFF213D30.toInt()))
        addState(intArrayOf(), surface(context, if (primary) ACCENT else SURFACE))
    }
}

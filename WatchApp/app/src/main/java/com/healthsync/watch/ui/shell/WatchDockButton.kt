package com.healthsync.watch.ui.shell

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View

class WatchDockButton(context: Context, private val glyph: String, label: String) : View(context) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    var badge: Boolean=false
        set(value) { field=value; invalidate() }
    init {
        isClickable=true; isFocusable=true; contentDescription=label
        val fill=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(0xFF14221B.toInt()) }
        background=RippleDrawable(ColorStateList.valueOf(0xFF456E58.toInt()),fill,null)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx=width/2f; val cy=height/2f; val s=minOf(width,height)*.20f
        ink.color=0xFFC4EBD6.toInt(); ink.strokeWidth=s*.18f; ink.style=Paint.Style.STROKE; ink.strokeCap=Paint.Cap.ROUND
        when(glyph) {
            "apps" -> {
                ink.style=Paint.Style.FILL
                for(y in -1..1) for(x in -1..1) canvas.drawCircle(cx+x*s*.85f,cy+y*s*.85f,s*.20f,ink)
            }
            "bell" -> {
                path.reset(); path.moveTo(cx-s,cy+s*.5f); path.lineTo(cx-s*.65f,cy)
                path.cubicTo(cx-s*.7f,cy-s*1.4f,cx+s*.7f,cy-s*1.4f,cx+s*.65f,cy)
                path.lineTo(cx+s,cy+s*.5f); path.close(); canvas.drawPath(path,ink)
                canvas.drawArc(cx-s*.3f,cy+s*.4f,cx+s*.3f,cy+s*1.1f,0f,180f,false,ink)
            }
            "play" -> { path.reset(); path.moveTo(cx-s*.55f,cy-s); path.lineTo(cx+s*.85f,cy); path.lineTo(cx-s*.55f,cy+s); path.close(); canvas.drawPath(path,ink) }
            "back" -> { canvas.drawLine(cx+s*.5f,cy-s,cx-s*.5f,cy,ink); canvas.drawLine(cx-s*.5f,cy,cx+s*.5f,cy+s,ink) }
            else -> {
                canvas.drawCircle(cx,cy,s*.45f,ink)
                for(i in 0..7) {
                    val a=Math.toRadians(i*45.0)
                    canvas.drawLine(cx+kotlin.math.cos(a).toFloat()*s*.78f,cy+kotlin.math.sin(a).toFloat()*s*.78f,
                        cx+kotlin.math.cos(a).toFloat()*s*1.1f,cy+kotlin.math.sin(a).toFloat()*s*1.1f,ink)
                }
                canvas.drawCircle(cx,cy,s*.8f,ink)
            }
        }
        if(badge) { ink.style=Paint.Style.FILL; ink.color=0xFFFFB583.toInt(); canvas.drawCircle(cx+s*.92f,cy-s*.9f,s*.3f,ink) }
    }
}

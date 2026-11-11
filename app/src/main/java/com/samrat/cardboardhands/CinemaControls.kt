package com.samrat.cardboardhands

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** One native VR layer; no page CSS, video state or timer can remove scrolling. */
internal object CinemaControls {
    const val HEIGHT_M = .50f
    const val GAP_M = .13f
    val browser = listOf("back", "forward", "reload", "address", "home", "cinema",
        "scrollUp", "scrollDown", "play", "seekBack", "seekForward", "fullscreen",
        "size", "distance", "curve", "recenter", "environment", "passthrough",
        "website", "hands", "dwell", "sensitivity", "help", "hide", "envYaw")
    val video = browser.filter { it !in listOf("back", "forward", "reload", "address", "home", "scrollUp", "scrollDown", "fullscreen") }
    fun actions(isBrowser: Boolean, shown: Boolean): List<String> =
        if (!shown) (if (isBrowser) listOf("show", "scrollUp", "scrollDown", "help") else listOf("show", "help"))
        else if (isBrowser) browser else video
    fun hit(u: Float, v: Float, actions: List<String>): String? {
        if (u !in 0f..1f || v !in 0f..1f) return null
        val cols = if (actions.size > 6) 6 else actions.size
        val rows = (actions.size + cols - 1) / cols
        val col = (u * cols).toInt().coerceAtMost(cols - 1)
        val row = (v * rows).toInt().coerceAtMost(rows - 1)
        return actions.getOrNull(row * cols + col)
    }
    fun label(action: String) = when(action) {
        "scrollUp" -> "Scroll up"; "scrollDown" -> "Scroll down"; "play" -> "Play / pause"
        "seekBack" -> "−10 seconds"; "seekForward" -> "+10 seconds"; "cinema" -> "Cinema / browser"
        "size" -> "Screen size"; "distance" -> "Distance"; "curve" -> "Curvature"
        "envYaw" -> "Room heading"; "recenter" -> "Recenter"; "environment" -> "Environment"; "passthrough" -> "Real world"
        "website" -> "Website controls"; "hands" -> "Hands (experimental)"; "dwell" -> "Dwell time"
        "sensitivity" -> "Sensitivity"; "help" -> "Help"; "hide" -> "Hide controls"; "show" -> "Show controls"
        "address" -> "Address / search"; "fullscreen" -> "Fullscreen"; else -> action.replaceFirstChar { it.uppercase() }
    }
    fun draw(actions: List<String>, hovered: String?, unavailable: Set<String>, hint: String, playback: String = "unknown"): Bitmap {
        val b = Bitmap.createBitmap(1800, 480, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(13, 18, 27); c.drawRoundRect(RectF(0f,0f,1800f,480f),24f,24f,p)
        val cols = if (actions.size > 6) 6 else actions.size
        val rows = (actions.size + cols - 1) / cols
        val cellH = 400f / rows
        for ((i,a) in actions.withIndex()) {
            val x = (i % cols) * 1800f / cols
            val y = (i / cols) * cellH
            val r = RectF(x+7,y+7,x+1800f/cols-7,y+cellH-7)
            p.color = if (a==hovered) Color.rgb(55,87,110) else Color.rgb(27,36,50)
            c.drawRoundRect(r,16f,16f,p)
            p.color = if (a in unavailable) Color.rgb(125,132,145) else Color.WHITE
            p.textSize = 27f; p.textAlign = Paint.Align.CENTER
            val shown=if(a=="play") when(playback) {"playing"->"Pause";"paused","ended"->"Play";"buffering"->"Buffering…";else->label(a)} else label(a)
            c.drawText(shown,r.centerX(),r.centerY()+10,p)
        }
        p.color=Color.rgb(171,204,220); p.textSize=26f
        c.drawText(hint.take(110),900f,454f,p)
        return b
    }
}

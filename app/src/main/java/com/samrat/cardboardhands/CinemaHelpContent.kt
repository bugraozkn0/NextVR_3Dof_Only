package com.samrat.cardboardhands

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture

/** User-opened help, offset from the movie; no popup interrupts playback. */
internal class CinemaHelpContent : VrWindow.Content {
    override val pixelWidth=1500
    override val pixelHeight=1050
    override val external=false
    private var image:Bitmap?=null
    private var fresh=true
    override fun attach(context:Context,texture:SurfaceTexture?,onReady:()->Unit) {
        image?.recycle();fresh=true
        image=Bitmap.createBitmap(pixelWidth,pixelHeight,Bitmap.Config.ARGB_8888).also { b ->
            val c=Canvas(b);c.drawColor(Color.rgb(14,20,30))
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textSize=40f}
            val lines=listOf("NEXTVR CINEMA • HELP",
                "Gaze: hold your view on a control until its ring fills.",
                "Head tremors are tolerated; empty space cannot click.",
                "Scroll: gaze at the native up/down buttons to repeat.",
                "Cinema: controls remain BELOW the screen during playback.",
                "Website controls: explicitly unlock the web player's surface.",
                "Recenter: face the comfortable direction and select Recenter.",
                "Grab: pinch a window's move handle, drag, then release.",
                "Resize: pinch the corner handle, or use Screen size.",
                "Hands: experimental and optional; camera permission required.",
                "Browser pinch: move hand UP to scroll DOWN; release to stop.",
                "Short pinch on a link selects it; moving prevents the click.",
                "Two-hand pinch: spread both hands to enlarge the screen.",
                "Hand detection lost: grabs and pending clicks cancel.",
                "Align screen: select, look at the cinema wall, hold still.",
                "Room -5° / +5° adjusts the panorama independently.",
                "Camera Rotate +90° fixes orientation; hands rotate too.",
                "Real world: camera behind the movie. Environments: virtual.",
                "Gallery is optional. Close Help with its window close button.")
            lines.forEachIndexed { i,line ->p.textSize=if(i==0) 48f else 32f;c.drawText(line,45f,65f+i*51f,p)}
        }
        onReady()
    }
    override fun takeBitmap():Bitmap?=if(fresh) image.also {fresh=false} else null
    override fun touch(action:Int,u:Float,v:Float) {}
    override fun release() {image?.recycle();image=null}
}

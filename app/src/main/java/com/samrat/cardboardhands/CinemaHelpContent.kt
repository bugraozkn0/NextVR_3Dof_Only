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
                "Gaze: look at a button until the blue ring fills.",
                "Ordinary text and the locked movie do not select.",
                "Scroll: dwell on native Scroll up/down; keep looking to repeat.",
                "Cinema: choose Cinema / browser. Controls stay below screen.",
                "Hide controls leaves a small Show controls bar.",
                "Website controls explicitly unlock the player surface.",
                "DRM / iframe players may need the website's own controls.",
                "Recenter: face your comfortable direction, then select Recenter.",
                "Move: dwell on the handle, move head, hold still to drop.",
                "Resize: dwell on corner handle, or choose Screen size.",
                "Hands: experimental, off by default. Camera required.",
                "Pinch a button; hold a handle to move/resize; release to drop.",
                "Hand lost: grab cancels and gaze returns.",
                "Real world: camera behind the movie. Environment: virtual.",
                "Camera stays on while Hands or Real world is enabled.",
                "Gallery is optional. Phone menu: Open a single video.",
                "Dwell time / Sensitivity change gaze preferences.",
                "Close Help using its window's close button.")
            lines.forEachIndexed { i,line ->p.textSize=if(i==0) 48f else 32f;c.drawText(line,45f,65f+i*51f,p)}
        }
        onReady()
    }
    override fun takeBitmap():Bitmap?=if(fresh) image.also {fresh=false} else null
    override fun touch(action:Int,u:Float,v:Float) {}
    override fun release() {image?.recycle();image=null}
}

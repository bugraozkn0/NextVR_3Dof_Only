package com.samrat.cardboardhands

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.SystemClock
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** CameraX 1.4 Preview -> OES texture, independent of ImageAnalysis and movie textures.
 * SurfaceRequest owns the lease until its result callback; never release a live camera surface.
 * Sources: developer.android.com/reference/androidx/camera/core/SurfaceRequest
 * developer.android.com/reference/android/graphics/SurfaceTexture#updateTexImage()
 * Construct, latch and sample UV on the current GL context only.
 */
internal class CinemaCameraPreview(request: SurfaceRequest, executor: Executor) {
    val texture = IntArray(1).also { GLES20.glGenTextures(1,it,0) }[0]
    private val surfaceTexture: SurfaceTexture
    private val surface: Surface
    private val pending = AtomicBoolean()
    private val captured = AtomicLong()
    private var latched = 0L
    private var epoch = SystemClock.elapsedRealtime()
    private val transform = FloatArray(16)
    /** CameraX's buffer-to-target rotation (also determines the displayed crop aspect). */
    @Volatile private var rotation = 0
    /** SurfaceTexture may have already applied sensor orientation. Do not rotate it twice. */
    @Volatile private var targetRotation = 0
    @Volatile private var hasCameraTransform = false
    @Volatile private var crop = android.graphics.Rect(0,0,request.resolution.width,request.resolution.height)
    /** User correction for unusual camera HAL orientation. Applied after the automatic transform. */
    @Volatile var correctionDegrees = 0
    @Volatile var mirror = false
    @Volatile var captureFps = 0f; private set
    @Volatile var previewFps = 0f; private set
    @Volatile var bufferAgeMs = 0f; private set
    private val width = request.resolution.width
    private val height = request.resolution.height
    var ready = false; private set
    val aspect get() = if (rotation % 180 == 0) crop.width().toFloat()/crop.height() else crop.height().toFloat()/crop.width()
    init {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        surfaceTexture = SurfaceTexture(texture).apply {
            setDefaultBufferSize(width,height)
            setOnFrameAvailableListener { captured.incrementAndGet(); pending.set(true) }
        }
        surface = Surface(surfaceTexture)
        request.setTransformationInfoListener(executor) { info ->
            rotation = info.rotationDegrees
            targetRotation = info.targetRotation
            hasCameraTransform = info.hasCameraTransform()
            crop = android.graphics.Rect(info.cropRect)
        }
        request.provideSurface(surface,executor) {
            surface.release(); surfaceTexture.release()
            // Texture deletion belongs to the GL context and is handled by the renderer lease.
        }
    }
    fun latch() {
        if (pending.getAndSet(false)) {
            runCatching {
                surfaceTexture.updateTexImage()
                surfaceTexture.getTransformMatrix(transform)
                // Camera buffer age is an approximation, not measured photon-to-display latency.
                val age = (System.nanoTime()-surfaceTexture.timestamp)/1_000_000f
                bufferAgeMs = if (age in 0f..1000f) age else -1f
                ready=true; latched++
            }
        }
        val now=SystemClock.elapsedRealtime()
        if(now-epoch>=1000) {
            captureFps=captured.getAndSet(0)*1000f/(now-epoch)
            previewFps=latched*1000f/(now-epoch); latched=0;epoch=now
        }
    }
    /** Same rotation/crop and source UV in both eye viewports. */
    fun quad(eyeAspect: Float): FloatArray {
        val a=aspect
        val us=if(eyeAspect<a) eyeAspect/a else 1f
        val vs=if(eyeAspect>a) a/eyeAspect else 1f
        val correctedRotation = (CinemaCameraUv.remainingRotation(rotation, targetRotation, hasCameraTransform)
            + correctionDegrees).mod(360)
        val validCrop = crop.takeIf { it.width() > 0 && it.height() > 0 }
            ?: android.graphics.Rect(0, 0, width, height)
        fun point(x:Float,y:Float,u:Float,v:Float):FloatArray {
            val dx=.5f+(u-.5f)*us
            val dy=.5f+(v-.5f)*vs
            // Apply user mirroring in DISPLAY space, then one source UV rotation.
            val (rx,ry)=CinemaCameraUv.rotate(if(mirror) 1f-dx else dx,dy,correctedRotation)
            val sx=(validCrop.left+rx*validCrop.width())/width
            val sy=1f-(validCrop.top+ry*validCrop.height())/height
            return floatArrayOf(x,y,0f,transform[0]*sx+transform[4]*sy+transform[12],transform[1]*sx+transform[5]*sy+transform[13])
        }
        return point(-1f,-1f,0f,1f)+point(1f,-1f,1f,1f)+point(-1f,1f,0f,0f)+point(1f,1f,1f,0f)
    }
}

/** Pure orientation math for deterministic tests, independent of Android's GL context.
 * CameraX Preview documentation: the SurfaceTexture transform already includes the
 * camera sensor transform when hasCameraTransform=true; only target-display rotation
 * remains. Otherwise CameraX rotationDegrees corrects the untransformed buffer.
 */
internal object CinemaCameraUv {
    fun remainingRotation(sourceDegrees:Int, targetRotation:Int, hasCameraTransform:Boolean):Int =
        if (hasCameraTransform) (360 - ((targetRotation.mod(4)) * 90)).mod(360)
        else sourceDegrees.mod(360)

    /** Inverse mapping: screen-space UV -> texture-space UV (clockwise image rotation). */
    fun rotate(u:Float,v:Float,degrees:Int):Pair<Float,Float> = when(degrees.mod(360)) {
        90 -> v to (1f-u)
        180 -> (1f-u) to (1f-v)
        270 -> (1f-v) to u
        else -> u to v
    }
}

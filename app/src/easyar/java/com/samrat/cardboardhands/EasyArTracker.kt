package com.samrat.cardboardhands

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import cn.easyar.CameraTransformType
import cn.easyar.Engine
import cn.easyar.InputFrameToOutputFrameAdapter
import cn.easyar.MotionTrackerCameraDevice
import cn.easyar.MotionTrackerCameraDeviceFPS
import cn.easyar.MotionTrackerCameraDeviceResolution
import cn.easyar.MotionTrackingStatus
import cn.easyar.OutputFrameBuffer
import java.util.Locale
import java.nio.ByteBuffer
import cn.easyar.ValidationState
import cn.easyar.Vec2F

/** EasyAR Sense Native Android 4.11.0. No Unity, ARCore or custom-camera device.
 * Dataflow and resource ownership verified against the shipped Community Android
 * HelloARMotionTracking/HelloAR.java, using only its EasyARMotionTracker path.
 * https://www.easyar.com/doc/en/api/native/easyar.MotionTrackerCameraDevice.html
 *
 * Native handles are confined to this monitor. Copies of image bytes, not SDK frames,
 * cross to the hand executor. The sensor remains the only source of view rotation.
 */
class EasyArTracker private constructor(private val headAt: ((Long, FloatArray) -> Unit)?) : SixDof {
    private val camera = MotionTrackerCameraDevice()
    private val adapter = InputFrameToOutputFrameAdapter.create()
    private val frames = OutputFrameBuffer.create()
    private val pose = EasyArPose()
    private var running = false
    private var closed = false
    private var index = -1
    private var rawTime = Long.MIN_VALUE
    private var arrival = 0L
    private var acceptAfter = 0L
    private var lastImage = 0L
    private var previewWanted=false
    private var scanWanted=false
    private var eyeAspect=1f
    private var gpu:CinemaGpuCamera?=null
    private var staging:ByteBuffer?=null
    private var stagingNative:cn.easyar.Buffer?=null
    private var rawDof=0
    private val rawPosition=FloatArray(3)
    private var lastScan=0L
    private var plane:FloatArray?=null
    private var validation="pending"
    private var quality=0
    private var accepted=0L
    private var excursion=0f
    private var handBytes:ByteArray?=null
    private var handPixels:IntArray?=null
    private var previewFailed=false
    override val positionTravelMeters:Float @Synchronized get()=excursion
    override val cameraCpuFallback:Boolean @Synchronized get()=previewFailed
    private var imageRotation = 0
    private var screenRotation = 90
    private var nativeStatus = MotionTrackingStatus.NotTracking
    private var count = 0
    private var epoch = SystemClock.elapsedRealtimeNanos()
    private var fps = 0f
    private var clockMatches = false
    private var diagnosticAt = 0L
    @Volatile private var diagnostic = "EasyAR · waiting for first frame"
    @Volatile private var failed = false
    @Volatile private var imageProblem = ""

    init {
        // Same three-node pipeline as the official sample; retain no unused filter/map components.
        val source = camera.inputFrameSource(); val input = adapter.input()
        try { source.connect(input) } finally { source.dispose(); input.dispose() }
        val output = adapter.output(); val sink = frames.input()
        try { output.connect(sink) } finally { output.dispose(); sink.dispose() }
        quality=MotionTrackerCameraDevice.getQualityLevel()
        camera.setBufferCapacity(frames.bufferRequirement() + 3)
        camera.setFrameRateType(MotionTrackerCameraDeviceFPS.Camera_FPS_30)
        camera.setFrameResolutionType(MotionTrackerCameraDeviceResolution.Resolution_1280)
    }

    override val ownsCamera get() = true
    @Synchronized override fun imageDemand(preview:Boolean,scan:Boolean){previewWanted=preview;scanWanted=scan}
    @Synchronized override fun drawCamera(eyeAspect:Float,correction:Int):Boolean = gpu?.draw(eyeAspect,correction)==true
    override val cameraAspect:Float? @Synchronized get()=gpu?.takeIf {it.ready}?.aspect
    @Synchronized override fun attachTexture(texture:Int){gpu?.release();gpu=null}
    @Synchronized override fun releaseGl(){gpu?.release();gpu=null}
    override val placementPoint:FloatArray? @Synchronized get()=plane?.copyOf()
    override val surfaces:List<RoomScan.Surface> @Synchronized get(){
        val point=plane ?: return emptyList()
        if(!scanWanted || !tracking) return emptyList()
        val lines=ArrayList<Float>()
        for(i in -2..2){
            val a=point.copyOf();val b=point.copyOf();a[0]-=.2f;b[0]+=.2f;a[2]+=i*.1f;b[2]+=i*.1f
            a.forEach {lines+=it};b.forEach {lines+=it}
            val c=point.copyOf();val d=point.copyOf();c[2]-=.2f;d[2]+=.2f;c[0]+=i*.1f;d[0]+=i*.1f
            c.forEach {lines+=it};d.forEach {lines+=it}
        }
        return listOf(RoomScan.Surface(RoomScan.Kind.OTHER,lines.toFloatArray(),point,0f,.2f,.2f))
    }
    @Synchronized override fun trackingSummary():String = when {
        failed -> "6DoF error - rotation only"
        !running -> "EasyAR paused"
        accepted==0L -> "Scanning room - DOF $rawDof - license $validation"
        !tracking -> "TRACKING LOST - position held - license $validation"
        else -> String.format(Locale.ROOT,"%s - XYZ %.2f %.2f %.2f m - %s",
            if(nativeStatus==MotionTrackingStatus.Tracking) "6DoF TRACKING" else "6DoF LIMITED",
            pose.position[0],pose.position[1],pose.position[2],if(plane!=null) "horizontal surface found" else "scanning surfaces")
    }
    override val tracking: Boolean
        @Synchronized get() = running && !failed && nativeStatus != MotionTrackingStatus.NotTracking &&
            SystemClock.elapsedRealtimeNanos() - arrival in 0L..250_000_000L

    @Synchronized override fun copyPosition(out: FloatArray) {
        System.arraycopy(pose.position, 0, out, 0, 3)
    }

    @Synchronized override fun statusText(): String = when {
        closed -> "3DoF · EasyAR closed"
        failed -> "3DoF rotation · EasyAR failed; select None to restore CameraX"
        !running -> "EasyAR · paused"
        arrival == 0L -> "EasyAR · initializing motion tracking; move slowly in a lit room"
        !tracking -> "EasyAR · tracking lost/stale; position held · $diagnostic"
        else -> diagnostic + String.format(Locale.ROOT," - raw XYZ %.3f %.3f %.3f",rawPosition[0],rawPosition[1],rawPosition[2]) + imageProblem
    }

    @Synchronized override fun resume(): Boolean {
        if (closed || failed) return false
        if (running) return true
        return runCatching {
            Engine.onResume(); frames.resume()
            running = camera.start()
            pose.newStream(); rawTime = Long.MIN_VALUE; acceptAfter = SystemClock.elapsedRealtimeNanos()
            if (!running) lastError = "EasyAR camera could not start; 3DoF remains available"
            running
        }.getOrElse { failed = true; lastError = "EasyAR native start failed"; false }
    }

    @Synchronized override fun pause() {
        if (closed) return
        running = false; nativeStatus = MotionTrackingStatus.NotTracking; pose.lost()
        runCatching { camera.stop(); frames.pause(); Engine.onPause() }
    }

    @Synchronized override fun close() {
        if (closed) return
        pause(); closed = true
        runCatching { camera.close() }
        runCatching { camera.dispose() }
        runCatching { adapter.dispose() }
        runCatching { frames.dispose() }
        runCatching { stagingNative?.dispose() };stagingNative=null;staging=null
    }

    @Synchronized override fun recenter() {
        pose.reset();plane=null;accepted=0L;excursion=0f
        acceptAfter = SystemClock.elapsedRealtimeNanos()
    }

    @Synchronized override fun setDisplay(rotation: Int, eyeWidth: Int, height: Int) {
        eyeAspect=eyeWidth.toFloat()/height.coerceAtLeast(1)
        val degrees = rotation.mod(4) * 90 // Android Surface enum -> EasyAR degrees.
        if (screenRotation != degrees) { screenRotation = degrees; pose.lost() }
    }

    @Synchronized override fun update(sensorHead: FloatArray, wantImage: Boolean): (() -> ArTracker.CameraFrame?)? {
        if (!running || closed || failed) return null
        val now = SystemClock.elapsedRealtimeNanos()
        if (arrival != 0L && now - arrival > 250_000_000L) pose.lost()
        return try {
            val output = frames.peek() ?: return null
            try {
                val input = output.inputFrame() ?: return null
                try {
                    if (input.index() == index) return null
                    index = input.index()
                    if (!input.hasTemporalInformation() || !input.hasCameraParameters()) return null
                    val time = EasyArTime.nanoseconds(input.timestamp()) ?: return null
                    if (time <= rawTime) return null
                    rawTime = time
                    val sensorTime = EasyArTime.sensorTime(time, now)
                    clockMatches = sensorTime == time
                    if (sensorTime < acceptAfter) return null
                    arrival = now; count++
                    val parameters = input.cameraParameters()
                    try {
                        imageRotation = parameters.imageOrientation(screenRotation)
                        rawDof=if(input.hasSpatialInformation()) input.cameraTransformType() else 0
                        nativeStatus = if (rawDof == CameraTransformType.SixDof) input.trackingStatus() else MotionTrackingStatus.NotTracking
                        if (nativeStatus != MotionTrackingStatus.NotTracking) {
                            val head = sensorHead.copyOf()
                            headAt?.invoke(sensorTime, head)
                            val rawMatrix=input.cameraTransform().data
                            for(i in 0..2) rawPosition[i]=rawMatrix[i*4+3]
                            if (!pose.update(EasyArPose.displayPose(rawMatrix, imageRotation), head, time)) {
                                nativeStatus = MotionTrackingStatus.NotTracking; pose.lost()
                            } else {
                                accepted++
                                excursion=maxOf(excursion,kotlin.math.sqrt(pose.position.sumOf {(it*it).toDouble()}).toFloat())
                            }
                        } else pose.lost()
                        if(now-diagnosticAt>=1_000_000_000L){
                            validation=when(Engine.validationState()){ValidationState.Successful->"valid";ValidationState.Failed->"rejected";else->"pending"}
                        }
                        if(scanWanted && tracking && now-lastScan>=500_000_000L){
                            lastScan=now
                            val imagePoint=parameters.imageCoordinatesFromScreenCoordinates(eyeAspect,screenRotation,true,false,Vec2F(.5f,.72f))
                            val hit=runCatching {camera.hitTestAgainstHorizontalPlane(imagePoint).firstOrNull()?.data}.getOrNull()
                            if(hit!=null) plane=pose.point(hit)
                        }
                        if (now - epoch >= 1_000_000_000L) {
                            fps = count * 1e9f / (now - epoch); count = 0; epoch = now
                        }
                        if (now - diagnosticAt >= 1_000_000_000L) {
                            diagnosticAt = now
                            diagnostic = String.format(Locale.ROOT,
                                "EasyAR %s · %.0f Hz · xyz %.2f %.2f %.2f m · t=%.3f s · %s · rebases %d · DOF %d · license %s · quality %d · accepted %d",
                                if (nativeStatus == MotionTrackingStatus.Tracking) "tracking" else if (nativeStatus == MotionTrackingStatus.Limited) "limited" else "lost",
                                fps, pose.position[0], pose.position[1], pose.position[2], input.timestamp(),
                                if (clockMatches) "sensor clock" else "arrival clock", pose.rebases,rawDof,validation,quality,accepted)
                        }
                        val handDue=wantImage && now-lastImage>=33_000_000L
                        if(!previewWanted && !handDue) return null
                        val image = input.image()
                        try {
                            val buffer = image.buffer()
                            try {
                                val w = image.width(); val h = image.height()
                                val stride = image.pixelWidth(); val rows = image.pixelHeight(); val format = image.format()
                                val size=buffer.size()
                                if(CinemaCameraLayout.bytes(format,w,h,stride,rows)==null || size !in 1..32_000_000){imageProblem=" - unsupported image layout ($format)";return null}
                                if(previewWanted && !previewFailed) try {
                                    var direct=staging
                                    if(direct==null || direct.capacity()!=size){
                                        stagingNative?.dispose()
                                        direct=ByteBuffer.allocateDirect(size);staging=direct
                                        stagingNative=cn.easyar.Buffer.wrapBuffer(direct)
                                    }
                                    check(buffer.tryCopyTo(0,stagingNative!!.data(),0,size))
                                    val preview=gpu ?: CinemaGpuCamera().also {gpu=it}
                                    preview.upload(direct,format,w,h,stride,rows,imageRotation,parameters.imageHorizontalFlip(false),now)
                                } catch(_:Exception){
                                    previewFailed=true;gpu?.release();gpu=null
                                    imageProblem=" - GPU preview failed; CPU fallback active"
                                }
                                if(!handDue) return null
                                lastImage=now
                                val bytes=handBytes?.takeIf {it.size==size} ?: ByteArray(size).also {handBytes=it}
                                buffer.copyToByteArray(bytes)
                                val degrees = imageRotation
                                val mirrored = parameters.imageHorizontalFlip(false)
                                val convert: () -> ArTracker.CameraFrame? = {
                                    val rgb = EasyArPixels.decode(bytes, format, w, h, stride, rows,handPixels)
                                    if (rgb == null) { imageProblem=" · camera image format/layout $format unsupported; hands/preview unavailable"; null } else {
                                        handPixels=rgb.pixels
                                        if(!previewFailed) imageProblem=""
                                        val raw = Bitmap.createBitmap(rgb.pixels, rgb.width, rgb.height, Bitmap.Config.ARGB_8888)
                                        val matrix = Matrix().apply { postRotate(degrees.toFloat()); if (mirrored) postScale(-1f, 1f) }
                                        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                                        if (upright !== raw) raw.recycle()
                                        ArTracker.CameraFrame(upright, sensorTime, 0f, 0f, 1f, 1f)
                                    }
                                }
                                convert
                            } finally { buffer.dispose() }
                        } finally { image.dispose() }
                    } finally { parameters.dispose() }
                } finally { input.dispose() }
            } finally { output.dispose() }
        } catch (error: Exception) {
            failed = true; nativeStatus = MotionTrackingStatus.NotTracking; pose.lost()
            lastError = "EasyAR frame failed: "+error.javaClass.simpleName+" "+(error.message?:"").replace(Regex("[A-Za-z0-9+/=]{40,}"),"[redacted]").take(120)
            runCatching { camera.stop() }
            null
        }
    }

    companion object {
        @Volatile private var lastError: String? = null
        fun missingReason(context: Context): String? = when {
            !BuildConfig.BE -> "EasyAR Native is available in Cinema only"
            !SixDofSupport.hasImu(context) -> "EasyAR needs a gyroscope and accelerometer"
            !SixDofSupport.hasCamera(context) -> "EasyAR needs a camera"
            runCatching { context.assets.open("easyar-license.txt").bufferedReader().use { it.readText().isBlank() } }.getOrDefault(true) ->
                "EasyAR license missing; configure easyar.local.properties and rebuild"
            else -> null
        }

        fun create(activity: Activity, headAt: ((Long, FloatArray) -> Unit)? = null): EasyArTracker? {
            if (missingReason(activity) != null || activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return null
            return runCatching {
                val key = activity.assets.open("easyar-license.txt").bufferedReader().use { it.readText().trim() }
                // Never log the key or SDK error text (which may contain license details).
                if (!Engine.initialize(activity, key)) { lastError = "EasyAR license rejected: "+Engine.errorMessage().replace(key,"[redacted]").replace(Regex("[A-Za-z0-9+/=]{40,}"),"[redacted]").take(160); return null }
                if (!MotionTrackerCameraDevice.isAvailable()) { lastError = "EasyAR motion tracking is unsupported on this device"; return null }
                lastError = null
                EasyArTracker(headAt)
            }.getOrElse { lastError = "EasyAR native library could not initialize"; null }
        }

        fun failureReason(): String? = lastError
    }
}

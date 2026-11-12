package com.samrat.cardboardhands

import kotlin.math.cos
import kotlin.math.sin

/** SDK-independent alignment math, column-major GL matrices, metres throughout.
 * EasyAR cameraTransform is CAMERA TO WORLD, row-major, right-handed (+z back).
 * https://www.easyar.com/doc/en/api/native/easyar.InputFrame.html
 * Keep the sensor rotation as the only view rotation. Map translation into that exact
 * recentered world using a full rotation (Cinema can recenter while lying down).
 */
internal class EasyArPose {
    val position = FloatArray(3)
    private val origin = FloatArray(3)
    private val lastRaw = FloatArray(3)
    private val offset = FloatArray(3)
    private val rotation = FloatArray(9)
    private var aligned = false
    private var lost = true
    private var lastTime = Long.MIN_VALUE
    var rebases = 0; private set

    fun reset() {
        aligned = false; lost = true; lastTime = Long.MIN_VALUE
        position.fill(0f); offset.fill(0f)
    }

    fun lost() { lost = true }
    fun newStream() { lost = true; lastTime = Long.MIN_VALUE }
    fun point(raw:FloatArray):FloatArray? {
        if(!aligned || raw.size<3 || raw.any {!it.isFinite()}) return null
        return FloatArray(3) {r -> offset[r]+(0..2).sumOf {c ->
            (rotation[c*3+r]*(raw[c]-origin[c])).toDouble()
        }.toFloat()}
    }

    fun update(camera: FloatArray, sensor: FloatArray, timeNs: Long): Boolean {
        if (camera.size != 16 || sensor.size != 16 || camera.any { !it.isFinite() } ||
            sensor.any { !it.isFinite() } || timeNs <= lastTime) return false
        lastTime = timeNs
        val jump = aligned && (0..2).sumOf { i ->
            val d = (camera[12 + i] - lastRaw[i]).toDouble(); d * d
        } > .25
        if (!aligned || lost || jump) {
            // A = sensorHead * inverse(cameraDisplayRotation). Translation is never scaled.
            for (r in 0..2) for (c in 0..2) {
                rotation[c * 3 + r] = (0..2).sumOf { k ->
                    (sensor[k * 4 + r] * camera[k * 4 + c]).toDouble()
                }.toFloat()
            }
            for (i in 0..2) { origin[i] = camera[12 + i]; offset[i] = position[i] }
            if (aligned) rebases++
            aligned = true; lost = false
        }
        for (r in 0..2) {
            position[r] = offset[r] + (0..2).sumOf { c ->
                (rotation[c * 3 + r] * (camera[12 + c] - origin[c])).toDouble()
            }.toFloat()
            lastRaw[r] = camera[12 + r]
        }
        return true
    }

    companion object {
        /** Right-multiply the camera pose by image-to-display rotation, once, then align.
         * CameraParameters.imageOrientation returns clockwise image rotation in DEGREES.
         * P_display = Rz(-angle) * P_camera, hence C_display = C_camera * Rz(angle).
         */
        fun displayPose(rowMajor: FloatArray, imageRotation: Int): FloatArray {
            require(rowMajor.size == 16)
            val out = FloatArray(16) { i -> rowMajor[(i % 4) * 4 + i / 4] }
            val a = Math.toRadians(imageRotation.toDouble())
            val c = cos(a).toFloat(); val s = sin(a).toFloat()
            for (r in 0..3) {
                val x = out[r]; val y = out[4 + r]
                out[r] = c * x + s * y
                out[4 + r] = -s * x + c * y
            }
            return out
        }
    }
}

/** EasyAR timestamps are seconds; the API does not promise Android's clock epoch.
 * Preserve raw SDK time for diagnostics. Only use it for sensor-history lookup when
 * it matches Android elapsed realtime; otherwise use arrival time explicitly.
 */
internal object EasyArTime {
    fun nanoseconds(seconds: Double): Long? =
        if (seconds.isFinite() && seconds > 0 && seconds < Long.MAX_VALUE / 1e9)
            (seconds * 1e9).toLong() else null

    fun sensorTime(rawNs: Long, arrivalNs: Long): Long =
        if (kotlin.math.abs(rawNs - arrivalNs) < 2_000_000_000L) rawNs else arrivalNs
}

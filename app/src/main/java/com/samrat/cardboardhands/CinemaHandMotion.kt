package com.samrat.cardboardhands

import kotlin.math.abs
import kotlin.math.hypot

/** Gesture interpretation is independent of the ML model and can be unit tested.
 * One pinch on a browser page is a TAP if it stays still, or a vertical SCROLL
 * once the hand moves farther than the slop. Coordinates are normalized 0..1.
 */
internal class CinemaHandMotion(private val slop: Float = .027f) {
    private var down = false
    private var startX = 0f
    private var startY = 0f
    private var lastY = 0f
    private var movedBeyondTapSlop = false
    var scrolling = false; private set

    fun begin(x:Float,y:Float) {
        down = true; scrolling = false; movedBeyondTapSlop = false
        startX = x; startY = y; lastY = y
    }

    /** Positive return value scrolls the web page DOWN when the hand moves UP. */
    fun sample(x:Float,y:Float):Float {
        if (!down) return 0f
        // Large sideways movements are neither taps nor vertical scrolls.
        // Never activate a page link after the user's hand has travelled away.
        if (hypot(x-startX,y-startY) > slop) movedBeyondTapSlop = true
        if (!scrolling) {
            val vertical = y-startY
            if (abs(vertical) < slop || abs(vertical) < abs(x-startX)*.6f) return 0f
            scrolling = true
            // Include the original movement once: no jump when crossing the threshold.
            lastY = startY
        }
        val delta = (lastY-y).coerceIn(-.15f,.15f)
        lastY = y
        return delta
    }

    /** True means the pinch stayed still enough to count as a click. */
    fun finish():Boolean {
        val tap = down && !scrolling && !movedBeyondTapSlop
        cancel()
        return tap
    }

    fun cancel() {down=false;scrolling=false;movedBeyondTapSlop=false}
}

/** Two pinches resize the focused VR window based on their separation in the image.
 * It is not true metric depth tracking. The second hand must stay pinched for
 * a short period to avoid accidental activation while a second hand enters frame.
 */
internal class CinemaTwoHandScale(private val armMs:Long=160L) {
    private var candidateAt = -1L
    private var baseline = 1f
    private var initialScale = 1f
    var active = false; private set
    var blocking = false; private set

    fun update(
        bothPinched:Boolean, leftX:Float,leftY:Float,rightX:Float,rightY:Float,
        nowMs:Long, currentScale:Float,
    ):Float? {
        if (!bothPinched) {
            candidateAt=-1L
            active=false
            blocking=false
            return null
        }
        if (candidateAt<0L || nowMs<candidateAt) {
            candidateAt=nowMs
            blocking=true // Reserve input immediately; don't click the browser while zoom arms.
            return null
        }
        val distance=hypot(leftX-rightX,leftY-rightY)
        if (distance < .09f) return null
        if (!active) {
            if (nowMs-candidateAt < armMs) return null
            initialScale=currentScale
            baseline=distance
            active=true
            blocking=true
            return initialScale
        }
        return (initialScale*(distance/baseline)).coerceIn(.6f,3.4f)
    }

    fun cancel() { candidateAt=-1L;active=false;blocking=false }
}

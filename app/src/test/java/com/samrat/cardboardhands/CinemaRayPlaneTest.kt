package com.samrat.cardboardhands

import org.junit.Assert.*
import org.junit.Test

class CinemaRayPlaneTest {
    @Test fun lateralAndVerticalHeadMovementMoveTheHitByMetres() {
        assertArrayEquals(floatArrayOf(.2f,.3f),CinemaRayPlane.intersect(0f,0f,-1f,.2f,.3f,0f,1f)!!,.00001f)
    }
    @Test fun forwardAndBackwardMovementChangeRayDistance() {
        assertArrayEquals(floatArrayOf(.65f,.26f),CinemaRayPlane.intersect(.5f,.2f,-1f,0f,0f,.3f,1f)!!,.00001f)
        assertArrayEquals(floatArrayOf(.35f,.14f),CinemaRayPlane.intersect(.5f,.2f,-1f,0f,0f,-.3f,1f)!!,.00001f)
    }
    @Test fun parallelBehindAndInvalidRaysNeverActivateControls() {
        assertNull(CinemaRayPlane.intersect(1f,0f,0f,0f,0f,0f,1f))
        assertNull(CinemaRayPlane.intersect(0f,0f,-1f,0f,0f,-2f,1f))
        assertNull(CinemaRayPlane.intersect(0f,0f,Float.NaN,0f,0f,0f,1f))
    }
}

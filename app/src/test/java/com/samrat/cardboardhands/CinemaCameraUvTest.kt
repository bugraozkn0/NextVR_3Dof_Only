package com.samrat.cardboardhands

import org.junit.Assert.assertEquals
import org.junit.Test

/** CameraX may place the camera transform in the SurfaceTexture or leave it to the app. */
class CinemaCameraUvTest {
    @Test fun directCameraSurfaceUsesOnlyDisplayRotation() {
        assertEquals(0, CinemaCameraUv.remainingRotation(90, 0, true))
        assertEquals(270, CinemaCameraUv.remainingRotation(90, 1, true))
        assertEquals(180, CinemaCameraUv.remainingRotation(90, 2, true))
        assertEquals(90, CinemaCameraUv.remainingRotation(90, 3, true))
    }

    @Test fun transformedSurfaceUsesCameraXRotation() {
        assertEquals(90, CinemaCameraUv.remainingRotation(90, 1, false))
        assertEquals(270, CinemaCameraUv.remainingRotation(270, 3, false))
        assertEquals(0, CinemaCameraUv.remainingRotation(360, 0, false))
    }

    @Test fun textureQuarterTurnsHaveInverseMappings() {
        val point = .25f to .75f
        val quarter = CinemaCameraUv.rotate(point.first, point.second, 90)
        assertEquals(.75f,quarter.first,.0001f)
        assertEquals(.75f,quarter.second,.0001f)
        val restored = CinemaCameraUv.rotate(quarter.first, quarter.second, 270)
        assertEquals(point.first, restored.first, .0001f)
        assertEquals(point.second, restored.second, .0001f)
    }

    @Test fun halfTurnAndFullTurn() {
        assertEquals(.75f to .25f, CinemaCameraUv.rotate(.25f,.75f,180))
        assertEquals(.25f to .75f, CinemaCameraUv.rotate(.25f,.75f,360))
    }
    @Test fun manualCameraCorrectionMapsHandInTheSameDirection() {
        val corrected=CinemaCameraUv.handToView(.25f,.75f,90)
        assertEquals(.25f,corrected.first,.0001f)
        assertEquals(.25f,corrected.second,.0001f)
        val mirrored=CinemaCameraUv.handToView(.25f,.75f,90,true)
        assertEquals(.75f,mirrored.first,.0001f)
        assertEquals(.25f,mirrored.second,.0001f)
    }

}

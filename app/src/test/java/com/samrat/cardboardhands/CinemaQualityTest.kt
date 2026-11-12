package com.samrat.cardboardhands

import org.junit.Assert.*
import org.junit.Test

class CinemaQualityTest {
    @Test fun aHandAlreadyClosedWhenItAppearsCannotClick() {
        val gate=CinemaPinchGate(35,requireOpen=true)
        assertEquals(CinemaPinchGate.Event.NONE,gate.update(true,true,100))
        assertEquals(CinemaPinchGate.Event.NONE,gate.update(true,true,1000))
        assertFalse(gate.ready)
        gate.update(true,false,1010)
        assertTrue(gate.ready)
        gate.update(true,true,1100)
        assertEquals(CinemaPinchGate.Event.DOWN,gate.update(true,true,1135))
    }
    @Test fun handLossRequiresAnOpenHandBeforeAnotherSelection() {
        val gate=CinemaPinchGate(35,requireOpen=true)
        gate.update(true,false,100);gate.update(true,true,150);gate.update(true,true,200)
        assertEquals(CinemaPinchGate.Event.UP,gate.update(false,false,210))
        assertEquals(CinemaPinchGate.Event.NONE,gate.update(true,true,250))
        assertEquals(CinemaPinchGate.Event.NONE,gate.update(true,true,900))
    }
    @Test fun shortNoisyPinchDoesNotTriggerASelection() {
        val gate=CinemaPinchGate(35,requireOpen=true)
        gate.update(true,false,100);gate.update(true,true,200)
        assertEquals(CinemaPinchGate.Event.NONE,gate.update(true,false,220))
        assertEquals(CinemaPinchGate.Event.NONE,gate.update(true,false,300))
    }
    @Test fun paddedNativeYuvLayoutUsesPaddedRowsAndRejectsIncompleteChroma() {
        assertEquals(24,CinemaCameraLayout.bytes(2,3,3,4,4))
        assertEquals(24,CinemaCameraLayout.bytes(5,3,3,4,4))
        assertNull(CinemaCameraLayout.bytes(2,3,3,3,4))
        assertNull(CinemaCameraLayout.bytes(2,3,3,4,3))
    }
    @Test fun allNativePixelLayoutsHaveTheRightByteCapacity() {
        assertEquals(8,CinemaCameraLayout.bytes(1,4,2,4,2))
        for(f in 2..5) assertEquals(12,CinemaCameraLayout.bytes(f,4,2,4,2))
        assertEquals(24,CinemaCameraLayout.bytes(6,4,2,4,2))
        assertEquals(32,CinemaCameraLayout.bytes(9,4,2,4,2))
        assertNull(CinemaCameraLayout.bytes(0,4,2,4,2))
        assertNull(CinemaCameraLayout.bytes(6,4,2,3,2))
    }
    @Test fun conversionReusesItsExclusiveWorkerBufferAndOverwritesOldPixels() {
        val reuse=IntArray(4) {0xabcdef}
        val rgb=EasyArPixels.decode(byteArrayOf(10,20,30,40),1,2,2,2,2,reuse)!!
        assertSame(reuse,rgb.pixels)
        assertArrayEquals(intArrayOf(0xff0a0a0a.toInt(),0xff141414.toInt(),0xff1e1e1e.toInt(),0xff282828.toInt()),rgb.pixels)
    }
    @Test fun aPlaneHitUsesTheSameMetricOriginAsTheTrackedHead() {
        fun identity()=FloatArray(16){if(it%5==0) 1f else 0f}
        val a=EasyArPose()
        val camera=identity().also {it[12]=10f;it[13]=20f;it[14]=30f}
        assertNull(a.point(floatArrayOf(10f,20f,30f)))
        a.update(camera,identity(),1)
        assertArrayEquals(floatArrayOf(.1f,0f,-.5f),a.point(floatArrayOf(10.1f,20f,29.5f))!!,.00001f)
    }
    @Test fun compactControlsKeepEssentialActionsAndAnAccessibleAdvancedPanel() {
        val compact=CinemaControls.actions(true,true,false)
        assertEquals(12,compact.size)
        assertEquals("scrollDown",CinemaControls.hit(.75f,.15f,compact))
        assertEquals("hide",CinemaControls.hit(.92f,.62f,compact))
        assertNull(CinemaControls.hit(.5f,.95f,compact))
        assertTrue(CinemaControls.actions(true,true,true).containsAll(listOf("website","cameraRotate","diagnostics","scanRoom","inputMode","dwell","environment")))
    }
}

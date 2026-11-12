package com.samrat.cardboardhands

import org.junit.Assert.*
import org.junit.Test

class EasyArPoseTest {
    private fun identity() = FloatArray(16) { if(it%5==0) 1f else 0f }
    private fun pose(x:Float=0f,y:Float=0f,z:Float=0f) = identity().also {it[12]=x;it[13]=y;it[14]=z}

    @Test fun rowMajorCameraToWorldIsTransposedWithoutInvertingTranslation() {
        val row=floatArrayOf(1f,0f,0f,2f, 0f,1f,0f,3f, 0f,0f,1f,-4f, 0f,0f,0f,1f)
        assertArrayEquals(pose(2f,3f,-4f),EasyArPose.displayPose(row,0),.00001f)
    }

    @Test fun metricMovementHasUnitScaleAndCorrectSignsInAllThreeAxes() {
        val a=EasyArPose();a.update(pose(10f,20f,30f),identity(),1)
        a.update(pose(10.1f,20.2f,29.7f),identity(),2)
        assertArrayEquals(floatArrayOf(.1f,.2f,-.3f),a.position,.00001f)
    }

    @Test fun landscapeRotationAlignsCameraAxesExactlyOnce() {
        val row=identity()
        val display=EasyArPose.displayPose(row,90)
        val a=EasyArPose();a.update(display,identity(),1)
        val next=display.copyOf().also {it[13]=.2f}
        a.update(next,identity(),2)
        assertArrayEquals(floatArrayOf(.2f,0f,0f),a.position,.00001f)
    }

    @Test fun fullRecenterSupportsRecliningInsteadOfAssumingYawOnly() {
        val sensor=identity().also {it[5]=0f;it[6]=1f;it[9]=-1f;it[10]=0f}
        val a=EasyArPose();a.update(identity(),sensor,1)
        a.update(pose(y=.2f),sensor,2)
        assertArrayEquals(floatArrayOf(0f,0f,.2f),a.position,.00001f)
    }

    @Test fun rotationAloneNeverAddsATranslationOrAppliesTwice() {
        val a=EasyArPose();a.update(identity(),identity(),1)
        val turned=identity().also {it[0]=0f;it[2]=-1f;it[8]=1f;it[10]=0f}
        a.update(turned,turned,2)
        assertArrayEquals(FloatArray(3),a.position,0f)
    }

    @Test fun lossAndRelocalizationHoldPositionThenContinueFromNewOrigin() {
        val a=EasyArPose();a.update(identity(),identity(),1);a.update(pose(x=.2f),identity(),2)
        a.lost();a.update(pose(9f,4f,8f),identity(),3)
        assertArrayEquals(floatArrayOf(.2f,0f,0f),a.position,.00001f)
        a.update(pose(9.1f,4f,8f),identity(),4)
        assertEquals(.3f,a.position[0],.00001f)
    }

    @Test fun mapJumpRebasesWithoutMovingTheCinema() {
        val a=EasyArPose();a.update(identity(),identity(),1);a.update(pose(x=.2f),identity(),2)
        a.update(pose(x=2f),identity(),3)
        assertEquals(.2f,a.position[0],.00001f);assertEquals(1,a.rebases)
        a.reset();a.update(pose(x=2f),identity(),4)
        assertArrayEquals(FloatArray(3),a.position,0f)
    }

    @Test fun invalidAndOutOfOrderSamplesNeverMoveTheHead() {
        val a=EasyArPose();a.update(identity(),identity(),10)
        assertFalse(a.update(pose(x=.2f),identity(),9))
        assertFalse(a.update(pose(x=Float.NaN),identity(),11))
        assertArrayEquals(FloatArray(3),a.position,0f)
    }

    @Test fun sdkClockIsNotSilentlyAssumedToBeAndroidClock() {
        assertEquals(1_500_000_000L,EasyArTime.nanoseconds(1.5))
        assertNull(EasyArTime.nanoseconds(Double.NaN));assertNull(EasyArTime.nanoseconds(-1.0))
        assertEquals(10_000_000_000L,EasyArTime.sensorTime(123L,10_000_000_000L))
        assertEquals(10_000_000_000L,EasyArTime.sensorTime(10_000_000_000L,10_010_000_000L))
    }
}

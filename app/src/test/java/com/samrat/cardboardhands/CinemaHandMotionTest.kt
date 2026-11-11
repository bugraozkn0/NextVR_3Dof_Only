package com.samrat.cardboardhands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaHandMotionTest {
    @Test fun stillPinchProducesClick() {
        val motion=CinemaHandMotion()
        motion.begin(.5f,.4f)
        assertEquals(0f,motion.sample(.51f,.41f))
        assertTrue(motion.finish())
        assertFalse(motion.finish())
    }

    @Test fun upwardsHandMotionScrollsDownWithoutClick() {
        val motion=CinemaHandMotion()
        motion.begin(.4f,.5f)
        assertEquals(0f,motion.sample(.4f,.49f))
        assertTrue(motion.sample(.4f,.45f)>0f)
        assertTrue(motion.scrolling)
        assertFalse(motion.finish())
    }

    @Test fun horizontalWiggleDoesNotScroll() {
        val motion=CinemaHandMotion()
        motion.begin(.4f,.5f)
        assertEquals(0f,motion.sample(.55f,.48f))
        assertFalse(motion.scrolling)
        assertTrue(motion.finish())
    }

    @Test fun twoHandScaleNeedsSustainedBothHandPinchAndClamps() {
        val scale=CinemaTwoHandScale(armMs=150)
        assertNull(scale.update(true,.2f,.5f,.6f,.5f,100,1f))
        assertNull(scale.update(true,.2f,.5f,.6f,.5f,200,1f))
        assertEquals(1f,scale.update(true,.2f,.5f,.6f,.5f,250,1f)!!)
        assertTrue(scale.active)
        assertEquals(2f,scale.update(true,.1f,.5f,.9f,.5f,280,1f)!!, .001f)
        assertEquals(3.4f,scale.update(true,0f,0f,1f,1f,300,3f)!!.coerceAtMost(3.4f),.001f)
        assertNull(scale.update(false,.1f,.5f,.9f,.5f,310,2f))
        assertFalse(scale.active)
    }
}

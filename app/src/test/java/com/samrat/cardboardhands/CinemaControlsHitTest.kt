package com.samrat.cardboardhands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CinemaControlsHitTest {
    @Test fun bottomHintIsNotClickable() {
        val actions=CinemaControls.browser
        assertNull(CinemaControls.hit(.5f,.90f,actions))
        assertNull(CinemaControls.hit(.5f,1f,actions))
    }

    @Test fun gridMapsToRealButtons() {
        val actions=listOf("show","scrollUp","scrollDown","help")
        assertEquals("show",CinemaControls.hit(.1f,.25f,actions))
        assertEquals("help",CinemaControls.hit(.9f,.25f,actions))
    }

    @Test fun emptyControlsNeverHit() {
        assertNull(CinemaControls.hit(.5f,.25f,emptyList()))
    }
}

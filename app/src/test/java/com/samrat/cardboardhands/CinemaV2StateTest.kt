package com.samrat.cardboardhands

import org.junit.Assert.*
import org.junit.Test

class CinemaV2StateTest {
    @Test fun tremorKeepsSemanticDwellAndProgress() {
        val c=CinemaGazeController(dwellMs=1000)
        assertFalse(c.update("button:play",0,x=.01f))
        assertFalse(c.update("button:play",500,x=.018f))
        assertEquals(.5f,c.progress,.001f)
        assertTrue(c.update("button:play",1000,x=.003f))
        assertFalse(c.update("button:play",3000,x=.015f))
    }
    @Test fun edgeHysteresisCannotFireAndLookingAwayCancels() {
        val c=CinemaGazeController(dwellMs=1000)
        c.update("a",0);c.update("a",950)
        assertFalse(c.update(null,1000,x=.002f))
        assertTrue(c.update("a",1010,x=.003f))
        c.update(null,1100,x=.5f)
        assertEquals(0f,c.progress,0f)
        assertFalse(c.update("a",1500))
    }
    @Test fun switchingElementCancelsWithoutGrace() {
        val c=CinemaGazeController(dwellMs=1000)
        c.update("a",0);c.update("a",900)
        assertFalse(c.update("b",950))
        assertEquals(0f,c.progress,0f)
        assertFalse(c.update("b",1500))
        assertTrue(c.update("b",1950))
    }
    @Test fun scrollRepeatsAndStopsImmediatelyOnOtherTarget() {
        val c=CinemaGazeController(dwellMs=700,repeatMs=500)
        c.update("scroll:down",0,true)
        assertTrue(c.update("scroll:down",700,true))
        assertFalse(c.update("scroll:down",1199,true))
        assertTrue(c.update("scroll:down",1200,true))
        assertFalse(c.update("link",1250))
        assertFalse(c.update("scroll:down",1300,true))
    }
    @Test fun disabledAndClockRollbackDisarmClicks() {
        val c=CinemaGazeController()
        c.update("a",100);c.disabled(true)
        assertEquals(CinemaGazeController.State.UNAVAILABLE,c.state)
        assertFalse(c.update("a",1100))
        assertFalse(c.update("a",50))
    }
    @Test fun handsOwnOnlyFreshInputAndGazeRecovers() {
        val a=CinemaInputArbiter()
        assertEquals(CinemaInputArbiter.Source.GAZE,a.source(0))
        a.hand(100)
        assertEquals(CinemaInputArbiter.Source.HAND,a.source(399))
        assertEquals(CinemaInputArbiter.Source.GAZE,a.source(400))
        a.hand(500);a.clear()
        assertEquals(CinemaInputArbiter.Source.GAZE,a.source(501))
    }
    @Test fun pinchDebouncesAndLossReleasesExactlyOnce() {
        val g=CinemaPinchGate()
        assertEquals(CinemaPinchGate.Event.NONE,g.update(true,true,0))
        assertEquals(CinemaPinchGate.Event.NONE,g.update(true,false,20))
        assertEquals(CinemaPinchGate.Event.NONE,g.update(true,true,40))
        assertEquals(CinemaPinchGate.Event.DOWN,g.update(true,true,105))
        assertEquals(CinemaPinchGate.Event.NONE,g.update(true,true,180))
        assertEquals(CinemaPinchGate.Event.UP,g.update(false,false,190))
        assertEquals(CinemaPinchGate.Event.NONE,g.update(false,false,200))
    }
    @Test fun explicitCinemaUnlockHasNoTimeExpiryAndHideRelocks() {
        val s=CinemaSession()
        assertTrue(s.surfaceEnabled)
        s.enter();assertFalse(s.surfaceEnabled)
        s.toggleWebsite();assertTrue(s.surfaceEnabled)
        s.media("playing");assertTrue(s.surfaceEnabled)
        s.toggleControls();assertFalse(s.surfaceEnabled);assertFalse(s.controls)
        s.toggleControls();assertTrue(s.controls);assertFalse(s.surfaceEnabled)
        s.leave();assertTrue(s.surfaceEnabled)
    }
    @Test fun playbackTransitionsDoNotNavigateOrChangeCinemaMode() {
        val s=CinemaSession();s.enter()
        for(p in listOf("paused","playing","buffering","ended","unknown")) {
            s.media(p);assertTrue(s.cinema);assertTrue(s.controls)
        }
        s.toggleWebsite();s.foreground(false);assertFalse(s.surfaceEnabled)
        s.foreground(true);assertFalse(s.websiteControls);assertTrue(s.cinema)
    }
    @Test fun cameraNeedsExplicitDemandPermissionAndForeground() {
        assertFalse(CinemaCameraDemand.required(true,true,false,false))
        assertTrue(CinemaCameraDemand.required(true,true,true,false))
        assertTrue(CinemaCameraDemand.required(true,true,false,true))
        assertFalse(CinemaCameraDemand.required(false,true,true,true))
        assertFalse(CinemaCameraDemand.required(true,false,true,true))
        assertFalse(CinemaCameraDemand.required(false,false,false,false))
    }
}

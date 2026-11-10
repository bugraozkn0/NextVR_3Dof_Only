package com.samrat.cardboardhands

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaGazeControllerTest {
    @Test fun onlyFiresOnceAfterDwell() {
        val c = CinemaGazeController(dwellMs = 1000)
        assertFalse(c.update("video", 100))
        assertFalse(c.update("video", 1099))
        assertTrue(c.update("video", 1100))
        assertFalse(c.update("video", 4000))
    }
    @Test fun targetChangeDisarms() {
        val c = CinemaGazeController(dwellMs = 1000)
        assertFalse(c.update("a", 0))
        assertFalse(c.update("b", 900))
        assertFalse(c.update("b", 1800))
        assertTrue(c.update("b", 1900))
    }
    @Test fun scrollRepeatsButNeverImmediately() {
        val c = CinemaGazeController(dwellMs = 1000, repeatMs = 600)
        assertFalse(c.update("scroll:down", 0, repeatable = true))
        assertTrue(c.update("scroll:down", 1000, repeatable = true))
        assertFalse(c.update("scroll:down", 1599, repeatable = true))
        assertTrue(c.update("scroll:down", 1600, repeatable = true))
        assertFalse(c.update(null, 1700))
        assertFalse(c.update("scroll:down", 1800, repeatable = true))
    }
}

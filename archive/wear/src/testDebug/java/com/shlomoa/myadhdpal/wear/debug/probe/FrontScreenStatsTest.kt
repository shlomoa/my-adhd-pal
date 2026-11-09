package com.shlomoa.myadhdpal.wear.debug.probe

import org.junit.Assert.assertEquals
import org.junit.Test

/** The debug front screen (experiment E4): screen state and whether it is in front, no heart-rate values. */
class FrontScreenStatsTest {
    @Test
    fun `TC-W-150 the front line gives event, screen state, in front and keep-screen-on`() {
        assertEquals(
            "PROBE front resumed screen=on inFront=true keepOn=false",
            frontLine("resumed", displayState = 2, inFront = true, keepOn = false)
        )
        assertEquals(
            "PROBE front display-changed screen=doze inFront=true keepOn=true",
            frontLine("display-changed", displayState = 3, inFront = true, keepOn = true)
        )
        assertEquals(
            "PROBE front paused screen=off inFront=false keepOn=false",
            frontLine("paused", displayState = 1, inFront = false, keepOn = false)
        )
    }

    @Test
    fun `TC-W-151 a screen state the probe does not know is named other`() {
        assertEquals(
            "PROBE front focus screen=other inFront=false keepOn=true",
            frontLine("focus", displayState = 0, inFront = false, keepOn = true)
        )
    }

    @Test
    fun `TC-W-152 the screen is in front only when it is resumed and has window focus`() {
        assertEquals(true, isInFront(resumed = true, hasFocus = true))
        assertEquals(false, isInFront(resumed = true, hasFocus = false))
        assertEquals(false, isInFront(resumed = false, hasFocus = true))
    }
}

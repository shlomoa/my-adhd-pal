package com.shlomoa.myadhdpal.phone.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure part of the phone alert: what a level means in vibration, flashing and sound. */
class AlertPlannerTest {
    private val prefs = AlertPrefs()

    private fun plan(level: Int, prefs: AlertPrefs = this.prefs, amplitude: Boolean = true) =
        AlertPlanner.plan(level, prefs, hasAmplitudeControl = amplitude)

    /** Waveform timings alternate off, on, off, on, ... starting with a delay. */
    private fun onTime(timings: List<Long>) = timings.filterIndexed { i, _ -> i % 2 == 1 }.sum()

    private fun pauses(timings: List<Long>) = timings.filterIndexed { i, _ -> i % 2 == 0 && i > 0 }

    @Test
    fun `TC-A-090 level 1 vibrates at full amplitude for about 15 s, or on and off only without amplitude control`() {
        val withControl = plan(1).vibration
        assertEquals(15_000L, withControl.timings.sum().coerceIn(13_000L, 17_000L))
        assertTrue(withControl.amplitudes!!.all { it == 0 || it == 255 })
        assertTrue(withControl.amplitudes!!.any { it == 255 })
        assertEquals(withControl.timings.size, withControl.amplitudes!!.size)
        assertTrue("there are pauses at level 1", pauses(withControl.timings).isNotEmpty())

        val without = plan(1, amplitude = false).vibration
        assertNull(without.amplitudes)
        assertEquals(withControl.timings, without.timings)
    }

    @Test
    fun `TC-A-091 level 2 is stronger with about 30 s and no pause over 0_3 s`() {
        val one = plan(1).vibration
        val two = plan(2).vibration
        assertEquals(30_000L, two.timings.sum().coerceIn(28_000L, 32_000L))
        assertTrue(pauses(two.timings).all { it <= 300L })
        assertTrue(onTime(two.timings) > onTime(one.timings))
        assertTrue(plan(2).durationMillis > plan(1).durationMillis)
    }

    @Test
    fun `TC-A-092 flashing is at most 3 per second, faster at level 2, and absent when switched off`() {
        val l1 = plan(1).flash
        val l2 = plan(2).flash
        assertNotNull(l1)
        assertNotNull(l2)
        assertTrue("3 cycles per second is 334 ms or more", l1!!.cycleMillis >= 334 && l2!!.cycleMillis >= 334)
        assertTrue(l2!!.cycleMillis < l1.cycleMillis)
        assertNull(plan(1, AlertPrefs(flashOn = false)).flash)
        assertNull(plan(2, AlertPrefs(flashOn = false)).flash)
    }

    @Test
    fun `TC-A-093 sound is on by default and follows the setting`() {
        assertTrue(plan(1).sound)
        assertTrue(plan(2).sound)
        assertEquals(false, plan(1, AlertPrefs(soundOn = false)).sound)
    }

    @Test
    fun `an unknown level is treated as level 1`() {
        assertEquals(plan(1), plan(0))
        assertEquals(plan(1), plan(7))
    }
}

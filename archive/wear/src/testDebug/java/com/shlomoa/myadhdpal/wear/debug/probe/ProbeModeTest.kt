package com.shlomoa.myadhdpal.wear.debug.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The delivery variants of the M2 probe: exercise without and with the 5 second mode, and a wake lock control. */
class ProbeModeTest {
    @Test
    fun `TC-W-253 the mode names parse and an unknown or missing name means the plain live route`() {
        assertEquals(ProbeMode.WAKELOCK, probeModeOf("wakelock"))
        assertEquals(ProbeMode.EXERCISE, probeModeOf("exercise"))
        assertEquals(ProbeMode.EXERCISE_5S, probeModeOf("exercise5s"))
        assertEquals(ProbeMode.MEASURE, probeModeOf("measure"))
        assertEquals(ProbeMode.MEASURE, probeModeOf(null))
        assertEquals(ProbeMode.MEASURE, probeModeOf("nonsense"))
    }

    @Test
    fun `TC-W-254 only the wake lock mode holds a wake lock and only the two exercise modes use the exercise client`() {
        assertEquals(listOf(ProbeMode.WAKELOCK), ProbeMode.entries.filter { it.wakeLock })
        assertEquals(listOf(ProbeMode.EXERCISE, ProbeMode.EXERCISE_5S), ProbeMode.entries.filter { it.exercise })
        assertTrue(ProbeMode.WAKELOCK.measureClient)
        assertFalse(ProbeMode.EXERCISE.measureClient)
    }

    @Test
    fun `TC-W-255 only the exercise5s mode asks for the 5 second heart-rate reporting`() {
        assertEquals(listOf(ProbeMode.EXERCISE_5S), ProbeMode.entries.filter { it.fiveSeconds })
    }

    @Test
    fun `TC-W-256 each mode has a log line that names it`() {
        assertEquals("PROBE measure mode=exercise5s", ProbeMode.EXERCISE_5S.logLine())
    }
}

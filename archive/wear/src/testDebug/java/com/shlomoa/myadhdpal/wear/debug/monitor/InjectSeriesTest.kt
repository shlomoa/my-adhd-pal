package com.shlomoa.myadhdpal.wear.debug.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

/** The debug injector's synthetic readings (they go through the same evaluator as the real ones). */
class InjectSeriesTest {
    @Test
    fun `TC-W-240 a rising series gives one reading per second from the first to the last value`() {
        val series = risingSeries(from = 60, to = 120, seconds = 30, startAt = 10_000L)
        assertEquals(31, series.size)
        assertEquals(60.0, series.first().value, 0.0)
        assertEquals(120.0, series.last().value, 0.0)
        assertEquals(10_000L, series.first().at)
        assertEquals(40_000L, series.last().at)
    }

    @Test
    fun `TC-W-241 a history gives three samples in each of the last hours at the resting value`() {
        val history = restingHistory(bpm = 62, hours = 25, endAt = 100L * 3_600_000)
        assertEquals(75, history.size)
        assertEquals(setOf(62.0), history.map { it.value }.toSet())
        assertEquals(25, history.map { it.at / 3_600_000 }.toSet().size)
    }

    @Test
    fun `TC-W-242 a list of values parses and a bad list is refused`() {
        assertEquals(listOf(70.0, 95.0, 120.0), parseValues("70,95,120"))
        assertEquals(null, parseValues("70,x"))
        assertEquals(null, parseValues(""))
    }
}

package com.shlomoa.myadhdpal.poc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PulseGraphTest {
    private val s = 1_000_000_000L

    @Test
    fun emptyDataGivesTheDefaultRangeAndNoLine() {
        val g = PulseGraph.layout(emptyList(), 60.0, 600f, 200f)
        assertEquals(50, g.lowBpm)
        assertEquals(100, g.highBpm)
        assertEquals(listOf(50, 60, 70, 80, 90, 100), g.ticks)
        assertTrue(g.segments.isEmpty())
    }

    @Test
    fun newestReadingIsAtTheRightEdgeAndHigherPulseIsHigher() {
        val g = PulseGraph.layout(listOf(GraphPoint(2.0, 60f), GraphPoint(0.0, 90f)), 60.0, 600f, 200f)
        val line = g.segments.single()
        assertEquals(580f, line.first().x, 0.01f) // 2 s old: 2/60 of the width from the right edge
        assertEquals(600f, line.last().x, 0.01f) // newest at the right edge
        assertTrue(line.last().y < line.first().y) // 90 bpm is drawn above 60 bpm
        assertEquals(200f * (1 - 10 / 50.0).toFloat(), line.first().y, 0.01f) // 60 bpm in a 50..100 range
    }

    @Test
    fun aGapStartsANewSegment() {
        val points = listOf(GraphPoint(30.0, 70f), GraphPoint(29.0, 71f), GraphPoint(20.0, 72f), GraphPoint(19.0, 73f))
        val g = PulseGraph.layout(points, 60.0, 600f, 200f)
        assertEquals(listOf(2, 2), g.segments.map { it.size })
    }

    @Test
    fun rangeGrowsForExtremePulseAndStepsWiden() {
        val g = PulseGraph.layout(listOf(GraphPoint(5.0, 45f), GraphPoint(1.0, 165f)), 60.0, 600f, 200f)
        assertEquals(40, g.lowBpm)
        assertEquals(170, g.highBpm)
        assertEquals(20, g.ticks[1] - g.ticks[0])
    }

    @Test
    fun readingsOutsideTheWindowAreNotDrawn() {
        val g = PulseGraph.layout(listOf(GraphPoint(75.0, 70f), GraphPoint(-1.0, 70f)), 60.0, 600f, 200f)
        assertTrue(g.segments.isEmpty())
    }

    @Test
    fun liveStatsHandsTheGraphItsPoints() {
        val live = LiveStats()
        live.add(LiveReading(10 * s, 10 * s, 70f, 3, 50.0, "bt", "r2", "on"))
        live.add(LiveReading(11 * s, 11 * s, 0f, 3, 50.0, "bt", "r2", "on")) // unworn: not pulse
        live.add(LiveReading(12 * s, 12 * s, 72f, 3, 50.0, "bt", "r2", "on"))
        val points = live.graphPoints(14 * s)
        assertEquals(2, points.size)
        assertEquals(4.0, points.first().ageSeconds, 1e-9)
        assertEquals(2.0, points.last().ageSeconds, 1e-9)
    }
}

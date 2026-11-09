package com.shlomoa.myadhdpal.poc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveTest {
    private val s = 1_000_000_000L

    private fun reading(sec: Int, bpm: Float, path: String = "bt", latency: Double? = 80.0, acc: Int = 3) =
        LiveReading(sec * s, sec * s + 50_000_000L, bpm, acc, latency, path, "r2", "on")

    @Test
    fun statusFollowsTheAgeOfTheLastReading() {
        val live = LiveStats()
        live.add(reading(10, 70f))
        assertEquals(LiveStatus.LIVE, live.snapshot(11 * s).status)
        assertEquals(LiveStatus.STALE, live.snapshot(18 * s).status)
        assertEquals(LiveStatus.NO_SIGNAL, live.snapshot(25 * s).status)
        assertEquals(LiveStatus.NO_SIGNAL, LiveStats().snapshot(1 * s).status)
    }

    @Test
    fun theSameReadingOnTwoPathsCountsOnceButKeepsBothLatencies() {
        val live = LiveStats()
        live.add(reading(10, 70f, "bt", 60.0))
        live.add(reading(10, 70f, "dl", 110.0))
        val snap = live.snapshot(11 * s)
        assertEquals(1, snap.readings)
        assertEquals(60.0, snap.latencyBtMs)
        assertEquals(110.0, snap.latencyDlMs)
    }

    @Test
    fun minAvgMaxMeanIntervalAndProxy() {
        val live = LiveStats()
        val bpms = listOf(60f, 70f, 80f, 70f, 60f, 70f, 80f, 70f, 60f, 70f, 80f, 70f)
        bpms.forEachIndexed { i, b -> live.add(reading(i, b)) }
        val snap = live.snapshot(12 * s)
        assertEquals(60f, snap.minBpm)
        assertEquals(80f, snap.maxBpm)
        assertEquals(70.0, snap.avgBpm!!, 1e-9)
        assertEquals(60_000.0 / 70.0, snap.meanIntervalMs!!, 1e-9)
        assertNotNull(snap.variabilityProxyMs)
        assertTrue(snap.variabilityProxyMs!! > 0)
    }

    @Test
    fun trendIsPositiveWhileThePulseRises() {
        val live = LiveStats()
        for (i in 0 until 30) live.add(reading(i, 60f + i * 0.5f)) // +30 bpm per minute
        val trend = live.snapshot(30 * s).trendBpmPerMin!!
        assertEquals(30.0, trend, 0.5)
        assertEquals("rising", LiveFormat.trendArrow(trend))
        assertNull(LiveStats().snapshot(s).trendBpmPerMin)
    }

    @Test
    fun zeroAndImpossibleValuesAreNotPulse() {
        val live = LiveStats()
        live.add(reading(1, 0f))
        live.add(reading(2, 300f))
        val snap = live.snapshot(3 * s)
        assertEquals(0, snap.readings)
        assertNull(snap.avgBpm)
        assertNull(snap.bpm)
        assertEquals("-- bpm", LiveFormat.headline(snap))
    }

    @Test
    fun oldReadingsLeaveTheWindow() {
        val live = LiveStats(windowNanos = 20 * s)
        for (i in 0 until 40) live.add(reading(i, 70f))
        assertTrue(live.snapshot(40 * s).readings <= 21)
    }

    @Test
    fun textStatesWhatIsAnAverageAndWhatIsNotHrv() {
        val live = LiveStats()
        for (i in 0 until 12) live.add(reading(i, 70f + i % 3))
        val text = LiveFormat.details(live.snapshot(12 * s)).joinToString("\n")
        assertTrue("not true beat-to-beat" in text)
        assertTrue("not HRV" in text)
        assertTrue(LiveFormat.logLine(live.snapshot(12 * s)).startsWith("LIVE 7"))
        assertEquals("high", LiveFormat.accuracyLabel(3))
        assertEquals("not reported", LiveFormat.accuracyLabel(-1))
    }
}

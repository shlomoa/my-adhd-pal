package com.shlomoa.myadhdpal.wear.monitor

import com.shlomoa.myadhdpal.protocol.AlertRules
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.alerts.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The continuous comparison of every reading with the baseline (the user's definition of stress detection,
 * 2026-10-17).
 */
class StressMonitorTest {
    private val ids = ArrayDeque((1..9).map { "00000000-0000-4000-8000-00000000000$it" })
    private val monitor = StressMonitor(AlertSettings(AlertRules.DEFAULT_COOLDOWN_SECONDS), { ids.removeFirst() })
    private val hour = 3_600_000L
    private val start = 1_000L * hour

    private fun hr(value: Double, at: Long) = Reading(Metric.HEART_RATE, value, at)

    /** 25 hours of three resting samples each, so that a baseline exists (24 hourly lows are needed). */
    private fun history(bpm: Double) = (1..25).flatMap { h ->
        (0..2).map { hr(bpm, start - h * hour + it * 1_000L) }
    }

    @Test
    fun `TC-W-231 every reading is compared at once with the threshold (the initial value until a baseline exists)`() {
        val calm = monitor.onReadings(listOf(hr(80.0, start)), now = start)
        assertNull(calm.baselineBpm)
        assertEquals(AlertRules.DEFAULT_INITIAL_BPM.toDouble(), calm.thresholdBpm, 0.0)
        assertTrue(calm.alerts.isEmpty())
        assertEquals(80.0, calm.latestBpm!!, 0.0)
        val stress = monitor.onReadings(listOf(hr(115.0, start + 1_000)), now = start + 1_000)
        assertEquals(1, stress.alerts.size)
        assertEquals(1, stress.alerts.single().level)
        assertEquals(start + 1_000, stress.alerts.single().at)
    }

    @Test
    fun `TC-W-232 with a baseline the threshold is the baseline plus the offset and the alert carries it`() {
        val seeded = monitor.onReadings(history(60.0), now = start)
        assertEquals(60.0, seeded.baselineBpm!!, 0.0)
        assertEquals(60.0 + AlertRules.DEFAULT_OFFSET_BPM, seeded.thresholdBpm, 0.0)
        assertTrue(seeded.alerts.isEmpty())
        val below = monitor.onReadings(listOf(hr(89.0, start + 1_000)), now = start + 1_000)
        assertTrue(below.alerts.isEmpty())
        val above = monitor.onReadings(listOf(hr(91.0, start + 2_000)), now = start + 2_000)
        assertEquals(90.0, above.alerts.single().threshold, 0.0)
        assertEquals(91.0, above.alerts.single().value, 0.0)
        assertEquals("00000000-0000-4000-8000-000000000001", above.alerts.single().episodeId)
    }

    @Test
    fun `TC-W-233 a rising series, one reading at a time, raises one level 1 alert, then a new episode`() {
        monitor.onReadings(history(60.0), now = start)
        val alerts = (0..30).flatMap { second ->
            val bpm = 60.0 + second * 2
            monitor.onReadings(listOf(hr(bpm, start + second * 1_000L)), now = start + second * 1_000L).alerts
        }
        assertEquals(1, alerts.size)
        assertEquals(1, alerts.single().level)
        val calm = monitor.onReadings(listOf(hr(70.0, start + 40_000)), now = start + 40_000)
        assertNull(calm.episodeId)
        val again = monitor.onReadings(listOf(hr(120.0, start + 41_000)), now = start + 41_000)
        assertEquals(1, again.alerts.single().level)
        assertEquals("00000000-0000-4000-8000-000000000002", again.alerts.single().episodeId)
    }

    @Test
    fun `TC-W-234 endEpisode ends the running episode so that the next breach is level 1 of a new episode`() {
        monitor.onReadings(history(60.0), now = start)
        val first = monitor.onReadings(listOf(hr(100.0, start)), now = start).alerts.single()
        assertTrue(monitor.endEpisode(first.episodeId!!))
        assertFalse(monitor.endEpisode(first.episodeId!!))
        val next = monitor.onReadings(listOf(hr(100.0, start + 1_000)), now = start + 1_000).alerts.single()
        assertEquals(1, next.level)
        assertEquals("00000000-0000-4000-8000-000000000002", next.episodeId)
    }

    @Test
    fun `TC-W-235 only the hourly lows are kept for saving, never the raw readings`() {
        monitor.onReadings(history(60.0), now = start)
        assertEquals(25, monitor.hourlyLows().size)
    }
}

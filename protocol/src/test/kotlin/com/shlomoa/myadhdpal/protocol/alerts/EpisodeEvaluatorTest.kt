package com.shlomoa.myadhdpal.protocol.alerts

import com.shlomoa.myadhdpal.protocol.AlertRules
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The episode logic of `docs/plans/biometrics.md` section 3 as the watch runs it: threshold = baseline + offset (the
 * initial value until a baseline exists), level 1 at the first breach, level 2 after the cooldown and then every 2
 * minutes, at most 6 level 2 alerts, the episode ends on a reading at or below the threshold or on `endEpisode`.
 */
class EpisodeEvaluatorTest {
    private val t0 = 1_760_000_000_000L
    private val baseline = 60.0
    private val threshold = 90.0 // baseline 60 + default offset 30
    private val ids = ArrayDeque(
        listOf(
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
            "33333333-3333-4333-8333-333333333333"
        )
    )

    private fun evaluator(offset: Int = AlertRules.DEFAULT_OFFSET_BPM, initial: Int = AlertRules.DEFAULT_INITIAL_BPM) =
        EpisodeEvaluator(
            AlertSettings(AlertRules.DEFAULT_COOLDOWN_SECONDS, offset, initial),
            newEpisodeId = { ids.removeFirst() }
        )

    private fun reading(value: Double, second: Long) = Reading(Metric.HEART_RATE, value, t0 + second * 1000)

    private fun EpisodeEvaluator.feed(value: Double, second: Long, base: Double? = baseline) =
        evaluate(listOf(reading(value, second)), base)

    @Test
    fun `TC-W-123 a reading above baseline plus offset starts an episode with a level 1 alert`() {
        val alert = evaluator().feed(threshold + 1, 0).single()
        assertEquals(AlertRules.LEVEL_1, alert.level)
        assertEquals("11111111-1111-4111-8111-111111111111", alert.episodeId)
        assertEquals(threshold + 1, alert.value, 0.0)
        assertEquals(threshold, alert.threshold, 0.0)
        assertEquals(t0, alert.at)
        assertTrue(evaluator().feed(threshold, 0).isEmpty())
    }

    @Test
    fun `TC-W-124 the initial value is the threshold until a baseline exists and the offset is a setting`() {
        assertEquals(110.0, evaluator().thresholdBpm(null), 0.0)
        assertEquals(60.0 + 20, evaluator(offset = 20).thresholdBpm(60.0), 0.0)
        assertEquals(60.0 + 50, evaluator(offset = 50).thresholdBpm(60.0), 0.0)
        assertEquals(130.0, evaluator(initial = 130).thresholdBpm(null), 0.0)
        assertTrue(evaluator().feed(110.0, 0, base = null).isEmpty())
        assertEquals(1, evaluator().feed(111.0, 0, base = null).size)
    }

    @Test
    fun `TC-W-125 inside the cooldown no further alert is raised`() {
        val evaluator = evaluator()
        evaluator.feed(threshold + 5, 0)
        assertTrue(evaluator.feed(threshold + 9, 299).isEmpty())
    }

    @Test
    fun `TC-W-126 the first breach after the cooldown raises a level 2 alert of the same episode`() {
        val evaluator = evaluator()
        val first = evaluator.feed(threshold + 5, 0).single()
        val second = evaluator.feed(threshold + 9, 300).single()
        assertEquals(AlertRules.LEVEL_2, second.level)
        assertEquals(first.episodeId, second.episodeId)
        assertEquals(t0 + 300_000, second.at)
    }

    @Test
    fun `TC-W-127 level 2 repeats at most once per 2 minutes`() {
        val evaluator = evaluator()
        evaluator.feed(threshold + 5, 0)
        evaluator.feed(threshold + 5, 300)
        assertTrue(evaluator.feed(threshold + 5, 360).isEmpty())
        assertTrue(evaluator.feed(threshold + 5, 419).isEmpty())
        assertEquals(1, evaluator.feed(threshold + 5, 420).size)
    }

    @Test
    fun `TC-W-128 at most 6 level 2 alerts are raised in one episode`() {
        val evaluator = evaluator()
        evaluator.feed(threshold + 5, 0)
        val level2 = (0 until 10).flatMap { evaluator.feed(threshold + 5, 300L + it * 120) }
        assertEquals(AlertRules.MAX_LEVEL2_REPEATS, level2.size)
        assertTrue(level2.all { it.level == AlertRules.LEVEL_2 })
        assertTrue(evaluator.feed(threshold + 5, 5000).isEmpty())
    }

    @Test
    fun `TC-W-129 a reading at or below the threshold ends the episode and the next breach starts a new one`() {
        val evaluator = evaluator()
        val first = evaluator.feed(threshold + 5, 0).single()
        assertTrue(evaluator.feed(threshold, 60).isEmpty())
        val again = evaluator.feed(threshold + 5, 120).single()
        assertEquals(AlertRules.LEVEL_1, again.level)
        assertNotEquals(first.episodeId, again.episodeId)
    }

    @Test
    fun `TC-W-130 endEpisode ends the active episode and ignores an unknown or finished one`() {
        val evaluator = evaluator()
        val first = evaluator.feed(threshold + 5, 0).single()
        assertFalse(evaluator.endEpisode("99999999-9999-4999-8999-999999999999"))
        assertEquals(first.episodeId, evaluator.activeEpisodeId)
        assertTrue(evaluator.endEpisode(first.episodeId!!))
        assertFalse(evaluator.endEpisode(first.episodeId!!))
        val next = evaluator.feed(threshold + 5, 10).single()
        assertEquals(AlertRules.LEVEL_1, next.level)
        assertNotEquals(first.episodeId, next.episodeId)
        // a late item for the finished episode cannot end the newer one
        assertFalse(evaluator.endEpisode(first.episodeId!!))
        assertEquals(next.episodeId, evaluator.activeEpisodeId)
    }

    @Test
    fun `TC-W-131 batched and out-of-order readings are evaluated in timestamp order`() {
        val shuffled = evaluator().evaluate(
            listOf(reading(threshold + 9, 300), reading(threshold + 5, 0), reading(threshold + 7, 150)),
            baseline
        )
        assertEquals(listOf(AlertRules.LEVEL_1, AlertRules.LEVEL_2), shuffled.map { it.level })
        assertEquals(listOf(t0, t0 + 300_000), shuffled.map { it.at })
    }

    @Test
    fun `TC-W-138 the alert settings carry no absolute threshold, only baseline plus offset counts`() {
        val evaluator = EpisodeEvaluator(
            AlertSettings(AlertRules.DEFAULT_COOLDOWN_SECONDS, offsetBpm = 30, initialBpm = 110),
            newEpisodeId = { ids.removeFirst() }
        )
        // no baseline: only initialBpm 110 counts
        assertTrue(evaluator.feed(100.0, 0, base = null).isEmpty())
        val alert = evaluator.feed(120.0, 10, base = null).single()
        assertEquals(110.0, alert.threshold, 0.0)
        // with a baseline: only baseline + offset counts
        assertEquals(90.0, evaluator.thresholdBpm(60.0), 0.0)
    }
}

package com.shlomoa.myadhdpal.protocol.alerts

import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Checkpoint
import com.shlomoa.myadhdpal.protocol.Direction
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.protocol.Threshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The evaluator takes thresholds and cooldown as input (they come from the synced schedule, never from code), so the
 * numbers below are only test inputs. Reading and cooldown times are watch-clock epoch milliseconds.
 */
class AlertEvaluatorTest {
    private val limit = 100.0
    private val cooldownSeconds = 60
    private val t0 = 1_760_000_000_000L

    private fun settings(direction: Direction = Direction.ABOVE, cooldown: Int = cooldownSeconds) =
        AlertSettings(listOf(Threshold(Metric.HEART_RATE, direction, limit)), cooldown)

    private fun reading(value: Double, secondsAfterStart: Long) =
        Reading(Metric.HEART_RATE, value, t0 + secondsAfterStart * 1000)

    @Test
    fun `TC-W-070 a reading above the threshold produces one alert with metric, value, threshold and time`() {
        val alerts = AlertEvaluator(settings()).evaluate(listOf(reading(limit + 1, 5)))
        assertEquals(1, alerts.size)
        val alert = alerts.single()
        assertEquals(Metric.HEART_RATE, alert.metric)
        assertEquals(limit + 1, alert.value, 0.0)
        assertEquals(limit, alert.threshold, 0.0)
        assertEquals(t0 + 5000, alert.at)
    }

    @Test
    fun `TC-W-071 a reading at or below the threshold produces no alert`() {
        val evaluator = AlertEvaluator(settings())
        assertTrue(evaluator.evaluate(listOf(reading(limit, 0), reading(limit - 1, 1))).isEmpty())
    }

    @Test
    fun `TC-W-072 a second breach inside the cooldown is suppressed`() {
        val evaluator = AlertEvaluator(settings())
        val alerts = evaluator.evaluate(listOf(reading(limit + 5, 0)))
        val later = evaluator.evaluate(listOf(reading(limit + 9, cooldownSeconds - 1L)))
        assertEquals(1, alerts.size)
        assertTrue(later.isEmpty())
    }

    @Test
    fun `TC-W-073 a breach once the cooldown has elapsed alerts again`() {
        val evaluator = AlertEvaluator(settings())
        val first = evaluator.evaluate(listOf(reading(limit + 5, 0)))
        val second = evaluator.evaluate(listOf(reading(limit + 9, cooldownSeconds.toLong())))
        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(t0 + cooldownSeconds * 1000L, second.single().at)
    }

    @Test
    fun `TC-W-074 a below-limit threshold alerts on a low breach only`() {
        val evaluator = AlertEvaluator(settings(Direction.BELOW))
        assertTrue(evaluator.evaluate(listOf(reading(limit, 0), reading(limit + 20, 1))).isEmpty())
        val alerts = evaluator.evaluate(listOf(reading(limit - 1, 2)))
        assertEquals(1, alerts.size)
        assertEquals(limit - 1, alerts.single().value, 0.0)
    }

    @Test
    fun `TC-W-075 thresholds and cooldown come from the synced schedule settings`() {
        fun scheduleWith(alerts: AlertSettings) =
            Schedule(1, emptyList(), emptyList<Checkpoint>(), SnoozeSettings(10, 3), alerts)
        val strict = scheduleWith(settings(cooldown = 0))
        val lenient = scheduleWith(settings(cooldown = 3600))
        val twoBreaches = listOf(reading(limit + 1, 0), reading(limit + 1, 10))
        assertEquals(2, AlertEvaluator(strict.alerts).evaluate(twoBreaches).size)
        assertEquals(1, AlertEvaluator(lenient.alerts).evaluate(twoBreaches).size)
        assertTrue(AlertEvaluator(AlertSettings(emptyList(), 0)).evaluate(twoBreaches).isEmpty())
    }

    @Test
    fun `TC-W-076 batched and out-of-order readings are evaluated in timestamp order`() {
        val evaluator = AlertEvaluator(settings())
        val alerts = evaluator.evaluate(listOf(reading(limit + 2, 30), reading(limit + 1, 0)))
        assertEquals(1, alerts.size)
        assertEquals(t0, alerts.single().at)
        assertEquals(limit + 1, alerts.single().value, 0.0)
    }
}

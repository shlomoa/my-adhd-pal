package com.shlomoa.myadhdpal.poc.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeartMathTest {
    /** Beat times with the given mean interval and Gaussian jitter; returns beat times in seconds. */
    private fun beats(count: Int, meanMs: Double, jitterMs: Double, seed: Long): List<Double> {
        val random = Random(seed)
        var t = 1.0
        return List(count) { t.also { t += (meanMs + random.nextGaussian() * jitterMs) / 1000.0 } }
    }

    /** A PPG-like waveform: one smooth pulse per beat, slow baseline wander and noise, sampled at [hz]. */
    private fun waveform(beats: List<Double>, hz: Int, inverted: Boolean, seed: Long): Pair<LongArray, DoubleArray> {
        val random = Random(seed)
        val n = ((beats.last() + 1.0) * hz).toInt()
        val times = LongArray(n) { (it * 1e9 / hz).toLong() + 5_000_000_000L }
        val values = DoubleArray(n) { i ->
            val s = i.toDouble() / hz
            val pulse = beats.sumOf { b -> exp(-((s - b) * (s - b)) / (2 * 0.07 * 0.07)) }
            val wander = 0.6 * sin(2 * PI * 0.25 * s)
            (pulse + wander + random.nextGaussian() * 0.03) * (if (inverted) -1 else 1)
        }
        return times to values
    }

    private fun run(jitterMs: Double, hz: Int, inverted: Boolean): Triple<HrvMetrics, HrvMetrics, Int> {
        val truth = beats(60, 850.0, jitterMs, seed = 7)
        val (t, v) = waveform(truth, hz, inverted, seed = 11)
        val found = BeatDetector.beatTimes(t, v)
        val estimated = Hrv.metrics(Hrv.clean(BeatDetector.intervalsMs(found)))
        val expected = Hrv.metrics(truth.zipWithNext { a, b -> (b - a) * 1000.0 })
        assertNotNull(estimated)
        assertNotNull(expected)
        return Triple(estimated, expected, found.size)
    }

    @Test
    fun metricsOfKnownIntervals() {
        val m = Hrv.metrics(listOf(800.0, 850.0, 900.0, 850.0))!!
        assertEquals(850.0, m.meanIbiMs, 1e-9)
        assertEquals(60000.0 / 850.0, m.meanBpm, 1e-9)
        assertEquals(sqrt((50.0 * 50 + 50 * 50) / 3), m.sdnnMs, 1e-9) // deviations -50, 0, 50, 0 with n-1
        assertEquals(sqrt((50.0 * 50 + 50 * 50 + 50 * 50) / 3), m.rmssdMs, 1e-9) // differences +50, +50, -50
        assertEquals(0.0, m.pnn50Percent, 1e-9) // differences are exactly 50, not over 50
        assertNull(Hrv.metrics(listOf(800.0, 850.0)))
    }

    @Test
    fun cleanDropsImpossibleAndJumpingIntervals() {
        assertEquals(listOf(800.0, 820.0, 790.0), Hrv.clean(listOf(800.0, 2500.0, 100.0, 820.0, 1500.0, 790.0)))
    }

    @Test
    fun beatsAreFoundAtFiftyHertzWithRealisticJitter() {
        val (estimated, expected, found) = run(jitterMs = 40.0, hz = 50, inverted = false)
        assertTrue(abs(found - 60) <= 1, "beats found: $found")
        assertTrue(
            abs(estimated.meanIbiMs - expected.meanIbiMs) < 10,
            "mean ${estimated.meanIbiMs} vs ${expected.meanIbiMs}"
        )
        assertTrue(abs(estimated.rmssdMs - expected.rmssdMs) < 15, "rmssd ${estimated.rmssdMs} vs ${expected.rmssdMs}")
    }

    @Test
    fun invertedWaveformGivesTheSameBeats() {
        val (estimated, expected, _) = run(jitterMs = 40.0, hz = 100, inverted = true)
        assertTrue(abs(estimated.rmssdMs - expected.rmssdMs) < 15, "rmssd ${estimated.rmssdMs} vs ${expected.rmssdMs}")
    }

    @Test
    fun noBeatsInNoise() {
        val random = Random(3)
        val times = LongArray(1000) { it * 20_000_000L }
        val values = DoubleArray(1000) { random.nextGaussian() * 0.01 }
        val intervals = Hrv.clean(BeatDetector.intervalsMs(BeatDetector.beatTimes(times, values)))
        // Pure noise must not produce a believable, regular heartbeat: too few intervals or implausible variability.
        val m = Hrv.metrics(intervals)
        assertTrue(m == null || m.rmssdMs > 100 || intervals.size < 20, "noise looked like a heartbeat: $m")
    }
}

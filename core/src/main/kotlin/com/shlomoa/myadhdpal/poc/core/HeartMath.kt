package com.shlomoa.myadhdpal.poc.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/** Heart-rate variability numbers computed from beat-to-beat intervals in milliseconds. */
data class HrvMetrics(
    val intervals: Int,
    val meanIbiMs: Double,
    val meanBpm: Double,
    val sdnnMs: Double,
    val rmssdMs: Double,
    val pnn50Percent: Double
)

object Hrv {
    private const val MS_PER_MINUTE = 60_000.0
    private const val MIN_INTERVALS = 3
    private const val PNN_LIMIT_MS = 50.0
    private const val PERCENT = 100.0
    const val MIN_IBI_MS = 300.0
    const val MAX_IBI_MS = 2000.0
    private const val MAX_JUMP = 0.3

    /**
     * Drops intervals outside 300..2000 ms (200..30 beats per minute) and intervals that differ from the previous kept
     * interval by more than 30 percent, the usual crude artifact rule for missed or extra beats.
     */
    fun clean(ibisMs: List<Double>): List<Double> {
        val kept = mutableListOf<Double>()
        for (ibi in ibisMs) {
            val previous = kept.lastOrNull()
            val plausible = ibi in MIN_IBI_MS..MAX_IBI_MS
            if (plausible && (previous == null || abs(ibi - previous) <= MAX_JUMP * previous)) kept += ibi
        }
        return kept
    }

    /** null when there are fewer than three intervals: no variability can be stated. */
    fun metrics(ibisMs: List<Double>): HrvMetrics? {
        if (ibisMs.size < MIN_INTERVALS) return null
        val mean = ibisMs.average()
        val sdnn = sqrt(ibisMs.sumOf { (it - mean) * (it - mean) } / (ibisMs.size - 1))
        val diffs = ibisMs.zipWithNext { a, b -> b - a }
        val rmssd = sqrt(diffs.sumOf { it * it } / diffs.size)
        val pnn50 = diffs.count { abs(it) > PNN_LIMIT_MS } * PERCENT / diffs.size
        return HrvMetrics(ibisMs.size, mean, MS_PER_MINUTE / mean, sdnn, rmssd, pnn50)
    }
}

/**
 * Finds heartbeats in a pulse waveform (PPG-like samples) and returns their times, so beat-to-beat intervals can be
 * computed. Works on any sample rate high enough to resolve a beat (about 20 Hz and up); at one sample per second it
 * cannot work, which is why a 1 Hz beats-per-minute stream cannot give intervals.
 */
object BeatDetector {
    private const val NANOS_PER_SECOND = 1e9
    private const val NANOS_PER_MS = 1e6
    private const val BASELINE_SECONDS = 1.0
    private const val SMOOTH_SECONDS = 0.08
    private const val MIN_GAP_SECONDS = 0.3
    private const val THRESHOLD_FRACTION = 0.35
    private const val PERCENTILE = 0.95
    private const val MIN_SAMPLES = 8
    private const val MIN_PEAKS_TO_SCORE = 4

    /** Beat times in nanoseconds (same clock as [timesNanos]); the polarity of the waveform is chosen automatically. */
    fun beatTimes(timesNanos: LongArray, values: DoubleArray): DoubleArray {
        val usable = values.size >= MIN_SAMPLES && timesNanos.size == values.size
        val found = if (usable) detect(timesNanos, values) else emptyList()
        return DoubleArray(found.size) { found[it] }
    }

    private fun detect(timesNanos: LongArray, values: DoubleArray): List<Double> {
        val seconds = DoubleArray(values.size) { (timesNanos[it] - timesNanos[0]) / NANOS_PER_SECOND }
        val spacing = seconds.last() / (seconds.size - 1)
        val filtered = highPass(smooth(values, window(spacing, SMOOTH_SECONDS)), window(spacing, BASELINE_SECONDS))
        // A pulse peak is narrower than the stretch between beats, so the waveform is skewed towards its peaks;
        // the sign with positive skew is the beat polarity (some sensors report the inverted signal).
        val first = if (skewness(filtered) >= 0.0) 1.0 else -1.0
        val best = listOf(first, -first)
            .map { sign -> peaks(DoubleArray(filtered.size) { filtered[it] * sign }, seconds) }
            .firstOrNull { it.size >= MIN_PEAKS_TO_SCORE }
        return best.orEmpty().map { timesNanos[0] + it * NANOS_PER_SECOND }
    }

    fun intervalsMs(beatTimesNanos: DoubleArray): List<Double> =
        beatTimesNanos.toList().zipWithNext { a, b -> (b - a) / NANOS_PER_MS }

    private fun window(spacingSeconds: Double, seconds: Double): Int = maxOf(1, ceil(seconds / spacingSeconds).toInt())

    private fun smooth(x: DoubleArray, w: Int): DoubleArray = movingAverage(x, w)

    private fun highPass(x: DoubleArray, w: Int): DoubleArray {
        val base = movingAverage(x, w)
        return DoubleArray(x.size) { x[it] - base[it] }
    }

    private fun movingAverage(x: DoubleArray, w: Int): DoubleArray {
        val half = w / 2
        val prefix = DoubleArray(x.size + 1)
        for (i in x.indices) prefix[i + 1] = prefix[i] + x[i]
        return DoubleArray(x.size) {
            val a = maxOf(0, it - half)
            val b = minOf(x.size, it + half + 1)
            (prefix[b] - prefix[a]) / (b - a)
        }
    }

    /**
     * Local maxima above a fraction of the 95th percentile, at least 0.3 s apart (the larger one is kept), with
     * parabolic timing.
     */
    private fun peaks(x: DoubleArray, seconds: DoubleArray): List<Double> {
        val sorted = x.sorted()
        val threshold = THRESHOLD_FRACTION * sorted[(PERCENTILE * (sorted.size - 1)).toInt()]
        if (threshold <= 0.0) return emptyList()
        val found = mutableListOf<Int>()
        for (i in 1 until x.size - 1) {
            if (x[i] <= threshold || x[i] < x[i - 1] || x[i] <= x[i + 1]) continue
            val last = found.lastOrNull()
            if (last == null || seconds[i] - seconds[last] >= MIN_GAP_SECONDS) {
                found += i
            } else if (x[i] > x[last]) {
                found[found.size - 1] = i
            }
        }
        return found.map { refine(x, seconds, it) }
    }

    private fun refine(x: DoubleArray, seconds: DoubleArray, i: Int): Double {
        val denominator = x[i - 1] - 2 * x[i] + x[i + 1]
        if (denominator == 0.0) return seconds[i]
        val offset = 0.5 * (x[i - 1] - x[i + 1]) / denominator
        val step = if (offset >= 0) seconds[i + 1] - seconds[i] else seconds[i] - seconds[i - 1]
        return seconds[i] + offset * step
    }

    private fun skewness(x: DoubleArray): Double {
        val mean = x.average()
        val sd = sqrt(x.sumOf { (it - mean) * (it - mean) } / x.size)
        if (sd == 0.0) return 0.0
        return x.sumOf { ((it - mean) / sd).let { z -> z * z * z } } / x.size
    }
}

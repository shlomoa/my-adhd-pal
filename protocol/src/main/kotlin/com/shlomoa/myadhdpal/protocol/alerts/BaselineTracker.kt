package com.shlomoa.myadhdpal.protocol.alerts

import kotlin.math.ceil

/** The lowest median over 3 consecutive samples of one whole hour ([hour] = epoch milliseconds / one hour), in bpm. */
data class HourlyLow(val hour: Long, val bpm: Double)

/**
 * The resting baseline of `docs/plans/biometrics.md` section 2, from passive heart-rate samples:
 * - the hourly low of an hour with at least 3 samples is the lowest median over any 3 consecutive samples of that
 *   hour;
 * - the baseline is the 20th percentile (nearest rank) of the hourly lows of the last 7 days, and is known from 24
 *   lows.
 *
 * Only the hourly lows (a derived statistic) are kept and can be saved with [hourlyLows] and restored through the
 * constructor; raw samples are held in memory for the current and the previous hour only, so that batched and
 * out-of-order delivery gives the hour's real low, and are never saved or logged. Not thread-safe.
 */
class BaselineTracker(restored: List<HourlyLow> = emptyList()) {
    private val lows = sortedMapOf<Long, Double>().apply { restored.forEach { put(it.hour, it.bpm) } }
    private val recent = mutableMapOf<Long, MutableList<Reading>>()

    fun add(readings: List<Reading>) {
        if (readings.isEmpty()) return
        readings.groupBy { it.at / MILLIS_PER_HOUR }.forEach { (hour, samples) ->
            val kept = recent.getOrPut(hour) { mutableListOf() }.also { it.addAll(samples) }
            lowOf(kept)?.let { lows[hour] = it }
        }
        val newest = recent.keys.max()
        recent.keys.removeAll { it < newest - 1 }
        lows.keys.removeAll { it <= newest - WINDOW_HOURS }
    }

    /** The saved form: one value per hour with a low, oldest first. */
    fun hourlyLows(): List<HourlyLow> = lows.map { HourlyLow(it.key, it.value) }

    /** The baseline at [now] (epoch milliseconds), or null until 24 hourly lows of the last 7 days exist. */
    fun baseline(now: Long): Double? {
        val nowHour = now / MILLIS_PER_HOUR
        val inWindow = lows.filterKeys { it in (nowHour - WINDOW_HOURS + 1)..nowHour }.values.sorted()
        if (inWindow.size < MIN_LOWS) return null
        val rank = ceil(PERCENTILE * inWindow.size).toInt()
        return inWindow[rank - 1]
    }

    private fun lowOf(samples: List<Reading>): Double? {
        if (samples.size < WINDOW_SAMPLES) return null
        return samples.sortedBy { it.at }.map { it.value }.windowed(WINDOW_SAMPLES).minOf { it.sorted()[MIDDLE] }
    }

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000L
        const val WINDOW_HOURS = 7 * 24L
        const val WINDOW_SAMPLES = 3
        const val MIDDLE = 1
        const val MIN_LOWS = 24
        const val PERCENTILE = 0.2
    }
}

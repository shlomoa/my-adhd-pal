package com.shlomoa.myadhdpal.wear.debug.monitor

import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.alerts.Reading

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val SAMPLES_PER_HOUR = 3

/** [seconds] + 1 readings, one per second from [from] to [to] bpm (a linear rise), the first at [startAt]. */
fun risingSeries(from: Int, to: Int, seconds: Int, startAt: Long): List<Reading> = (0..seconds).map { second ->
    val bpm = if (seconds == 0) to.toDouble() else from + (to - from) * second.toDouble() / seconds
    Reading(Metric.HEART_RATE, bpm, startAt + second * MILLIS_PER_SECOND)
}

/**
 * Three resting samples at [bpm] in each of the [hours] hours before [endAt], so a baseline is known (24 hourly
 * lows).
 */
fun restingHistory(bpm: Int, hours: Int, endAt: Long): List<Reading> = (1..hours).flatMap { hour ->
    (0 until SAMPLES_PER_HOUR).map { index ->
        Reading(Metric.HEART_RATE, bpm.toDouble(), endAt - hour * MILLIS_PER_HOUR + index * MILLIS_PER_SECOND)
    }
}

/** "70,95,120" as values, or null for an empty or bad list. */
fun parseValues(text: String): List<Double>? {
    val values = text.split(',').map { it.trim().toDoubleOrNull() }
    return if (text.isBlank() || values.any { it == null }) null else values.filterNotNull()
}

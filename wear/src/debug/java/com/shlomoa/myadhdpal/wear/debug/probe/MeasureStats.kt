package com.shlomoa.myadhdpal.wear.debug.probe

import java.util.Locale

/**
 * One heart-rate reading delivered to the live probe: when it arrived ([elapsedMs], elapsed-realtime milliseconds) and
 * the
 * [display] state then. Counts and times only: the probe never records bpm values.
 */
data class MeasureReading(val elapsedMs: Long, val display: String) {
    fun toLine(): String = "$elapsedMs$MEASURE_SEPARATOR$display"
}

/** A change of what the sensor can deliver (acquiring, available, unavailable, off the body), by [name]. */
data class AvailabilityEvent(val elapsedMs: Long, val name: String) {
    fun toLine(): String = "$elapsedMs$MEASURE_SEPARATOR$name"
}

private const val MEASURE_SEPARATOR = "|"
private const val LINE_FIELDS = 2
private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_MINUTE = 60_000.0
private const val MILLIS_PER_HOUR = 3_600_000.0
private const val PERCENT = 100
private const val PERCENT_ROUND_UP = 99
private const val P95 = 95

/** Null for a line that is not exactly `elapsedMs|display` with a known display state. */
fun parseMeasureReading(line: String): MeasureReading? {
    val parts = line.split(MEASURE_SEPARATOR)
    val elapsed = parts.firstOrNull()?.toLongOrNull()
    return if (parts.size == LINE_FIELDS && parts[1] in DISPLAYS && elapsed != null) {
        MeasureReading(elapsed, parts[1])
    } else {
        null
    }
}

/** Null for a line that is not exactly `elapsedMs|name` with a non-empty name. */
fun parseAvailability(line: String): AvailabilityEvent? {
    val parts = line.split(MEASURE_SEPARATOR)
    val elapsed = parts.firstOrNull()?.toLongOrNull()
    return if (parts.size == LINE_FIELDS && parts[1].isNotEmpty() && elapsed != null) {
        AvailabilityEvent(elapsed, parts[1])
    } else {
        null
    }
}

/** The nearest-rank [percent] percentile of [sorted] (ascending), or null when it is empty. */
fun percentile(sorted: List<Long>, percent: Int): Long? = if (sorted.isEmpty()) {
    null
} else {
    sorted[((percent * sorted.size + PERCENT_ROUND_UP) / PERCENT - 1).coerceIn(0, sorted.size - 1)]
}

private fun gapText(gaps: List<Long>): String {
    val sorted = gaps.sorted()
    return if (sorted.isEmpty()) {
        "gapSec none"
    } else {
        "gapSec median=${median(sorted)!! / MILLIS_PER_SECOND} p95=${percentile(sorted, P95)!! / MILLIS_PER_SECOND} " +
            "max=${sorted.last() / MILLIS_PER_SECOND}"
    }
}

/**
 * The lines the checklist asks the user to copy: all readings (count, per minute over the run, gaps between readings),
 * then each display state that occurred (a gap counts for the state of the later reading), then each availability
 * change.
 */
fun measureReport(
    readings: List<MeasureReading>,
    availability: List<AvailabilityEvent>,
    startMs: Long,
    nowMs: Long
): List<String> {
    val ordered = readings.sortedBy { it.elapsedMs }
    val gaps = ordered.zipWithNext { a, b -> b.display to b.elapsedMs - a.elapsedMs }
    val duration = nowMs - startMs
    val perMinute = if (duration <=
        0
    ) {
        "none"
    } else {
        String.format(Locale.US, "%.1f", ordered.size * MILLIS_PER_MINUTE / duration)
    }
    val all = "PROBE measure screen=all readings=${ordered.size} perMin=$perMinute ${gapText(gaps.map { it.second })}"
    val perDisplay = DISPLAYS.filter { name -> ordered.any { it.display == name } }.map { name ->
        val count = ordered.count { it.display == name }
        "PROBE measure screen=$name readings=$count ${gapText(gaps.filter { it.first == name }.map { it.second })}"
    }
    val changes = availability.sortedBy { it.elapsedMs }.map {
        "PROBE measure availability ${it.name} atSec=${(it.elapsedMs - startMs) / MILLIS_PER_SECOND}"
    }
    return listOf(all) + perDisplay + changes
}

/** The battery line: percent at start and now, and the drop per hour (none without a duration or if it rose). */
fun batteryReport(startPercent: Int, nowPercent: Int, durationMs: Long): String {
    val drop = if (durationMs <= 0 || nowPercent > startPercent) {
        "none"
    } else {
        String.format(Locale.US, "%.1f", (startPercent - nowPercent) * MILLIS_PER_HOUR / durationMs)
    }
    return "PROBE measure battery start=$startPercent now=$nowPercent dropPerHour=$drop"
}

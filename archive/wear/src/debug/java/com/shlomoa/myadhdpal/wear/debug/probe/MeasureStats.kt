package com.shlomoa.myadhdpal.wear.debug.probe

import java.util.Locale

/**
 * One heart-rate reading delivered to the live probe: when it arrived ([elapsedMs], elapsed-realtime milliseconds), the
 * [display] state then and, when known, the time of the sample itself ([sampleMs], milliseconds since boot, from the
 * data point). Counts and times only: the probe never records bpm values.
 */
data class MeasureReading(val elapsedMs: Long, val display: String, val sampleMs: Long? = null) {
    fun toLine(): String = if (sampleMs == null) {
        "$elapsedMs$MEASURE_SEPARATOR$display"
    } else {
        "$elapsedMs$MEASURE_SEPARATOR$display$MEASURE_SEPARATOR$sampleMs"
    }
}

/** A change of what the sensor can deliver (acquiring, available, unavailable, off the body), by [name]. */
data class AvailabilityEvent(val elapsedMs: Long, val name: String) {
    fun toLine(): String = "$elapsedMs$MEASURE_SEPARATOR$name"
}

private const val MEASURE_SEPARATOR = "|"
private const val LINE_FIELDS = 2
private const val LINE_FIELDS_WITH_SAMPLE = 3
private const val SAMPLE_FIELD = 2
private const val MINUTE_MS = 60_000L
private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_MINUTE = 60_000.0
private const val MILLIS_PER_HOUR = 3_600_000.0
private const val PERCENT = 100
private const val PERCENT_ROUND_UP = 99
private const val P95 = 95

/** Null for a line that is not `elapsedMs|display` or `elapsedMs|display|sampleMs` with a known display state. */
fun parseMeasureReading(line: String): MeasureReading? {
    val parts = line.split(MEASURE_SEPARATOR)
    val elapsed = parts.firstOrNull()?.toLongOrNull()
    val sample = parts.getOrNull(SAMPLE_FIELD)?.toLongOrNull()
    val sampleOk = parts.size == LINE_FIELDS || (parts.size == LINE_FIELDS_WITH_SAMPLE && sample != null)
    return if (sampleOk && parts[1] in DISPLAYS && elapsed != null) MeasureReading(elapsed, parts[1], sample) else null
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

private fun seconds(ms: Long): String = String.format(Locale.US, "%.1f", ms / MILLIS_PER_SECOND.toDouble())

private fun perMinute(timesMs: List<Long>, startMs: Long): String {
    val minutes = timesMs.map { ((it - startMs) / MINUTE_MS).coerceAtLeast(0) }
    val counts = LongArray(((minutes.maxOrNull() ?: 0) + 1).toInt())
    minutes.forEach { counts[it.toInt()]++ }
    return counts.joinToString(",")
}

/**
 * The lines that tell a lost reading from a late one (issue 228): the callbacks (readings that arrived at the same
 * millisecond count as one callback), per screen state the longest gap between SAMPLE times and the delay (arrival
 * minus sample time: median, 95th percentile, longest), and the arrivals and the samples per minute of the run, so
 * that the position of every gap is visible. Readings without a sample time give only the callbacks and the arrivals.
 */
fun sampleReport(readings: List<MeasureReading>, startMs: Long): List<String> {
    val perCallback = readings.groupingBy { it.elapsedMs }.eachCount().values
    val callbacks = "PROBE measure callbacks=${perCallback.size} maxPerCallback=${perCallback.maxOrNull() ?: 0}"
    if (readings.isEmpty()) return listOf(callbacks)
    val sampled = readings.filter { it.sampleMs != null }.sortedBy { it.sampleMs }
    val states = DISPLAYS.filter { name -> sampled.any { it.display == name } }.map { name ->
        val mine = sampled.filter { it.display == name }
        val gaps = sampled.zipWithNext { a, b -> b.display to b.sampleMs!! - a.sampleMs!! }
            .filter { it.first == name }.map { it.second }
        val delays = mine.map { it.elapsedMs - it.sampleMs!! }.sorted()
        val gapMax = (gaps.maxOrNull() ?: 0) / MILLIS_PER_SECOND
        "PROBE measure samples screen=$name readings=${mine.size} sampleGapSec max=$gapMax " +
            "delaySec median=${seconds(
                median(delays)!!
            )} p95=${seconds(percentile(delays, P95)!!)} max=${seconds(delays.last())}"
    }
    val arrivals = "PROBE measure arrivalsPerMin ${perMinute(readings.map { it.elapsedMs }, startMs)}"
    val samples = if (sampled.isEmpty()) {
        emptyList()
    } else {
        listOf("PROBE measure samplesPerMin ${perMinute(sampled.map { it.sampleMs!! }, startMs)}")
    }
    return listOf(callbacks) + states + arrivals + samples
}

/** The minute of the run (0 for the first 60 s), counted from [startMs]; never below zero. */
fun minuteOfRun(startMs: Long, nowMs: Long): Int = ((nowMs - startMs) / MINUTE_MS).coerceAtLeast(0).toInt()

/**
 * The line the service writes every minute AS IT HAPPENS (not only in the report): the readings that arrived since
 * the previous line, how long since that line (a long time means the main thread or the CPU was delayed), how long
 * since the last arrival (none before the first) and the screen state now.
 */
fun liveMinuteLine(
    minute: Int,
    arrivals: Int,
    sincePrevLineMs: Long,
    sinceLastArrivalMs: Long?,
    display: String
): String {
    val last = sinceLastArrivalMs?.let { (it / MILLIS_PER_SECOND).toString() } ?: "none"
    val sincePrev = sincePrevLineMs / MILLIS_PER_SECOND
    return "PROBE measure live minute=$minute arrivals=$arrivals sincePrevLineSec=$sincePrev " +
        "sinceLastArrivalSec=$last screen=$display"
}

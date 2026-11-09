package com.shlomoa.myadhdpal.wear.debug.probe

/**
 * One delivery of heart-rate samples to the probe: when it arrived ([elapsedMs], elapsed-realtime milliseconds), how
 * many samples it held, the [display] state at that moment (on, doze for the always-on display, off, other) and, when
 * known, how old its newest sample was and how long a time its samples span. Counts and times only: the probe never
 * records bpm values.
 */
data class ProbeDelivery(
    val elapsedMs: Long,
    val samples: Int,
    val display: String,
    val newestAgeMs: Long?,
    val spanMs: Long?
) {
    fun toLine(): String = listOf(elapsedMs, samples, display, newestAgeMs ?: "", spanMs ?: "").joinToString(SEPARATOR)
}

data class ProbeSummary(
    val deliveries: Int,
    val samples: Int,
    val minGapMs: Long?,
    val medianGapMs: Long?,
    val maxGapMs: Long?,
    val medianNewestAgeMs: Long?,
    val maxNewestAgeMs: Long?
)

/** The age of the newest sample at delivery and the time the samples of one delivery span. */
data class SampleAges(val newestAgeMs: Long, val spanMs: Long)

private const val SEPARATOR = "|"
private const val FIELDS = 5
private const val MILLIS_PER_SECOND = 1000L
private const val PAIR = 2
private const val STATE_OFF = 1
private const val STATE_ON = 2
private const val STATE_DOZE = 3
private const val STATE_DOZE_SUSPEND = 4
internal val DISPLAYS = listOf("on", "doze", "off", "other")

/** The name of an `android.view.Display` state: the always-on display is doze. */
fun displayName(state: Int): String = when (state) {
    STATE_ON -> "on"
    STATE_DOZE, STATE_DOZE_SUSPEND -> "doze"
    STATE_OFF -> "off"
    else -> "other"
}

fun sampleAges(deliveryMs: Long, sampleTimesMs: List<Long>): SampleAges? = if (sampleTimesMs.isEmpty()) {
    null
} else {
    SampleAges(
        deliveryMs - sampleTimesMs.max(),
        sampleTimesMs.max() - sampleTimesMs.min()
    )
}

/** Null for a line that is not exactly `elapsedMs|samples|display|newestAgeMs|spanMs` (the last two may be empty). */
fun parseDelivery(line: String): ProbeDelivery? {
    val parts = line.split(SEPARATOR)
    if (parts.size != FIELDS || parts[2] !in DISPLAYS) return null
    val elapsed = parts[0].toLongOrNull()
    val samples = parts[1].toIntOrNull()
    val age = parts[3].toLongOrNull()
    val span = parts[4].toLongOrNull()
    val optionalOk = (parts[3].isEmpty() || age != null) && (parts[4].isEmpty() || span != null)
    return if (elapsed == null || samples == null ||
        !optionalOk
    ) {
        null
    } else {
        ProbeDelivery(elapsed, samples, parts[2], age, span)
    }
}

internal fun median(sorted: List<Long>): Long? = when {
    sorted.isEmpty() -> null
    sorted.size % PAIR == 1 -> sorted[sorted.size / PAIR]
    else -> (sorted[sorted.size / PAIR - 1] + sorted[sorted.size / PAIR]) / PAIR
}

fun summarize(deliveries: List<ProbeDelivery>): ProbeSummary {
    val gaps = deliveries.map { it.elapsedMs }.sorted().zipWithNext { a, b -> b - a }.sorted()
    val ages = deliveries.mapNotNull { it.newestAgeMs }.sorted()
    return ProbeSummary(
        deliveries.size,
        deliveries.sumOf { it.samples },
        gaps.firstOrNull(),
        median(gaps),
        gaps.lastOrNull(),
        median(ages),
        ages.lastOrNull()
    )
}

/** The report lines the checklist asks the user to copy: all deliveries, then each display state that occurred. */
fun formatReport(deliveries: List<ProbeDelivery>): List<String> {
    val groups = listOf("all" to deliveries) + DISPLAYS.map { name -> name to deliveries.filter { it.display == name } }
    return groups.filter { (name, group) -> name == "all" || group.isNotEmpty() }.map { (name, group) ->
        val s = summarize(group)
        val gaps = if (s.medianGapMs == null) {
            "none"
        } else {
            "min=${s.minGapMs!! / MILLIS_PER_SECOND} median=${s.medianGapMs / MILLIS_PER_SECOND} " +
                "max=${s.maxGapMs!! / MILLIS_PER_SECOND}"
        }
        val age = if (s.medianNewestAgeMs == null) {
            "none"
        } else {
            "median=${s.medianNewestAgeMs / MILLIS_PER_SECOND} max=${s.maxNewestAgeMs!! / MILLIS_PER_SECOND}"
        }
        "PROBE report screen=$name deliveries=${s.deliveries} samples=${s.samples} gapSec $gaps newestAgeSec $age"
    }
}

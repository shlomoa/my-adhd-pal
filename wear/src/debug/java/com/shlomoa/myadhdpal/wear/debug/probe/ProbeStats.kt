package com.shlomoa.myadhdpal.wear.debug.probe

/**
 * One delivery of heart-rate samples to the probe: when it arrived ([elapsedMs], elapsed-realtime milliseconds), how
 * many samples it held, the [display] state at that moment (on, doze for the always-on display, off, other) and, when
 * known, how old its newest sample was and how long a time its samples span. Counts and times only: the probe never
 * records bpm values.
 */
data class ProbeBatch(
    val elapsedMs: Long,
    val samples: Int,
    val display: String,
    val newestAgeMs: Long?,
    val spanMs: Long?
) {
    fun toLine(): String = listOf(elapsedMs, samples, display, newestAgeMs ?: "", spanMs ?: "").joinToString(SEPARATOR)
}

data class ProbeSummary(
    val batches: Int,
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
private val DISPLAYS = listOf("on", "doze", "off", "other")

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
fun parseBatch(line: String): ProbeBatch? {
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
        ProbeBatch(elapsed, samples, parts[2], age, span)
    }
}

private fun median(sorted: List<Long>): Long? = when {
    sorted.isEmpty() -> null
    sorted.size % PAIR == 1 -> sorted[sorted.size / PAIR]
    else -> (sorted[sorted.size / PAIR - 1] + sorted[sorted.size / PAIR]) / PAIR
}

fun summarize(batches: List<ProbeBatch>): ProbeSummary {
    val gaps = batches.map { it.elapsedMs }.sorted().zipWithNext { a, b -> b - a }.sorted()
    val ages = batches.mapNotNull { it.newestAgeMs }.sorted()
    return ProbeSummary(
        batches.size,
        batches.sumOf { it.samples },
        gaps.firstOrNull(),
        median(gaps),
        gaps.lastOrNull(),
        median(ages),
        ages.lastOrNull()
    )
}

/** The report lines the checklist asks the user to copy: all batches, then each display state that occurred. */
fun formatReport(batches: List<ProbeBatch>): List<String> {
    val groups = listOf("all" to batches) + DISPLAYS.map { name -> name to batches.filter { it.display == name } }
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
        "PROBE report screen=$name batches=${s.batches} samples=${s.samples} gapSec $gaps newestAgeSec $age"
    }
}

package com.shlomoa.myadhdpal.wear.debug.probe

/**
 * One delivery of heart-rate samples to the probe: when it arrived ([elapsedMs], elapsed-realtime milliseconds), how
 * many samples it held and whether the screen was on. Counts and times only: the probe never records bpm values.
 */
data class ProbeBatch(val elapsedMs: Long, val samples: Int, val interactive: Boolean) {
    fun toLine(): String = "$elapsedMs$SEPARATOR$samples$SEPARATOR$interactive"
}

data class ProbeSummary(
    val batches: Int,
    val samples: Int,
    val minGapMs: Long?,
    val medianGapMs: Long?,
    val maxGapMs: Long?
)

private const val SEPARATOR = "|"
private const val FIELDS = 3
private const val MILLIS_PER_SECOND = 1000L
private const val PAIR = 2

/** Null for a line that is not exactly `elapsedMs|samples|true-or-false`. */
fun parseBatch(line: String): ProbeBatch? {
    val parts = line.split(SEPARATOR)
    if (parts.size != FIELDS) return null
    val elapsed = parts[0].toLongOrNull()
    val samples = parts[1].toIntOrNull()
    val interactive = parts[2].toBooleanStrictOrNull()
    return elapsed?.let { e -> samples?.let { n -> interactive?.let { ProbeBatch(e, n, it) } } }
}

fun summarize(batches: List<ProbeBatch>): ProbeSummary {
    val gaps = batches.map { it.elapsedMs }.sorted().zipWithNext { a, b -> b - a }.sorted()
    val median = when {
        gaps.isEmpty() -> null
        gaps.size % PAIR == 1 -> gaps[gaps.size / PAIR]
        else -> (gaps[gaps.size / PAIR - 1] + gaps[gaps.size / PAIR]) / PAIR
    }
    return ProbeSummary(batches.size, batches.sumOf { it.samples }, gaps.firstOrNull(), median, gaps.lastOrNull())
}

/** The report lines the checklist asks the user to copy: all batches, then screen on and screen off when present. */
fun formatReport(batches: List<ProbeBatch>): List<String> {
    val groups = listOf(
        "all" to batches,
        "on" to batches.filter { it.interactive },
        "off" to batches.filterNot { it.interactive }
    )
    return groups.filter { (name, group) -> name == "all" || group.isNotEmpty() }.map { (name, group) ->
        val s = summarize(group)
        val gaps = if (s.medianGapMs == null) {
            "none"
        } else {
            "min=${s.minGapMs!! / MILLIS_PER_SECOND} median=${s.medianGapMs / MILLIS_PER_SECOND} " +
                "max=${s.maxGapMs!! / MILLIS_PER_SECOND}"
        }
        "PROBE report screen=$name batches=${s.batches} samples=${s.samples} gapSec $gaps"
    }
}

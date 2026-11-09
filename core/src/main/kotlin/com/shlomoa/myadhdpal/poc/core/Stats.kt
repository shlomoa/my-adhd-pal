package com.shlomoa.myadhdpal.poc.core

data class Summary(val count: Int, val min: Long, val p50: Long, val p95: Long, val max: Long)

object Stats {
    private const val P50 = 50.0
    private const val P95 = 95.0
    private const val PERCENT = 100.0
    private const val NANOS_PER_MS = 1_000_000.0

    /** Nearest-rank percentiles; null for an empty list. */
    fun summarize(values: List<Long>): Summary? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        fun rank(p: Double): Long =
            sorted[(Math.ceil(p / PERCENT * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
        return Summary(sorted.size, sorted.first(), rank(P50), rank(P95), sorted.last())
    }

    /** Differences between consecutive values (the gaps between arrivals). */
    fun gaps(sortedTimes: List<Long>): List<Long> = sortedTimes.zipWithNext { a, b -> b - a }

    fun millis(nanos: Long): String = "%.1f".format(java.util.Locale.ROOT, nanos / NANOS_PER_MS)

    fun line(name: String, s: Summary?): String = if (s == null) {
        "$name: no data"
    } else {
        "$name: n=${s.count} min=${millis(
            s.min
        )}ms p50=${millis(s.p50)}ms p95=${millis(s.p95)}ms max=${millis(s.max)}ms"
    }
}

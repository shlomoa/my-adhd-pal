package com.shlomoa.myadhdpal.poc.core

import kotlin.math.sqrt

/** One reading as the phone's live indicator sees it. Times are on the phone's elapsed-realtime clock. */
data class LiveReading(
    val sourceNanos: Long,
    val arrivalNanos: Long,
    val bpm: Float,
    val accuracy: Int,
    val latencyMs: Double?,
    val path: String,
    val route: String,
    val screen: String
)

enum class LiveStatus { LIVE, STALE, NO_SIGNAL }

data class LiveSnapshot(
    val status: LiveStatus,
    val bpm: Float?,
    val ageMs: Long?,
    val accuracy: Int?,
    val route: String,
    val screen: String,
    val latencyBtMs: Double?,
    val latencyDlMs: Double?,
    val readings: Int,
    val minBpm: Float?,
    val avgBpm: Double?,
    val maxBpm: Float?,
    val trendBpmPerMin: Double?,
    val meanIntervalMs: Double?,
    val variabilityProxyMs: Double?,
    val windowSeconds: Double
)

/**
 * Rolling statistics of the pulse readings for the phone's live indicator. The same reading arrives on two paths
 * (Bluetooth and the Data Layer); it is counted once, but the latency of each path is kept. Values of 0 or above 250
 * beats per minute (an unworn watch reports 0) are not counted as pulse.
 */
class LiveStats(private val windowNanos: Long = DEFAULT_WINDOW_NANOS) {
    private val series = ArrayDeque<LiveReading>()
    private val seen = HashSet<Pair<String, Long>>()
    private val latency = HashMap<String, Pair<Long, Double>>() // path -> arrival, latency

    @Synchronized
    fun add(r: LiveReading) {
        if (r.latencyMs != null) latency[r.path] = r.arrivalNanos to r.latencyMs
        if (!seen.add(r.route to r.sourceNanos)) return
        series.addLast(r)
        trim(r.arrivalNanos)
    }

    private fun trim(nowNanos: Long) {
        while (series.isNotEmpty() && nowNanos - series.first().arrivalNanos > windowNanos) {
            val old = series.removeFirst()
            seen.remove(old.route to old.sourceNanos)
        }
    }

    @Synchronized
    fun snapshot(nowNanos: Long): LiveSnapshot {
        trim(nowNanos)
        val valid = series.filter { plausible(it.bpm) }
        val last = series.lastOrNull()
        val age = last?.let { (nowNanos - it.arrivalNanos) / NANOS_PER_MS }
        val status = when {
            age == null || !plausible(last.bpm) -> LiveStatus.NO_SIGNAL
            age <= LIVE_MS -> LiveStatus.LIVE
            age <= STALE_MS -> LiveStatus.STALE
            else -> LiveStatus.NO_SIGNAL
        }
        val avg = if (valid.isEmpty()) null else valid.sumOf { it.bpm.toDouble() } / valid.size
        val intervals = valid.map { MS_PER_MINUTE / it.bpm }
        return LiveSnapshot(
            status = status,
            bpm = last?.bpm?.takeIf { plausible(it) },
            ageMs = age,
            accuracy = last?.accuracy,
            route = last?.route.orEmpty(),
            screen = last?.screen.orEmpty(),
            latencyBtMs = fresh("bt", nowNanos),
            latencyDlMs = fresh("dl", nowNanos),
            readings = valid.size,
            minBpm = valid.minOfOrNull { it.bpm },
            avgBpm = avg,
            maxBpm = valid.maxOfOrNull { it.bpm },
            trendBpmPerMin = trend(valid),
            meanIntervalMs = avg?.let { MS_PER_MINUTE / it },
            variabilityProxyMs = if (intervals.size >= MIN_FOR_PROXY) sdnn(intervals) else null,
            windowSeconds = windowNanos / NANOS_PER_S
        )
    }

    /** The pulse readings of the window for the graph, with their age at [nowNanos]. */
    @Synchronized
    fun graphPoints(nowNanos: Long): List<GraphPoint> {
        trim(nowNanos)
        return series.filter {
            plausible(it.bpm)
        }.map { GraphPoint((nowNanos - it.arrivalNanos) / NANOS_PER_S, it.bpm) }
    }

    private fun plausible(bpm: Float): Boolean = bpm > 0f && bpm <= MAX_BPM

    private fun fresh(path: String, nowNanos: Long): Double? =
        latency[path]?.takeIf { nowNanos - it.first <= STALE_MS * NANOS_PER_MS }?.second

    private fun trend(valid: List<LiveReading>): Double? {
        val minutes = valid.map { (it.arrivalNanos - valid.first().arrivalNanos) / NANOS_PER_S / SECONDS_PER_MINUTE }
        val enough = valid.size >= MIN_FOR_TREND && minutes.last() * SECONDS_PER_MINUTE >= MIN_TREND_SECONDS
        val meanT = minutes.average()
        val denominator = minutes.sumOf { (it - meanT) * (it - meanT) }
        return if (!enough || denominator == 0.0) {
            null
        } else {
            val bpm = valid.map { it.bpm.toDouble() }
            val meanY = bpm.average()
            minutes.indices.sumOf { (minutes[it] - meanT) * (bpm[it] - meanY) } / denominator
        }
    }

    private fun sdnn(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    companion object {
        const val DEFAULT_WINDOW_NANOS = 60_000_000_000L
        private const val NANOS_PER_MS = 1_000_000L
        private const val NANOS_PER_S = 1e9
        private const val SECONDS_PER_MINUTE = 60.0
        private const val MS_PER_MINUTE = 60_000.0
        private const val MAX_BPM = 250f
        private const val LIVE_MS = 3_000L
        private const val STALE_MS = 10_000L
        private const val MIN_FOR_PROXY = 10
        private const val MIN_FOR_TREND = 5
        private const val MIN_TREND_SECONDS = 10.0
    }
}

/** Text of the live indicator, shared by the phone screen and the log the test script reads. */
object LiveFormat {
    fun headline(s: LiveSnapshot): String = s.bpm?.let { "${it.toInt()} bpm" } ?: "-- bpm"

    fun statusLabel(s: LiveSnapshot): String = when (s.status) {
        LiveStatus.LIVE -> "LIVE"
        LiveStatus.STALE -> "STALE"
        LiveStatus.NO_SIGNAL -> "NO SIGNAL"
    }

    fun accuracyLabel(accuracy: Int?): String = when (accuracy) {
        ACCURACY_HIGH -> "high"
        ACCURACY_MEDIUM -> "medium"
        ACCURACY_LOW -> "low"
        ACCURACY_UNRELIABLE -> "unreliable"
        else -> "not reported"
    }

    fun trendArrow(perMinute: Double?): String = when {
        perMinute == null -> "-"
        perMinute > TREND_LIMIT -> "rising"
        perMinute < -TREND_LIMIT -> "falling"
        else -> "steady"
    }

    fun details(s: LiveSnapshot): List<String> = buildList {
        add(
            "last reading: ${s.ageMs?.let {
                "%.1f s ago".format(java.util.Locale.ROOT, it / MS_PER_SECOND)
            } ?: "none"}"
        )
        add("signal accuracy: ${accuracyLabel(s.accuracy)}")
        add("route ${s.route.ifEmpty { "-" }}, watch screen ${s.screen.ifEmpty { "-" }}")
        add("latency: Bluetooth ${ms(s.latencyBtMs)}, Data Layer ${ms(s.latencyDlMs)}")
        val w = "last ${s.windowSeconds.toInt()} s (${s.readings} readings)"
        if (s.avgBpm == null) {
            add("$w: no pulse")
        } else {
            add(
                "$w: min ${s.minBpm?.toInt()} / avg ${"%.0f".format(
                    java.util.Locale.ROOT,
                    s.avgBpm
                )} / max ${s.maxBpm?.toInt()} bpm"
            )
            add(
                "trend: ${trendArrow(s.trendBpmPerMin)}${s.trendBpmPerMin?.let {
                    " (%+.1f bpm/min)".format(java.util.Locale.ROOT, it)
                } ?: ""}"
            )
            add(
                "mean beat interval from bpm: ${"%.0f".format(
                    java.util.Locale.ROOT,
                    s.meanIntervalMs
                )} ms (an average, not true beat-to-beat)"
            )
            add(
                "variability proxy: ${s.variabilityProxyMs?.let {
                    "%.0f ms".format(java.util.Locale.ROOT, it)
                } ?: "not enough readings"} (spread of the bpm series, not HRV)"
            )
        }
    }

    /** One line for the log, e.g. `LIVE 72 bpm age 0.4 s acc high min 68 avg 72 max 77 bt 85 ms dl 120 ms`. */
    fun logLine(s: LiveSnapshot): String = "${statusLabel(s)} ${headline(s)} age ${s.ageMs?.let {
        "%.1f s".format(java.util.Locale.ROOT, it / MS_PER_SECOND)
    } ?: "-"} " +
        "acc ${accuracyLabel(s.accuracy)} min ${s.minBpm?.toInt() ?: "-"} avg ${s.avgBpm?.let {
            "%.0f".format(java.util.Locale.ROOT, it)
        } ?: "-"} " +
        "max ${s.maxBpm?.toInt() ?: "-"} bt ${ms(s.latencyBtMs)} dl ${ms(s.latencyDlMs)}"

    private fun ms(v: Double?): String = v?.let { "%.0f ms".format(java.util.Locale.ROOT, it) } ?: "-"

    private const val TREND_LIMIT = 2.0
    private const val ACCURACY_HIGH = 3
    private const val ACCURACY_MEDIUM = 2
    private const val ACCURACY_LOW = 1
    private const val ACCURACY_UNRELIABLE = 0
    private const val MS_PER_SECOND = 1000.0
}

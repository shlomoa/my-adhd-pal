package com.shlomoa.myadhdpal.poc.core

import kotlin.math.ceil
import kotlin.math.floor

/** One pulse reading for the graph: how long ago it arrived, and its value. */
data class GraphPoint(val ageSeconds: Double, val bpm: Float)

data class XY(val x: Float, val y: Float)

/**
 * What to draw: the vertical range, the grid lines, and the line segments in pixel coordinates (origin top left).
 * A new segment starts wherever readings are more than a few seconds apart, so a gap is shown as a gap, not as a
 * misleading straight line.
 */
data class GraphLayout(val lowBpm: Int, val highBpm: Int, val ticks: List<Int>, val segments: List<List<XY>>)

object PulseGraph {
    private const val DEFAULT_LOW = 50
    private const val DEFAULT_HIGH = 100
    private const val PADDING = 5
    private const val ROUND_TO = 10
    private const val WIDE_RANGE = 60
    private const val COARSE_STEP = 20

    /**
     * The range always covers 50..100 bpm so the picture does not jump about, and grows by 5 bpm beyond the data,
     * rounded to tens, when the pulse goes outside it.
     */
    fun layout(
        points: List<GraphPoint>,
        windowSeconds: Double,
        width: Float,
        height: Float,
        maxGapSeconds: Double = 3.0
    ): GraphLayout {
        val sorted = points.filter { it.ageSeconds in 0.0..windowSeconds }.sortedByDescending { it.ageSeconds }
        val low = minOf(DEFAULT_LOW, floorTo(sorted.minOfOrNull { it.bpm }?.minus(PADDING) ?: DEFAULT_LOW.toFloat()))
        val high = maxOf(DEFAULT_HIGH, ceilTo(sorted.maxOfOrNull { it.bpm }?.plus(PADDING) ?: DEFAULT_HIGH.toFloat()))
        val step = if (high - low > WIDE_RANGE) COARSE_STEP else ROUND_TO
        val ticks = (low..high step step).toList()
        val segments = mutableListOf<MutableList<XY>>()
        var previousAge = Double.NaN
        for (p in sorted) {
            val xy = XY(
                (width * (1.0 - p.ageSeconds / windowSeconds)).toFloat(),
                (height * (1.0 - (p.bpm - low) / (high - low).toDouble())).toFloat()
            )
            if (segments.isEmpty() ||
                previousAge - p.ageSeconds > maxGapSeconds
            ) {
                segments += mutableListOf(xy)
            } else {
                segments.last() += xy
            }
            previousAge = p.ageSeconds
        }
        return GraphLayout(low, high, ticks, segments)
    }

    private fun floorTo(v: Float): Int = (floor(v / ROUND_TO) * ROUND_TO).toInt()

    private fun ceilTo(v: Float): Int = (ceil(v / ROUND_TO) * ROUND_TO).toInt()
}

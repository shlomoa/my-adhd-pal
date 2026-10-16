package com.shlomoa.myadhdpal.protocol.alerts

import com.shlomoa.myadhdpal.protocol.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The baseline of `docs/plans/biometrics.md` section 2: hourly low = lowest median over 3 consecutive samples of an
 * hour (at least 3 samples), baseline = 20th percentile (nearest rank) of the hourly lows of the last 7 days, known
 * from 24 hourly lows. Times are epoch milliseconds; hours are whole hours since the epoch.
 */
class BaselineTrackerTest {
    private val hour = 3_600_000L
    private val start = 400_000L * hour

    private fun sample(bpm: Double, hourIndex: Int, secondInHour: Int) =
        Reading(Metric.HEART_RATE, bpm, start + hourIndex * hour + secondInHour * 1000L)

    /** Three equal samples in the given hour: an hourly low of exactly [bpm]. */
    private fun flatHour(bpm: Double, hourIndex: Int) = (0..2).map { sample(bpm, hourIndex, it * 60) }

    private fun now(hours: Int) = start + hours * hour

    @Test
    fun `TC-W-086 the hourly low is the lowest median over 3 consecutive samples`() {
        val tracker = BaselineTracker()
        // windows: (80,60,62) -> 62, (60,62,61) -> 61, (62,61,90) -> 62: the lowest median is 61
        tracker.add(listOf(80.0, 60.0, 62.0, 61.0, 90.0).mapIndexed { i, bpm -> sample(bpm, 0, i * 60) })
        assertEquals(listOf(HourlyLow(start / hour, 61.0)), tracker.hourlyLows())
    }

    @Test
    fun `TC-W-087 an hour with fewer than 3 samples gives no hourly low`() {
        val tracker = BaselineTracker()
        tracker.add(listOf(sample(60.0, 0, 0), sample(61.0, 0, 60)))
        assertEquals(emptyList<HourlyLow>(), tracker.hourlyLows())
    }

    @Test
    fun `TC-W-088 the baseline is unknown with 23 hourly lows and known with 24`() {
        val tracker = BaselineTracker()
        tracker.add((0 until 23).flatMap { flatHour(60.0, it) })
        assertNull(tracker.baseline(now(24)))
        tracker.add(flatHour(60.0, 23))
        assertEquals(60.0, tracker.baseline(now(24))!!, 0.0)
    }

    @Test
    fun `TC-W-089 the baseline is the 20th percentile by nearest rank of the hourly lows`() {
        val tracker = BaselineTracker()
        // 24 lows: 1..24 shifted by 50 -> 51..74; nearest rank = ceil(0.2 * 24) = 5 -> the 5th smallest = 55
        tracker.add((0 until 24).flatMap { flatHour(51.0 + it, it) })
        assertEquals(55.0, tracker.baseline(now(24))!!, 0.0)
    }

    @Test
    fun `TC-W-120 only the hourly lows of the last 7 days count`() {
        val tracker = BaselineTracker()
        tracker.add((0 until 24).flatMap { flatHour(60.0, it) })
        tracker.add((100 until 124).flatMap { flatHour(90.0, it) })
        // both blocks are inside the 7 days: 24 lows of 60 and 24 of 90, the 20th percentile (rank 10) is 60
        assertEquals(60.0, tracker.baseline(now(124))!!, 0.0)
        // 192 hours in, the first block (hours 0 to 23) is older than 7 days: only the 90s remain
        assertEquals(90.0, tracker.baseline(now(192))!!, 0.0)
    }

    @Test
    fun `TC-W-121 batched and out-of-order samples give the same hourly low as sorted ones`() {
        val series = listOf(80.0, 60.0, 62.0, 61.0, 90.0).mapIndexed { i, bpm -> sample(bpm, 0, i * 60) }
        val sorted = BaselineTracker().apply { add(series) }
        val shuffled = BaselineTracker().apply {
            add(listOf(series[3], series[0]))
            add(listOf(series[4], series[2], series[1]))
        }
        assertEquals(sorted.hourlyLows(), shuffled.hourlyLows())
    }

    @Test
    fun `TC-W-122 the hourly lows can be saved and restored`() {
        val tracker = BaselineTracker()
        tracker.add((0 until 24).flatMap { flatHour(51.0 + it, it) })
        val restored = BaselineTracker(tracker.hourlyLows())
        assertEquals(tracker.baseline(now(24)), restored.baseline(now(24)))
        assertEquals(tracker.hourlyLows(), restored.hourlyLows())
    }
}

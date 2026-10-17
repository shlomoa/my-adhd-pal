package com.shlomoa.myadhdpal.wear.debug.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The live-measurement probe (biometrics.md section 11.5): counts and times only, never bpm values. */
class MeasureStatsTest {
    private fun reading(second: Long, display: String) = MeasureReading(second * 1000, display)

    @Test
    fun `TC-W-144 the measure report gives readings per minute and the gaps between readings, overall and per screen state`() {
        val readings = listOf(
            reading(1, "on"),
            reading(2, "on"),
            reading(3, "on"),
            reading(4, "on"),
            reading(10, "doze"),
            reading(11, "doze"),
            reading(100, "off")
        )
        assertEquals(
            listOf(
                "PROBE measure screen=all readings=7 perMin=0.7 gapSec median=1 p95=89 max=89",
                "PROBE measure screen=on readings=4 gapSec median=1 p95=1 max=1",
                "PROBE measure screen=doze readings=2 gapSec median=3 p95=6 max=6",
                "PROBE measure screen=off readings=1 gapSec median=89 p95=89 max=89"
            ),
            measureReport(readings, emptyList(), startMs = 0, nowMs = 600_000)
        )
    }

    @Test
    fun `TC-W-145 no readings give a line with zero readings and no gaps, and no duration gives no rate`() {
        assertEquals(
            listOf("PROBE measure screen=all readings=0 perMin=0.0 gapSec none"),
            measureReport(emptyList(), emptyList(), startMs = 0, nowMs = 60_000)
        )
        assertEquals(
            listOf("PROBE measure screen=all readings=1 perMin=none gapSec none"),
            measureReport(listOf(reading(1, "on")), emptyList(), startMs = 5_000, nowMs = 5_000)
        )
    }

    @Test
    fun `TC-W-146 availability changes are written and read back, listed in time order, and a malformed line is skipped`() {
        val change = AvailabilityEvent(30_000, "ACQUIRING")
        assertEquals("30000|ACQUIRING", change.toLine())
        assertEquals(change, parseAvailability("30000|ACQUIRING"))
        for (bad in listOf("30000|", "x|AVAILABLE", "30000|A|B", "30000")) assertNull(bad, parseAvailability(bad))
        assertEquals(MeasureReading(5000, "doze"), parseMeasureReading("5000|doze"))
        for (bad in listOf("5000", "x|on", "5000|weird", "5000|on|1")) assertNull(bad, parseMeasureReading(bad))
        assertEquals(
            listOf(
                "PROBE measure screen=all readings=0 perMin=0.0 gapSec none",
                "PROBE measure availability ACQUIRING atSec=25",
                "PROBE measure availability AVAILABLE atSec=45"
            ),
            measureReport(
                emptyList(),
                listOf(AvailabilityEvent(45_000, "AVAILABLE"), AvailabilityEvent(25_000, "ACQUIRING")),
                startMs = 0,
                nowMs = 60_000
            )
        )
    }

    @Test
    fun `TC-W-147 the battery report gives the drop per hour, and none when there is no duration or the level rose`() {
        assertEquals(
            "PROBE measure battery start=80 now=77 dropPerHour=6.0",
            batteryReport(startPercent = 80, nowPercent = 77, durationMs = 1_800_000)
        )
        assertEquals(
            "PROBE measure battery start=80 now=80 dropPerHour=none",
            batteryReport(startPercent = 80, nowPercent = 80, durationMs = 0)
        )
        assertEquals(
            "PROBE measure battery start=80 now=81 dropPerHour=none",
            batteryReport(startPercent = 80, nowPercent = 81, durationMs = 600_000)
        )
    }

    @Test
    fun `TC-W-148 the 95th percentile is the nearest rank of the sorted values`() {
        assertEquals(19L, percentile((1L..20L).toList(), 95))
        assertEquals(5L, percentile(listOf(5L), 95))
        assertEquals(89L, percentile(listOf(1L, 1L, 1L, 1L, 6L, 89L), 95))
        assertNull(percentile(emptyList(), 95))
    }
}

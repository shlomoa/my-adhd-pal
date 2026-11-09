package com.shlomoa.myadhdpal.wear.debug.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The arrival-time probe of step 6.4 (biometrics.md section 6): counts and times only, never bpm values. */
class ProbeStatsTest {
    private fun delivery(
        second: Long,
        samples: Int,
        display: String = "on",
        ageSec: Long? = null,
        spanSec: Long? = null
    ) = ProbeDelivery(second * 1000, samples, display, ageSec?.times(1000), spanSec?.times(1000))

    @Test
    fun `TC-W-132 the summary counts deliveries and samples and reports the gaps between deliveries`() {
        val summary = summarize(listOf(delivery(0, 5), delivery(60, 5), delivery(120, 5), delivery(300, 15)))
        assertEquals(4, summary.deliveries)
        assertEquals(30, summary.samples)
        assertEquals(60_000L, summary.minGapMs)
        assertEquals(60_000L, summary.medianGapMs)
        assertEquals(180_000L, summary.maxGapMs)
    }

    @Test
    fun `TC-W-133 an empty or single-delivery list has no gaps`() {
        val none = summarize(emptyList())
        assertEquals(0, none.deliveries)
        assertNull(none.medianGapMs)
        val one = summarize(listOf(delivery(0, 3)))
        assertEquals(1, one.deliveries)
        assertNull(one.minGapMs)
    }

    @Test
    fun `TC-W-134 the report has one line for all deliveries and one per display state, in seconds, with no values`() {
        val report = formatReport(
            listOf(
                delivery(0, 5, "on", ageSec = 2),
                delivery(60, 5, "on", ageSec = 4),
                delivery(600, 20, "doze", ageSec = 30),
                delivery(1200, 20, "off", ageSec = 50)
            )
        )
        assertEquals(
            listOf(
                "PROBE report screen=all deliveries=4 samples=50 gapSec min=60 median=540 max=600 " +
                    "newestAgeSec median=17 max=50",
                "PROBE report screen=on deliveries=2 samples=10 gapSec min=60 median=60 max=60 " +
                    "newestAgeSec median=3 max=4",
                "PROBE report screen=doze deliveries=1 samples=20 gapSec none newestAgeSec median=30 max=30",
                "PROBE report screen=off deliveries=1 samples=20 gapSec none newestAgeSec median=50 max=50"
            ),
            report
        )
        assertEquals(
            listOf("PROBE report screen=all deliveries=0 samples=0 gapSec none newestAgeSec none"),
            formatReport(emptyList())
        )
    }

    @Test
    fun `TC-W-135 a delivery is written as one line and read back, and a malformed line is skipped`() {
        val original = delivery(42, 7, "doze", ageSec = 9, spanSec = 300)
        assertEquals(original, parseDelivery(original.toLine()))
        val unknown = delivery(43, 2, "off")
        assertEquals(unknown, parseDelivery(unknown.toLine()))
        assertNull(parseDelivery("not a record"))
        assertNull(parseDelivery("1|x|on||"))
        assertNull(parseDelivery("42000|7|true"))
        assertNull(parseDelivery(""))
    }

    @Test
    fun `TC-W-142 a display state is named on, doze, off or other`() {
        assertEquals("off", displayName(1))
        assertEquals("on", displayName(2))
        assertEquals("doze", displayName(3))
        assertEquals("doze", displayName(4))
        assertEquals("other", displayName(0))
        assertEquals("other", displayName(6))
    }

    @Test
    fun `TC-W-143 the age of the newest sample and the span of a delivery come from the sample times`() {
        val ages = sampleAges(deliveryMs = 10_000_000L, sampleTimesMs = listOf(9_000_000L, 9_600_000L, 9_700_000L))
        assertEquals(300_000L, ages?.newestAgeMs)
        assertEquals(700_000L, ages?.spanMs)
        assertNull(sampleAges(10_000L, emptyList()))
    }
}

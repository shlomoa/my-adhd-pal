package com.shlomoa.myadhdpal.wear.debug.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The arrival-time probe of step 6.4 (biometrics.md section 6): counts and times only, never bpm values. */
class ProbeStatsTest {
    private fun batch(second: Long, samples: Int, interactive: Boolean = true) =
        ProbeBatch(second * 1000, samples, interactive)

    @Test
    fun `TC-W-132 the summary counts batches and samples and reports the gaps between batches`() {
        val summary = summarize(listOf(batch(0, 5), batch(60, 5), batch(120, 5), batch(300, 15)))
        assertEquals(4, summary.batches)
        assertEquals(30, summary.samples)
        assertEquals(60_000L, summary.minGapMs)
        assertEquals(60_000L, summary.medianGapMs)
        assertEquals(180_000L, summary.maxGapMs)
    }

    @Test
    fun `TC-W-133 an empty or single batch list has no gaps`() {
        val none = summarize(emptyList())
        assertEquals(0, none.batches)
        assertNull(none.medianGapMs)
        val one = summarize(listOf(batch(0, 3)))
        assertEquals(1, one.batches)
        assertNull(one.minGapMs)
    }

    @Test
    fun `TC-W-134 the report has one line for all batches and one per screen state, with seconds and no values`() {
        val report = formatReport(
            listOf(batch(0, 5), batch(60, 5), batch(600, 20, interactive = false), batch(1200, 20, interactive = false))
        )
        assertEquals(
            listOf(
                "PROBE report screen=all batches=4 samples=50 gapSec min=60 median=540 max=600",
                "PROBE report screen=on batches=2 samples=10 gapSec min=60 median=60 max=60",
                "PROBE report screen=off batches=2 samples=40 gapSec min=600 median=600 max=600"
            ),
            report
        )
        assertEquals(listOf("PROBE report screen=all batches=0 samples=0 gapSec none"), formatReport(emptyList()))
    }

    @Test
    fun `TC-W-135 a batch is written as one line and read back, and a malformed line is skipped`() {
        val original = batch(42, 7, interactive = false)
        assertEquals(original, parseBatch(original.toLine()))
        assertNull(parseBatch("not a record"))
        assertNull(parseBatch("1|x|true"))
        assertNull(parseBatch(""))
    }
}

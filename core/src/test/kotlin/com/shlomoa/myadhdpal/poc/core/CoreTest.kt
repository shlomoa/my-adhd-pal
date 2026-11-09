package com.shlomoa.myadhdpal.poc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreTest {
    @Test
    fun sampleRoundTripsThroughWire() {
        val s = Sample("r2-on", "r2", 7, 71.5f, 1_000, 2_000, -5, "doze")
        assertEquals(s, Wire.decodeSample(Wire.encode(s)))
    }

    @Test
    fun badSampleIsRejected() {
        assertNull(Wire.decodeSample("x,y".toByteArray()))
        assertNull(Wire.decodeSample("a,b,notanumber,1,2,3,4,on,3".toByteArray()))
    }

    @Test
    fun pingPongRoundTrip() {
        assertEquals(42L, Wire.decodePing(Wire.encodePing(42)))
        assertEquals(42L to 99L, Wire.decodePong(Wire.encodePong(42, 99)))
        assertNull(Wire.decodePong("1".toByteArray()))
    }

    @Test
    fun offsetIsPhoneMinusWatchAtMidpoint() {
        // Watch clock = phone clock - 1000. Symmetric 20 ns trip each way, so the midpoint is exact.
        val e = Exchange(t0 = 100, phoneNanos = 1_120, t3 = 140)
        assertEquals(40, e.roundTrip)
        assertEquals(1_000, e.offset)
    }

    @Test
    fun bestOffsetUsesSmallestRoundTrip() {
        val slow = Exchange(0, 5_000, 1_000) // noisy
        val fast = Exchange(0, 1_010, 20) // offset 1000
        assertEquals(1_000, ClockOffset.best(listOf(slow, fast)))
        assertNull(ClockOffset.best(emptyList()))
    }

    @Test
    fun percentilesAreNearestRank() {
        val s = Stats.summarize((1L..100L).toList())!!
        assertEquals(50, s.p50)
        assertEquals(95, s.p95)
        assertEquals(100, s.max)
        assertEquals(1, s.min)
        assertNull(Stats.summarize(emptyList()))
    }

    @Test
    fun gapsAreConsecutiveDifferences() {
        assertEquals(listOf(5L, 10L), Stats.gaps(listOf(0, 5, 15)))
    }
}

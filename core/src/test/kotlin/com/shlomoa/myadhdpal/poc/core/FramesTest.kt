package com.shlomoa.myadhdpal.poc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FramesTest {
    private val sample = Sample("r2-on", "r2", 3, 64.0f, 10, 20, -7, "on")

    @Test
    fun framesRoundTrip() {
        val buffer = LineBuffer()
        val bytes = Frames.sample(sample) + Frames.ping(42) + Frames.pong(42, 99)
        val frames = buffer.feed(bytes)
        assertEquals(
            listOf(Frame.SampleFrame(sample), Frame.PingFrame(42), Frame.PongFrame(42, 99)),
            frames
        )
    }

    @Test
    fun framesSurviveAnySplit() {
        val bytes = Frames.sample(sample) + Frames.ping(7)
        for (cut in 1 until bytes.size) {
            val buffer = LineBuffer()
            val frames = buffer.feed(bytes.copyOfRange(0, cut)) + buffer.feed(bytes.copyOfRange(cut, bytes.size))
            assertEquals(listOf(Frame.SampleFrame(sample), Frame.PingFrame(7)), frames, "cut at $cut")
        }
    }

    @Test
    fun byteAtATimeWorks() {
        val buffer = LineBuffer()
        val frames = Frames.pong(1, 2).flatMap { buffer.feed(byteArrayOf(it)) }
        assertEquals(listOf<Frame>(Frame.PongFrame(1, 2)), frames)
    }

    @Test
    fun badLinesAreReportedNotFatal() {
        val buffer = LineBuffer()
        val frames = buffer.feed("garbage\nS,x\nP,notanumber\n".toByteArray() + Frames.ping(5))
        assertEquals(3, frames.count { it is Frame.BadFrame })
        assertEquals(Frame.PingFrame(5), frames.last())
    }

    @Test
    fun runawayLineIsDropped() {
        val buffer = LineBuffer(maxLine = 16)
        val frames = buffer.feed(ByteArray(100) { 'a'.code.toByte() })
        assertEquals(1, frames.size)
        assertTrue(frames.single() is Frame.BadFrame)
        // The rest of the runaway line is skipped up to its newline; the next frame is intact.
        assertEquals(listOf<Frame>(Frame.PingFrame(1)), buffer.feed("\n".toByteArray() + Frames.ping(1)))
    }

    @Test
    fun trackerKeepsBestExchangeAndStartsWithoutOffset() {
        val tracker = OffsetTracker(keep = 2)
        assertEquals(Sample.NO_OFFSET, tracker.offset())
        tracker.add(Exchange(0, 5_000, 1_000)) // noisy
        tracker.add(Exchange(0, 1_010, 20)) // offset 1000
        assertEquals(1_000, tracker.offset())
        tracker.add(Exchange(0, 2_000, 4_000)) // pushes out the noisy one, not the good one
        tracker.add(Exchange(0, 3_000, 6_000)) // pushes out the good one (keep = 2)
        assertEquals(0, 2_000 - (0 + 4_000) / 2)
        tracker.clear()
        assertEquals(Sample.NO_OFFSET, tracker.offset())
    }
}

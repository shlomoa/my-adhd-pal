package com.shlomoa.myadhdpal.wear.debug

import com.shlomoa.myadhdpal.wear.log.ItemHandler
import com.shlomoa.myadhdpal.wear.log.LogLevel
import com.shlomoa.myadhdpal.wear.log.Logger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLoggingTest {
    @After
    fun reset() {
        Logger.sink = null
    }

    @Test
    fun `TC-W-056 the debug logger records entries in order and keeps only the last capacity`() {
        val sink = RingBufferLogSink(capacity = 2)
        Logger.sink = sink
        Logger.d("t", "one")
        Logger.i("t", "two")
        Logger.w("t", "three")
        assertEquals(listOf("two", "three"), sink.snapshot().map { it.message })
        assertEquals(LogLevel.Warn, sink.snapshot().last().level)
    }

    @Test
    fun `TC-W-056 without an installed sink nothing is recorded`() {
        val sink = RingBufferLogSink()
        Logger.d("t", "ignored")
        assertTrue(sink.snapshot().isEmpty())
    }

    @Test
    fun `TC-W-056 an injected message reaches the item handler with its path and body`() {
        val received = mutableListOf<Pair<String, String>>()
        forwardInjected("/schedule", """{"v":1}""", ItemHandler { path, body -> received += path to body })
        assertEquals(listOf("/schedule" to """{"v":1}"""), received)
    }

    @Test
    fun `TC-W-056 an injected message without path or body is dropped and logged`() {
        val sink = RingBufferLogSink()
        Logger.sink = sink
        val received = mutableListOf<String>()
        val handler = ItemHandler { path, _ -> received += path }
        forwardInjected(null, "{}", handler)
        forwardInjected("/schedule", null, handler)
        assertTrue(received.isEmpty())
        assertEquals(2, sink.snapshot().count { it.level == LogLevel.Warn })
    }
}

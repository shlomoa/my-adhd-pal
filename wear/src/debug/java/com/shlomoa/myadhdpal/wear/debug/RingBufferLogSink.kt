package com.shlomoa.myadhdpal.wear.debug

import com.shlomoa.myadhdpal.wear.log.LogLevel
import com.shlomoa.myadhdpal.wear.log.LogSink

data class LogEntry(val level: LogLevel, val tag: String, val message: String)

/** Keeps the last [capacity] entries for the debug log view and mirrors them to logcat. */
class RingBufferLogSink(private val capacity: Int = DEFAULT_CAPACITY, private val mirror: LogSink? = null) : LogSink {
    private val entries = ArrayDeque<LogEntry>()

    override fun log(level: LogLevel, tag: String, message: String) {
        synchronized(entries) {
            if (entries.size == capacity) entries.removeFirst()
            entries.addLast(LogEntry(level, tag, message))
        }
        mirror?.log(level, tag, message)
    }

    fun snapshot(): List<LogEntry> = synchronized(entries) { entries.toList() }

    private companion object {
        const val DEFAULT_CAPACITY = 500
    }
}

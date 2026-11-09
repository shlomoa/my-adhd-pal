package com.shlomoa.myadhdpal.poc.core

/**
 * Keeps the most recent ping/pong exchanges of one path and the clock offset (phone minus watch) of the
 * exchange with the shortest round trip. Each path (Data Layer, Bluetooth) has its own tracker, because the two
 * paths can have different one-way delays.
 */
class OffsetTracker(private val keep: Int = DEFAULT_KEEP) {
    private val exchanges = ArrayDeque<Exchange>()

    @Synchronized
    fun add(exchange: Exchange) {
        exchanges.addLast(exchange)
        while (exchanges.size > keep) exchanges.removeFirst()
    }

    /** The current offset, or [Sample.NO_OFFSET] before any usable exchange. */
    @Synchronized
    fun offset(): Long = ClockOffset.best(exchanges.toList()) ?: Sample.NO_OFFSET

    @Synchronized
    fun clear() = exchanges.clear()

    private companion object {
        const val DEFAULT_KEEP = 10
    }
}

package com.shlomoa.myadhdpal.poc.core

/**
 * One ping/pong exchange: the watch sent at [t0], the phone answered at [phoneNanos] on its own clock, the watch
 * received the answer at [t3]. t0 and t3 are watch times, [phoneNanos] is phone time.
 */
data class Exchange(val t0: Long, val phoneNanos: Long, val t3: Long) {
    val roundTrip: Long get() = t3 - t0

    /** Phone clock minus watch clock, assuming the phone answered halfway through the round trip. */
    val offset: Long get() = phoneNanos - (t0 + t3) / 2
}

object ClockOffset {
    /** The offset of the exchange with the smallest round trip (least queueing error), or null if none. */
    fun best(exchanges: List<Exchange>): Long? = exchanges.filter { it.roundTrip >= 0 }.minByOrNull { it.roundTrip }?.offset
}

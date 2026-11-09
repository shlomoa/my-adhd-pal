package com.shlomoa.myadhdpal.poc.core

/** Message paths on the Wearable link (watch to phone unless noted). */
object Paths {
    const val SAMPLE = "/poc/sample"
    const val PING = "/poc/ping"
    const val PONG = "/poc/pong" // phone to watch
}

/**
 * One pulse reading as it leaves the watch.
 *
 * All times are nanoseconds on the watch's elapsed-realtime clock. [sourceNanos] is when the sensor recorded the
 * value, [sendNanos] when the watch handed it to the link. [offsetNanos] is the watch's latest estimate of
 * (phone clock minus watch clock), or [NO_OFFSET] before the first ping/pong exchange completed.
 */
data class Sample(
    val label: String,
    val route: String,
    val seq: Long,
    val bpm: Float,
    val sourceNanos: Long,
    val sendNanos: Long,
    val offsetNanos: Long,
    /** Display state of the watch when the reading arrived: `on`, `doze` (always-on/dim), `off` or `?`. */
    val screen: String = "?",
    /** Sensor accuracy: 3 high, 2 medium, 1 low, 0 unreliable, -1 not reported (Health Services). */
    val accuracy: Int = -1
) {
    companion object {
        const val NO_OFFSET = Long.MIN_VALUE
    }
}

/** Compact text wire format; fields never contain the separator. */
object Wire {
    private const val SEP = ','
    private const val SAMPLE_FIELDS = 9
    private const val IDX_SEQ = 2
    private const val IDX_BPM = 3
    private const val IDX_SOURCE = 4
    private const val IDX_SEND = 5
    private const val IDX_OFFSET = 6
    private const val IDX_SCREEN = 7
    private const val IDX_ACCURACY = 8

    fun encode(s: Sample): ByteArray =
        listOf(s.label, s.route, s.seq, s.bpm, s.sourceNanos, s.sendNanos, s.offsetNanos, s.screen, s.accuracy)
            .joinToString(SEP.toString())
            .toByteArray(Charsets.UTF_8)

    fun decodeSample(bytes: ByteArray): Sample? {
        val f = String(bytes, Charsets.UTF_8).split(SEP)
        if (f.size != SAMPLE_FIELDS) return null
        return runCatching {
            Sample(
                f[0],
                f[1],
                f[IDX_SEQ].toLong(),
                f[IDX_BPM].toFloat(),
                f[IDX_SOURCE].toLong(),
                f[IDX_SEND].toLong(),
                f[IDX_OFFSET].toLong(),
                f[IDX_SCREEN],
                f[IDX_ACCURACY].toInt()
            )
        }.getOrNull()
    }

    fun encodePing(watchNanos: Long): ByteArray = watchNanos.toString().toByteArray(Charsets.UTF_8)

    fun decodePing(bytes: ByteArray): Long? = String(bytes, Charsets.UTF_8).toLongOrNull()

    /** Reply to a ping: the original watch time and the phone's clock when it answered. */
    fun encodePong(pingWatchNanos: Long, phoneNanos: Long): ByteArray =
        "$pingWatchNanos$SEP$phoneNanos".toByteArray(Charsets.UTF_8)

    fun decodePong(bytes: ByteArray): Pair<Long, Long>? {
        val f = String(bytes, Charsets.UTF_8).split(SEP)
        val a = f.getOrNull(0)?.toLongOrNull()
        val b = f.getOrNull(1)?.toLongOrNull()
        return if (f.size == 2 && a != null && b != null) a to b else null
    }
}

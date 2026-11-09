package com.shlomoa.myadhdpal.poc.core

/** Fixed identifiers of the direct Bluetooth (RFCOMM) path between the watch and the phone. */
object BtService {
    const val NAME = "PocPulse"
    const val UUID_STRING = "6f3c1e0a-5b7d-4c2e-9a41-0d8e7c5b2a10"
}

/** One line of the Bluetooth byte stream, decoded. */
sealed interface Frame {
    data class SampleFrame(val sample: Sample) : Frame

    /** The watch asks the phone for its clock; [watchNanos] is the watch time of sending. */
    data class PingFrame(val watchNanos: Long) : Frame

    /** The phone's answer to a ping. */
    data class PongFrame(val pingWatchNanos: Long, val phoneNanos: Long) : Frame

    /** A line that is not a valid frame; kept so the caller can count it. */
    data class BadFrame(val line: String) : Frame
}

/** Newline-framed text protocol over a byte stream: `S,<sample>`, `P,<t0>`, `O,<t0>,<phoneNanos>`. */
object Frames {
    private const val NL = '\n'.code.toByte()
    private const val SAMPLE = "S,"
    private const val PING = "P,"
    private const val PONG = "O,"

    fun sample(s: Sample): ByteArray = line(SAMPLE + String(Wire.encode(s), Charsets.UTF_8))

    fun ping(watchNanos: Long): ByteArray = line(PING + watchNanos)

    fun pong(pingWatchNanos: Long, phoneNanos: Long): ByteArray = line("$PONG$pingWatchNanos,$phoneNanos")

    private fun line(text: String): ByteArray = (text + NL.toInt().toChar()).toByteArray(Charsets.UTF_8)

    fun parse(line: String): Frame {
        val body = line.drop(2)
        val frame: Frame? =
            when {
                line.startsWith(SAMPLE) -> Wire.decodeSample(body.toByteArray(Charsets.UTF_8))?.let {
                    Frame.SampleFrame(it)
                }

                line.startsWith(PING) -> body.toLongOrNull()?.let { Frame.PingFrame(it) }

                line.startsWith(PONG) -> Wire.decodePong(body.toByteArray(Charsets.UTF_8))?.let {
                    Frame.PongFrame(it.first, it.second)
                }

                else -> null
            }
        return frame ?: Frame.BadFrame(line.take(MAX_BAD_LINE))
    }

    private const val MAX_BAD_LINE = 80
}

/** Collects bytes as they arrive (in any split) and returns the frames completed so far. */
class LineBuffer(private val maxLine: Int = DEFAULT_MAX_LINE) {
    private val pending = StringBuilder()
    private var discarding = false

    fun feed(bytes: ByteArray, length: Int = bytes.size): List<Frame> {
        val frames = mutableListOf<Frame>()
        for (i in 0 until length) {
            val c = bytes[i].toInt().toChar()
            when {
                c == '\n' -> {
                    // A line that overflowed was already reported; its tail up to here is thrown away.
                    if (!discarding && pending.isNotEmpty()) frames += Frames.parse(pending.toString())
                    pending.clear()
                    discarding = false
                }

                discarding -> Unit

                pending.length < maxLine -> pending.append(c)

                else -> {
                    pending.clear()
                    discarding = true
                    frames += Frame.BadFrame("line too long")
                }
            }
        }
        return frames
    }

    private companion object {
        const val DEFAULT_MAX_LINE = 512
    }
}

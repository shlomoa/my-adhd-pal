package com.shlomoa.myadhdpal.wear.reminder

/** A vibration waveform: [timingsMs] starts with a pause, [amplitudes] (0 to 255) pairs with each timing. */
data class HapticPattern(val timingsMs: List<Long>, val amplitudes: List<Int>)

/** [count] pulses of [onMs] at [amplitude], separated by [gapMs] of silence, after an initial zero-length pause. */
private fun pulses(count: Int, onMs: Long, gapMs: Long, amplitude: Int): HapticPattern {
    val timings = mutableListOf(0L)
    val amplitudes = mutableListOf(0)
    repeat(count) { index ->
        if (index > 0) {
            timings += gapMs
            amplitudes += 0
        }
        timings += onMs
        amplitudes += amplitude
    }
    return HapticPattern(timings, amplitudes)
}

private const val FIRM = 255
private const val SOFT = 90
private const val LIGHT = 180

/** The haptic catalogue (plan.md section 2): how each kind of nudge feels. The feel itself is checked on the watch. */
enum class Haptic(val pattern: HapticPattern) {
    /** A short single tap for a routine reminder. */
    TICK(pulses(count = 1, onMs = 40, gapMs = 0, amplitude = LIGHT)),

    /** Three firm pulses for something that needs attention now. */
    WARNING(pulses(count = 3, onMs = 200, gapMs = 120, amplitude = FIRM)),

    /** Slow, soft swells that set a breathing pace. */
    CALM_BREATH(pulses(count = 3, onMs = 900, gapMs = 600, amplitude = SOFT))
}

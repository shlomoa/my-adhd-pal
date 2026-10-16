package com.shlomoa.myadhdpal.phone.alert

/** What the user can switch off. Both default to on (user decision, 2026-10-15). */
data class AlertPrefs(val soundOn: Boolean = true, val flashOn: Boolean = true)

/**
 * A vibration waveform: [timings] alternate off, on, off, on, ... and start with a delay; [amplitudes] have the
 * same size (0 for off, 255 for on) or are null on a vibrator without amplitude control, which can only be on or off.
 */
data class VibrationPlan(val timings: List<Long>, val amplitudes: List<Int>?)

/** The screen alternates two colours once per [cycleMillis]. At most 3 cycles per second, so 334 ms or more. */
data class FlashPlan(val cycleMillis: Long)

data class AlertPlan(
    val level: Int,
    val vibration: VibrationPlan,
    val flash: FlashPlan?,
    val sound: Boolean,
    val durationMillis: Long
)

/**
 * The pure part of the phone alert (`docs/phone-alert.md`): what a level means in vibration, flashing and sound.
 * Level 1 vibrates about 15 s in bursts; level 2, the escalation, about 30 s with no pause over 0.3 s. Anything
 * else is level 1.
 */
object AlertPlanner {
    private const val MAX_AMPLITUDE = 255
    private const val LEVEL_2 = 2

    private const val LEVEL_1_BURST = 3_000L
    private const val LEVEL_1_PAUSE = 500L
    private const val LEVEL_1_BURSTS = 4
    private const val LEVEL_1_LAST_BURST = 1_000L
    private const val LEVEL_2_BURST = 2_000L
    private const val LEVEL_2_PAUSE = 300L
    private const val LEVEL_2_BURSTS = 13

    private const val LEVEL_1_FLASH_MILLIS = 1_000L

    /** 2.5 flashes per second, under the 3 per second limit. */
    private const val LEVEL_2_FLASH_MILLIS = 400L

    fun plan(level: Int, prefs: AlertPrefs, hasAmplitudeControl: Boolean): AlertPlan {
        val escalated = level == LEVEL_2
        val timings = if (escalated) {
            listOf(0L) + List(LEVEL_2_BURSTS) { listOf(LEVEL_2_BURST, LEVEL_2_PAUSE) }.flatten()
        } else {
            listOf(0L) + List(LEVEL_1_BURSTS) { listOf(LEVEL_1_BURST, LEVEL_1_PAUSE) }.flatten() + LEVEL_1_LAST_BURST
        }
        val amplitudes = if (hasAmplitudeControl) {
            timings.indices.map { if (it % 2 == 1) MAX_AMPLITUDE else 0 }
        } else {
            null
        }
        return AlertPlan(
            level = if (escalated) LEVEL_2 else 1,
            vibration = VibrationPlan(timings, amplitudes),
            flash = if (prefs.flashOn) {
                FlashPlan(
                    if (escalated) LEVEL_2_FLASH_MILLIS else LEVEL_1_FLASH_MILLIS
                )
            } else {
                null
            },
            sound = prefs.soundOn,
            durationMillis = timings.sum()
        )
    }
}

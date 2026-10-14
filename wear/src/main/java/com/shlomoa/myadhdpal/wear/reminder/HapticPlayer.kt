package com.shlomoa.myadhdpal.wear.reminder

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/** Plays the haptic catalogue on the watch's vibrator (needs the VIBRATE permission). */
class HapticPlayer(context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun play(haptic: Haptic) {
        val pattern = haptic.pattern
        vibrator.vibrate(
            VibrationEffect.createWaveform(pattern.timingsMs.toLongArray(), pattern.amplitudes.toIntArray(), NO_REPEAT)
        )
    }

    private companion object {
        const val NO_REPEAT = -1
    }
}

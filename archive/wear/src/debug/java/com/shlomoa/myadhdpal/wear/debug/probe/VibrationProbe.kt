package com.shlomoa.myadhdpal.wear.debug.probe

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Debug only: the strongest vibration the app can ask for, to feel under each setting of the checklist (normal, Do Not
 * Disturb, Bedtime, Theater mode, each vibration-intensity setting). Alarm usage is the best an ordinary app can do.
 */
object VibrationProbe {
    private const val ON_MS = 800L
    private const val OFF_MS = 150L
    private const val MAX_AMPLITUDE = 255
    private val timings = longArrayOf(0, ON_MS, OFF_MS, ON_MS, OFF_MS, ON_MS)
    private val amplitudes = intArrayOf(0, MAX_AMPLITUDE, 0, MAX_AMPLITUDE, 0, MAX_AMPLITUDE)

    fun strong(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        ProbeLog.note("vibrate amplitudeControl=${vibrator.hasAmplitudeControl()}")
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(timings, NO_REPEAT)
        }
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        vibrator.vibrate(effect, attributes)
    }

    private const val NO_REPEAT = -1
}

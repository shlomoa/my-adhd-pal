package com.shlomoa.myadhdpal.wear.debug.probe

import android.os.SystemClock
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType

/**
 * Debug only. Registered by [ProbeReceiver]; the system starts it when Health Services delivers heart-rate samples,
 * also when the app is not running. It records when a delivery arrived, how many samples it held, the display state
 * (on, doze for the always-on display, off), how old its newest sample was and the time its samples span, and never
 * the bpm values.
 */
class ProbeListenerService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val points = dataPoints.getData(DataType.HEART_RATE_BPM)
        val now = SystemClock.elapsedRealtime()
        val display = MeasureLog.displayNow(this)
        val ages = sampleAges(now, points.map { it.timeDurationFromBoot.toMillis() })
        val batch = ProbeBatch(now, points.size, display, ages?.newestAgeMs, ages?.spanMs)
        ProbeLog.record(this, batch)
        ProbeLog.note(
            "batch samples=${points.size} display=$display newestAgeMs=${ages?.newestAgeMs} spanMs=${ages?.spanMs}"
        )
    }
}

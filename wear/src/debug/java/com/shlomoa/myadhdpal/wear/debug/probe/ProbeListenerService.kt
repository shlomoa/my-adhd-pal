package com.shlomoa.myadhdpal.wear.debug.probe

import android.os.PowerManager
import android.os.SystemClock
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType

/**
 * Debug only. Registered by [ProbeReceiver]; the system starts it when Health Services delivers heart-rate samples,
 * also when the app is not running. It records when a delivery arrived, how many samples it held and whether the screen
 * was on, and never the bpm values.
 */
class ProbeListenerService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val samples = dataPoints.getData(DataType.HEART_RATE_BPM).size
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val batch = ProbeBatch(SystemClock.elapsedRealtime(), samples, interactive)
        ProbeLog.record(this, batch)
        ProbeLog.note("batch samples=$samples screenOn=$interactive")
    }
}

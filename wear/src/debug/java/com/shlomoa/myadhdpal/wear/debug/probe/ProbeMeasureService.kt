package com.shlomoa.myadhdpal.wear.debug.probe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType

/**
 * Debug only. The live route of `docs/plans/biometrics.md` section 11: a foreground service of type `health` with an
 * ongoing notification that registers a Health Services measure callback for heart rate. Started by
 * [ProbeMeasureActivity] (the app must be visible to start it) and stopped with `measure-stop`. It records when each
 * reading arrived, the display state, and each availability change, never the bpm values.
 */
class ProbeMeasureService : Service() {
    private val client by lazy { HealthServices.getClient(this).measureClient }

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            val name = availability.toString().replace("|", "/")
            MeasureLog.recordAvailability(
                this@ProbeMeasureService,
                AvailabilityEvent(SystemClock.elapsedRealtime(), name)
            )
            ProbeLog.note("measure availability $name")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val now = SystemClock.elapsedRealtime()
            val display = MeasureLog.displayNow(this@ProbeMeasureService)
            data.getData(DataType.HEART_RATE_BPM).forEach { point ->
                MeasureLog.record(
                    this@ProbeMeasureService,
                    MeasureReading(now, display, point.timeDurationFromBoot.toMillis())
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Probe measure", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Probe: measuring heart rate")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        MeasureLog.begin(this, SystemClock.elapsedRealtime(), MeasureLog.batteryPercent(this))
        client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        ProbeLog.note("measure start registered")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        ProbeLog.note("measure stopped")
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "probe_measure"
        const val NOTIFICATION_ID = 9004
    }
}

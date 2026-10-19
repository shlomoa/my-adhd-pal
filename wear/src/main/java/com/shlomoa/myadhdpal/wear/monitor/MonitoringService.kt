package com.shlomoa.myadhdpal.wear.monitor

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
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.alerts.Reading
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.log.Logger

/**
 * The live route of `docs/plans/biometrics.md` section 11 as a product service: a foreground service of type `health`
 * with an ongoing notification that registers a Health Services measure callback for heart rate and hands every
 * reading to [MonitorHub.feed]. With the screen on the callback delivers about once per second; with the display dim
 * or off the readings arrive in bursts of hundreds up to about 10 minutes late (experiment E2), so the stress
 * response is only as timely as that. Started by [MonitoringActivity], which is visible (a health service cannot be
 * started from the background).
 */
class MonitoringService : Service() {
    private val hub get() = (application as WatchApplication).graph.monitorHub
    private val client by lazy { HealthServices.getClient(this).measureClient }
    private var registered = false

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Logger.i(TAG, "availability $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val bootEpoch = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val readings = data.getData(DataType.HEART_RATE_BPM).map {
                Reading(Metric.HEART_RATE, it.value, bootEpoch + it.timeDurationFromBoot.toMillis())
            }
            hub.feed(readings)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Monitoring", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Monitoring your heart rate")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!registered) {
            registered = runCatching { client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback) }
                .onFailure { Logger.w(TAG, "measure callback not registered: ${it.javaClass.simpleName}") }
                .isSuccess
            Logger.i("DEMO", "monitor started registered=$registered")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (registered) client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        registered = false
        Logger.i("DEMO", "monitor stopped")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Monitoring"
        const val CHANNEL = "monitoring"
        const val NOTIFICATION_ID = 4001
    }
}

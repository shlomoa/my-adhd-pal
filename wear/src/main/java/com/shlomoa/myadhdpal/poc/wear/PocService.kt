package com.shlomoa.myadhdpal.poc.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import com.shlomoa.myadhdpal.poc.core.Paths
import com.shlomoa.myadhdpal.poc.core.Sample
import com.shlomoa.myadhdpal.poc.core.Stats
import com.shlomoa.myadhdpal.poc.core.Wire

/**
 * The watch side of the POC: one foreground (health) service that reads the pulse by one of two routes and sends
 * every reading to the phone.
 *
 *  - route `r1`: Health Services `MeasureClient` (baseline, what the previous implementation used)
 *  - route `r2`: `SensorManager` wake-up `TYPE_HEART_RATE` sensor, with a partial wake lock
 *
 * Commands arrive as intent extras from [ControlActivity]: `cmd` = start | stop | wakelock | status.
 * Everything measured is written to files (see [PocFiles]); the service itself decides nothing about pass or fail.
 */
@Suppress("TooManyFunctions") // one small service keeps the POC readable in a single file
class PocService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private var running = false
    private var route = ""
    private var label = ""
    private var rateUs = SensorManager.SENSOR_DELAY_FASTEST
    private var batchUs = 0
    private var wakeUpSensor = false
    private var count = 0L
    private var lastSourceNanos = 0L
    private var lastArrivalNanos = 0L
    private var error = ""
    private val sensorLatencies = mutableListOf<Long>()
    private val arrivals = mutableListOf<Long>()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val arrival = SystemClock.elapsedRealtimeNanos()
            onReading(event.values[0], event.timestamp, arrival, event.accuracy)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val measureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) = Unit

        override fun onDataReceived(data: DataPointContainer) {
            val arrival = SystemClock.elapsedRealtimeNanos()
            for (point in data.getData(DataType.HEART_RATE_BPM)) {
                onReading(point.value.toFloat(), point.timeDurationFromBoot.toNanos(), arrival, -1)
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            Link.send(this@PocService, Paths.PING, Wire.encodePing(SystemClock.elapsedRealtimeNanos()))
            writeStatus()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground service started with startForegroundService must call startForeground every time.
        enterForeground()
        when (intent?.getStringExtra("cmd")) {
            "start" -> start(intent)
            "stop" -> stop()
            "wakelock" -> setWakeLock(intent.getBooleanExtra("hold", true))
            else -> Unit // "status" or unknown: just rewrite the status file
        }
        writeStatus()
        if (!running) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running) stop()
        super.onDestroy()
    }

    private fun enterForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "POC", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Pulse POC")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            error = "startForeground: ${e.javaClass.simpleName} ${e.message}"
        }
    }

    private fun start(intent: Intent) {
        if (running) stop()
        route = intent.getStringExtra("route") ?: "r2"
        label = intent.getStringExtra("label") ?: route
        rateUs = intent.getIntExtra("rate_us", SensorManager.SENSOR_DELAY_FASTEST)
        batchUs = intent.getIntExtra("batch_us", 0)
        count = 0
        error = ""
        sensorLatencies.clear()
        arrivals.clear()
        Link.reset()
        Link.refreshNode(this)
        setWakeLock(intent.getBooleanExtra("hold", true))
        val registered = when (route) {
            "r1" -> registerMeasure()
            else -> registerSensor()
        }
        running = registered
        if (running) handler.post(ticker)
    }

    private fun registerSensor(): Boolean {
        val manager = getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_HEART_RATE, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (sensor == null) {
            error = "no TYPE_HEART_RATE sensor"
            return false
        }
        wakeUpSensor = sensor.isWakeUpSensor
        return try {
            val ok = manager.registerListener(sensorListener, sensor, rateUs, batchUs)
            if (!ok) error = "registerListener returned false"
            ok
        } catch (e: SecurityException) {
            error = "registerListener: ${e.javaClass.simpleName} ${e.message}"
            false
        }
    }

    private fun registerMeasure(): Boolean {
        wakeUpSensor = false
        return try {
            HealthServices.getClient(
                this
            ).measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, measureCallback)
            true
        } catch (e: SecurityException) {
            error = "registerMeasureCallback: ${e.javaClass.simpleName} ${e.message}"
            false
        }
    }

    private fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(ticker)
        if (route == "r1") {
            HealthServices.getClient(this).measureClient.unregisterMeasureCallbackAsync(
                DataType.HEART_RATE_BPM,
                measureCallback
            )
        } else {
            getSystemService(SensorManager::class.java).unregisterListener(sensorListener)
        }
        writeSummary()
        setWakeLock(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun setWakeLock(hold: Boolean) {
        if (hold && wakeLock == null) {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "poc:pulse").also { it.acquire() }
        } else if (!hold) {
            wakeLock?.release()
            wakeLock = null
        }
    }

    private fun onReading(bpm: Float, sourceNanos: Long, arrivalNanos: Long, accuracy: Int) {
        if (!running) return
        count++
        lastSourceNanos = sourceNanos
        lastArrivalNanos = arrivalNanos
        sensorLatencies += arrivalNanos - sourceNanos
        arrivals += arrivalNanos
        PocFiles.append(this, "watch-$label.csv", "$label,$route,$count,$bpm,$accuracy,$sourceNanos,$arrivalNanos")
        val sample = Sample(label, route, count, bpm, sourceNanos, SystemClock.elapsedRealtimeNanos(), Link.offsetNanos)
        Link.send(this, Paths.SAMPLE, Wire.encode(sample))
    }

    private fun writeSummary() {
        val text = buildString {
            appendLine("label=$label route=$route rateUs=$rateUs batchUs=$batchUs wakeUpSensor=$wakeUpSensor")
            appendLine("readings=$count error=${error.ifEmpty { "none" }}")
            appendLine(Stats.line("sensor_to_app (recorded -> watch app)", Stats.summarize(sensorLatencies)))
            appendLine(Stats.line("gap_between_arrivals", Stats.summarize(Stats.gaps(arrivals))))
        }
        PocFiles.write(this, "watch-summary-$label.txt", text)
    }

    private fun writeStatus() {
        val now = SystemClock.elapsedRealtimeNanos()
        val sinceLast = if (lastArrivalNanos == 0L) -1 else (now - lastArrivalNanos) / NANOS_PER_MS
        PocFiles.write(
            this,
            "status.txt",
            "running=$running route=$route label=$label count=$count msSinceLastReading=$sinceLast " +
                "wakeLockHeld=${wakeLock?.isHeld == true} wakeUpSensor=$wakeUpSensor rateUs=$rateUs batchUs=$batchUs " +
                "offsetNanos=${Link.offsetNanos} phoneNodeFound=${Link.nodeFound} error=${error.ifEmpty { "none" }}\n"
        )
    }

    private companion object {
        const val CHANNEL = "poc"
        const val NOTIFICATION_ID = 5001
        const val TICK_MS = 1000L
        const val NANOS_PER_MS = 1_000_000L
    }
}

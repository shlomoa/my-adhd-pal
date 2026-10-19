package com.shlomoa.myadhdpal.wear.debug.probe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode as ReportingMode
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate

/**
 * Debug only. The live route of `docs/plans/biometrics.md` section 11: a foreground service of type `health` with an
 * ongoing notification that registers a Health Services measure callback for heart rate. Started by
 * [ProbeMeasureActivity] (the app must be visible to start it) and stopped with `measure-stop`. It records when each
 * reading arrived, the display state, and each availability change, never the bpm values.
 */
class ProbeMeasureService : Service() {
    private val client by lazy { HealthServices.getClient(this).measureClient }
    private val handler = Handler(Looper.getMainLooper())
    private var startMs = 0L
    private var lastLineMs = 0L
    private var lastArrivalMs: Long? = null
    private var arrivals = 0
    private var mode = ProbeMode.MEASURE
    private var wakeLock: PowerManager.WakeLock? = null
    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }

    /** Every minute, as it happens: the live line, so the log has a record that does not depend on the final report. */
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            ProbeLog.note(
                liveMinuteLine(
                    minute = minuteOfRun(startMs, now),
                    arrivals = arrivals,
                    sincePrevLineMs = now - lastLineMs,
                    sinceLastArrivalMs = lastArrivalMs?.let { now - it },
                    display = MeasureLog.displayNow(this@ProbeMeasureService)
                )
            )
            arrivals = 0
            lastLineMs = now
            handler.postDelayed(this, LIVE_LINE_MS)
        }
    }

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
            arrivals += data.getData(DataType.HEART_RATE_BPM).size
            lastArrivalMs = now
            data.getData(DataType.HEART_RATE_BPM).forEach { point ->
                MeasureLog.record(
                    this@ProbeMeasureService,
                    MeasureReading(now, display, point.timeDurationFromBoot.toMillis())
                )
            }
        }
    }

    /** Every variant records the same way: arrival time, display state and sample time, never the value. */
    private fun recordPoints(points: List<androidx.health.services.client.data.SampleDataPoint<Double>>) {
        val now = SystemClock.elapsedRealtime()
        val display = MeasureLog.displayNow(this)
        arrivals += points.size
        lastArrivalMs = now
        points.forEach { MeasureLog.record(this, MeasureReading(now, display, it.timeDurationFromBoot.toMillis())) }
    }

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onRegistered() = ProbeLog.note("measure exercise callback registered")

        override fun onRegistrationFailed(throwable: Throwable) =
            ProbeLog.note("measure exercise start failed: ${throwable.javaClass.simpleName}")

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) =
            recordPoints(update.latestMetrics.getData(DataType.HEART_RATE_BPM))

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onAvailabilityChanged(
            dataType: androidx.health.services.client.data.DataType<*, *>,
            availability: Availability
        ) {
            ProbeLog.note("measure availability ${availability.toString().replace("|", "/")}")
        }
    }

    private fun startExercise() {
        val builder = ExerciseConfig.builder(ExerciseType.WORKOUT)
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(false)
        if (mode.fiveSeconds) builder.setBatchingModeOverrides(setOf(ReportingMode.HEART_RATE_5_SECONDS))
        exerciseClient.setUpdateCallback(exerciseCallback)
        val future = exerciseClient.startExerciseAsync(builder.build())
        future.addListener({
            runCatching { future.get() }
                .onSuccess { ProbeLog.note("measure start registered") }
                .onFailure { ProbeLog.note("measure exercise start failed: ${it.javaClass.simpleName}") }
        }, mainExecutor)
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
        startMs = SystemClock.elapsedRealtime()
        lastLineMs = startMs
        lastArrivalMs = null
        arrivals = 0
        MeasureLog.begin(this, startMs, MeasureLog.batteryPercent(this))
        mode = probeModeOf(intent?.getStringExtra(EXTRA_MODE))
        ProbeLog.note(mode.logLine())
        if (mode.wakeLock) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myadhdpal:probe")
                .also { it.acquire(WAKE_LOCK_MAX_MS) }
        }
        if (mode.exercise) {
            startExercise()
        } else {
            client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            ProbeLog.note("measure start registered")
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, LIVE_LINE_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (mode.exercise) {
            exerciseClient.endExerciseAsync()
            exerciseClient.clearUpdateCallbackAsync(exerciseCallback)
        } else {
            client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        ProbeLog.note("measure stopped")
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        private const val WAKE_LOCK_MAX_MS = 30 * 60_000L
        private const val CHANNEL = "probe_measure"
        private const val NOTIFICATION_ID = 9004
        private const val LIVE_LINE_MS = 60_000L
    }
}

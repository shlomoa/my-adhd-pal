package com.shlomoa.myadhdpal.wear.debug.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import java.util.concurrent.Executor

/**
 * Debug only, driven from the computer: `adb shell am broadcast -n
 * com.shlomoa.myadhdpal/.wear.debug.probe.ProbeReceiver --es cmd <command>` with one of capabilities, permissions,
 * start, stop, report, clear, vibrate, notify-alarm, alarm-clock, or measure-capabilities, -stop, -report,
 * -clear (the live heart-rate route starts from ProbeMeasureActivity, not from here). Results are in logcat with
 * the tag PROBE (`adb logcat -d -s PROBE:I`). See `docs/test-reports/watch-probe-checklist.md`.
 */
class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val client = HealthServices.getClient(context).passiveMonitoringClient
        val executor = Executor { it.run() }
        val command = intent.getStringExtra(EXTRA_CMD)
        if (command?.startsWith(MEASURE_PREFIX) == true) return measure(context, command, executor)
        when (command) {
            "capabilities" -> client.getCapabilitiesAsync().let { future ->
                future.addListener({
                    runCatching { future.get() }
                        .onSuccess {
                            ProbeLog.note(
                                "capabilities heartRatePassive=" +
                                    (DataType.HEART_RATE_BPM in it.supportedDataTypesPassiveMonitoring)
                            )
                        }
                        .onFailure { ProbeLog.note("capabilities failed: $it") }
                }, executor)
            }

            "permissions" -> permissionReport(required()) { granted(context, it) }.forEach(ProbeLog::note)

            "start" -> startPassive(context, client, executor)

            "stop" -> {
                val future = client.clearPassiveListenerServiceAsync()
                future.addListener({
                    runCatching { future.get() }
                        .onSuccess { ProbeLog.note("stop cleared") }
                        .onFailure { ProbeLog.note("stop failed: $it") }
                }, executor)
            }

            "report" -> formatReport(ProbeLog.deliveries(context)).forEach(ProbeLog::note)

            "clear" -> ProbeLog.clear(context).also { ProbeLog.note("cleared") }

            "vibrate" -> VibrationProbe.strong(context)

            "notify-alarm" -> ProbeAlarmRoutes.notifyAlarm(context)

            "alarm-clock" -> ProbeAlarmRoutes.scheduleAlarmClock(context)

            else -> ProbeLog.note(
                "unknown command; use capabilities, permissions, start, stop, report, clear, vibrate, " +
                    "notify-alarm, alarm-clock or measure-capabilities, -stop, -report, -clear"
            )
        }
    }

    private fun startPassive(context: Context, client: PassiveMonitoringClient, executor: Executor) {
        if (missingPermissions(required()) { granted(context, it) }.isNotEmpty()) {
            permissionReport(required()) { granted(context, it) }.forEach(ProbeLog::note)
            ProbeLog.note("start not done: grant the missing permissions first (checklist step 1)")
        } else {
            val config = PassiveListenerConfig.builder().setDataTypes(setOf(DataType.HEART_RATE_BPM)).build()
            val future = client.setPassiveListenerServiceAsync(ProbeListenerService::class.java, config)
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { ProbeLog.note("start registered") }
                    .onFailure { ProbeLog.note("start failed: $it") }
            }, executor)
        }
    }

    private fun measure(context: Context, command: String, executor: Executor) {
        when (command) {
            "measure-capabilities" -> HealthServices.getClient(
                context
            ).measureClient.getCapabilitiesAsync().let { future ->
                future.addListener({
                    runCatching { future.get() }
                        .onSuccess {
                            ProbeLog.note(
                                "measure capabilities heartRateMeasure=" +
                                    (DataType.HEART_RATE_BPM in it.supportedDataTypesMeasure)
                            )
                        }
                        .onFailure { ProbeLog.note("measure capabilities failed: $it") }
                }, executor)
            }

            "measure-stop" -> context.stopService(Intent(context, ProbeMeasureService::class.java))

            "measure-report" -> measureLines(context).forEach(ProbeLog::note)

            "measure-clear" -> MeasureLog.clear(context).also { ProbeLog.note("measure cleared") }

            else -> ProbeLog.note("unknown measure command: $command")
        }
    }

    private fun measureLines(context: Context): List<String> {
        val start =
            MeasureLog.start(context) ?: return listOf("PROBE measure no run started (open ProbeMeasureActivity)")
        val now = SystemClock.elapsedRealtime()
        val readings = MeasureLog.readings(context)
        return measureReport(readings, MeasureLog.availability(context), start.first, now) +
            batteryReport(start.second, MeasureLog.batteryPercent(context), now - start.first) +
            sampleReport(readings, start.first)
    }

    private fun required() = requiredPermissions(Build.VERSION.SDK_INT)

    private fun granted(context: Context, name: String) =
        context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_CMD = "cmd"
        private const val MEASURE_PREFIX = "measure-"
    }
}

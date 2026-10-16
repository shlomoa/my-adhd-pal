package com.shlomoa.myadhdpal.wear.debug.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import java.util.concurrent.Executor

/**
 * Debug only, driven from the computer: `adb shell am broadcast -n
 * com.shlomoa.myadhdpal/.wear.debug.probe.ProbeReceiver --es cmd <command>` with one of capabilities, start, stop,
 * report, clear, vibrate. Results are in logcat with the tag PROBE
 * (`adb logcat -d -s PROBE:I`). See `docs/test-reports/watch-probe-checklist.md`.
 */
class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val client = HealthServices.getClient(context).passiveMonitoringClient
        val executor = Executor { it.run() }
        when (intent.getStringExtra(EXTRA_CMD)) {
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

            "start" -> {
                val config = PassiveListenerConfig.builder().setDataTypes(setOf(DataType.HEART_RATE_BPM)).build()
                val future = client.setPassiveListenerServiceAsync(ProbeListenerService::class.java, config)
                future.addListener({
                    runCatching { future.get() }
                        .onSuccess { ProbeLog.note("start registered") }
                        .onFailure { ProbeLog.note("start failed: $it") }
                }, executor)
            }

            "stop" -> {
                val future = client.clearPassiveListenerServiceAsync()
                future.addListener({
                    runCatching { future.get() }
                        .onSuccess { ProbeLog.note("stop cleared") }
                        .onFailure { ProbeLog.note("stop failed: $it") }
                }, executor)
            }

            "report" -> formatReport(ProbeLog.batches(context)).forEach(ProbeLog::note)

            "clear" -> ProbeLog.clear(context).also { ProbeLog.note("cleared") }

            "vibrate" -> VibrationProbe.strong(context)

            else -> ProbeLog.note("unknown command; use capabilities, start, stop, report, clear or vibrate")
        }
    }

    companion object {
        const val EXTRA_CMD = "cmd"
    }
}

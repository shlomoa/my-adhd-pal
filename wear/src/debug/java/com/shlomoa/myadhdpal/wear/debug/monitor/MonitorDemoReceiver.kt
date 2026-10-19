package com.shlomoa.myadhdpal.wear.debug.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.alerts.Reading
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.log.Logger

/**
 * Debug only. The injector of the stress demo: it feeds synthetic readings into the same
 * [com.shlomoa.myadhdpal.wear.monitor.MonitorHub] the Monitoring service feeds, so the evaluation, the local response
 * and the alert to the phone are the real code, not a mock. Only `adb shell` (DUMP) can send to it: `adb shell am
 * broadcast -n <package>/.debug.monitor.MonitorDemoReceiver --es cmd <cmd>`.
 *
 * Commands: `baseline` (--ei bpm, default 62: 25 hours of resting samples, so a baseline exists), `inject` (--es
 * values "70,95,120", the last one now), `series` (--ei from 60 --ei to 125 --ei seconds 20: one reading per second
 * in real time), `im-ok`, `status`. It logs counts and ids with the tag DEMO, never a heart-rate value.
 */
class MonitorDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hub = (context.applicationContext as WatchApplication).graph.monitorHub
        val now = System.currentTimeMillis()
        when (val cmd = intent.getStringExtra("cmd").orEmpty()) {
            "baseline" -> {
                hub.feed(restingHistory(intent.getIntExtra("bpm", DEFAULT_RESTING), HISTORY_HOURS, now))
                Logger.i(TAG, "inject baseline fed hours=$HISTORY_HOURS")
            }

            "inject" -> {
                val values = parseValues(intent.getStringExtra("values").orEmpty())
                if (values == null) {
                    Logger.w(TAG, "inject refused: bad values")
                } else {
                    hub.feed(
                        values.mapIndexed { i, v ->
                            Reading(
                                Metric.HEART_RATE,
                                v,
                                now - (values.size - 1 - i) * MILLIS_PER_SECOND
                            )
                        }
                    )
                    Logger.i(TAG, "inject fed count=${values.size}")
                }
            }

            "series" -> {
                val from = intent.getIntExtra("from", DEFAULT_FROM)
                val to = intent.getIntExtra("to", DEFAULT_TO)
                val seconds = intent.getIntExtra("seconds", DEFAULT_SECONDS)
                Logger.i(TAG, "inject series start seconds=$seconds")
                val handler = Handler(Looper.getMainLooper())
                risingSeries(from, to, seconds, startAt = 0).forEachIndexed { second, reading ->
                    handler.postDelayed(
                        { hub.feed(listOf(reading.copy(at = System.currentTimeMillis()))) },
                        second * MILLIS_PER_SECOND
                    )
                }
            }

            "im-ok" -> hub.imOk()

            "status" -> {
                val state = hub.state
                Logger.i(
                    TAG,
                    "monitor status stress=${state.stress} level=${state.level} readings=${state.readings} " +
                        "baselineKnown=${state.baselineBpm != null}"
                )
            }

            else -> Logger.w(TAG, "unknown command $cmd")
        }
    }

    private companion object {
        const val TAG = "DEMO"
        const val MILLIS_PER_SECOND = 1_000L
        const val DEFAULT_RESTING = 62
        const val HISTORY_HOURS = 25
        const val DEFAULT_FROM = 60
        const val DEFAULT_TO = 125
        const val DEFAULT_SECONDS = 20
    }
}

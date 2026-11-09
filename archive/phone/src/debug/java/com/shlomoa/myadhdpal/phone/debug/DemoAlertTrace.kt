package com.shlomoa.myadhdpal.phone.debug

import com.shlomoa.myadhdpal.phone.alert.AlertObserver
import com.shlomoa.myadhdpal.phone.alert.AlertPlan
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import java.time.Clock

/**
 * Logs when this phone stored and raised each watch alert, for the stress-demo script (`scripts/demo`) to PASS or FAIL
 * the phone stage and measure the latency. Ids and epoch milliseconds only: no heart-rate value, threshold or metric.
 * The times are kept in memory, so [dump] shows `none` for an alert seen before this process started.
 */
class DemoAlertTrace(private val clock: Clock, private val log: (String) -> Unit) : AlertObserver {
    private val storedAt = mutableMapOf<String, Long>()
    private val raised = mutableMapOf<String, Pair<Long, Boolean>>()

    @Synchronized
    override fun stored(alert: HistoryEntry.AlertRaised) {
        val now = clock.millis()
        storedAt[alert.id] = now
        log(storedLine(alert, now.toString()))
    }

    @Synchronized
    override fun raised(alert: HistoryEntry.AlertRaised, plan: AlertPlan) {
        val now = clock.millis()
        raised[alert.id] = now to plan.sound
        log(raisedLine(alert.id, now.toString(), if (plan.sound) "1" else "0"))
    }

    /** One stored and one raised line per alert row, then the end line. */
    @Synchronized
    fun dump(alerts: List<HistoryEntry.AlertRaised>) {
        alerts.forEach { alert ->
            log(storedLine(alert, storedAt[alert.id]?.toString() ?: NONE))
            val shown = raised[alert.id]
            log(
                raisedLine(
                    alert.id,
                    shown?.first?.toString() ?: NONE,
                    shown?.let { if (it.second) "1" else "0" } ?: NONE
                )
            )
        }
        log("DEMO alerts end count=${alerts.size}")
    }

    private fun storedLine(alert: HistoryEntry.AlertRaised, at: String) =
        "DEMO alert stored id=${alert.id} watchAt=${alert.at} storedAt=$at"

    private fun raisedLine(id: String, at: String, sound: String) = "DEMO alert raised id=$id raisedAt=$at sound=$sound"

    private companion object {
        const val NONE = "none"
    }
}

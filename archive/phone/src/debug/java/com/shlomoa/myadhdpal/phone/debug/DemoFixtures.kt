package com.shlomoa.myadhdpal.phone.debug

import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Selection
import java.time.Clock
import java.util.UUID

/**
 * Debug builds only. Puts example data on the phone for the walkthrough script (`scripts/demo`): two tasks with a
 * reminder each, and an acknowledgement, a snooze and an answer in the history, all with fixed ids so repeating changes
 * nothing. With [seed]`(withAlert = true)` it also stores one new alert through the real history, which the running app
 * raises like an alert from the watch. Logs one `DEMO seeded` line; never a health value.
 */
class DemoFixtures(
    private val tasks: TaskRepository,
    private val history: HistoryRepository,
    private val clock: Clock,
    private val log: (String) -> Unit
) {
    suspend fun seed(withAlert: Boolean) {
        val now = clock.millis()
        tasks.save(Task("demo-task-1", "Take medication"))
        tasks.save(TaskReminder("demo-rem-1", "demo-task-1", minuteOfDay = 8 * MINUTES_PER_HOUR, days = ALL_DAYS))
        tasks.save(Task("demo-task-2", "Stand up and stretch"))
        tasks.save(TaskReminder("demo-rem-2", "demo-task-2", minuteOfDay = 15 * MINUTES_PER_HOUR, days = WEEKDAYS))
        history.store(
            WatchEvent.AckReceived("demo-ack-1", now, Acknowledgement(AckAction.DONE, now - TWO_MINUTES, "demo-rem-1"))
        )
        history.store(
            WatchEvent.AckReceived(
                "demo-ack-2",
                now,
                Acknowledgement(AckAction.SNOOZE, now - THREE_MINUTES, "demo-rem-2")
            )
        )
        history.store(WatchEvent.SelectionReceived("demo-selection-1", now, Selection("2", now - ONE_MINUTE)))
        val alertId = if (withAlert) storeAlert(now) else null
        val rows = if (withAlert) BASE_ROWS + 1 else BASE_ROWS
        log("DEMO seeded history=$rows tasks=2 alert=${alertId ?: "none"}")
    }

    private suspend fun storeAlert(now: Long): String {
        val id = UUID.randomUUID().toString()
        history.store(
            WatchEvent.AlertReceived(
                id,
                now,
                Alert(Metric.HEART_RATE, ALERT_BPM, ALERT_THRESHOLD, now, episodeId = UUID.randomUUID().toString())
            )
        )
        return id
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val BASE_ROWS = 3
        const val ONE_MINUTE = 60_000L
        const val TWO_MINUTES = 2 * ONE_MINUTE
        const val THREE_MINUTES = 3 * ONE_MINUTE
        const val ALERT_BPM = 138.0
        const val ALERT_THRESHOLD = 110.0
        val ALL_DAYS = (1..7).toList()
        val WEEKDAYS = (1..5).toList()
    }
}

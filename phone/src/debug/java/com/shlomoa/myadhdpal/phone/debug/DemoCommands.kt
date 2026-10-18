package com.shlomoa.myadhdpal.phone.debug

import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.PublishOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** The adb broadcasts of the real-device demo (`scripts/demo`). The receivers only parse extras and call these. */
object DemoActions {
    const val PUBLISH_DEMO_SCHEDULE = "com.shlomoa.myadhdpal.debug.PUBLISH_DEMO_SCHEDULE"
    const val DUMP_HISTORY = "com.shlomoa.myadhdpal.debug.DUMP_HISTORY"
    const val DUMP_ALERTS = "com.shlomoa.myadhdpal.debug.DUMP_ALERTS"
    const val EXTRA_MINUTES_AHEAD = "minutes_ahead"
    const val EXTRA_TITLE = "title"
    const val LOG_TAG = "DEMO"
}

/**
 * Writes the result of each command as `DEMO ...` lines through [log] (logcat tag `DEMO` in the receivers), which the
 * demo script reads with `adb logcat -d -s DEMO:I`. The line formats are the contract with the Watch lane. Never
 * logs the title or any health value other than what the script needs to see an alert arrive.
 */
class DemoCommands(
    private val publisher: DemoSchedulePublisher,
    private val history: HistoryRepository,
    private val log: (String) -> Unit
) {
    suspend fun publishSchedule(minutesAhead: Int?, title: String?) {
        val result = try {
            publisher.publish(
                minutesAhead ?: DemoSchedulePublisher.DEFAULT_MINUTES_AHEAD,
                title ?: DemoSchedulePublisher.DEFAULT_TITLE
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            log("DEMO publish failed reason=${failure.message ?: failure::class.java.simpleName}")
            return
        }
        val at = result.reminderAt.toEpochMilli()
        log(
            when (val outcome = result.outcome) {
                is PublishOutcome.Published -> "DEMO published schedule version=${outcome.version} reminderAt=$at"
                PublishOutcome.Unchanged -> "DEMO published schedule unchanged reminderAt=$at"
                is PublishOutcome.Failed -> "DEMO publish failed reason=${outcome.reason}"
            }
        )
    }

    suspend fun dumpHistory() {
        val entries = history.history(null).first()
        entries.forEach { log(it.demoLine()) }
        log("DEMO history end count=${entries.size}")
    }

    private fun HistoryEntry.demoLine(): String = when (this) {
        is HistoryEntry.Acknowledged ->
            "DEMO history ack id=$id action=$action reminderId=${reminderId ?: NONE} " +
                "occurrenceAt=${occurrenceAt ?: NONE} at=$at"

        is HistoryEntry.Selected -> "DEMO history selection id=$id key=$key at=$at"

        is HistoryEntry.AlertRaised ->
            "DEMO history alert id=$id metric=$metric value=$value threshold=$threshold at=$at"
    }

    private companion object {
        const val NONE = "none"
    }
}

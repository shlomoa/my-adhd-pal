package com.shlomoa.myadhdpal.wear.demo

import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.wear.log.Logger
import com.shlomoa.myadhdpal.wear.scheduler.FiredReminder
import java.time.Instant

/**
 * The lines the demo script (`scripts/demo/run-demo.ps1`) reads with `adb logcat -s DEMO`. They hold ids and times
 * only, never reminder titles or other user text. Release builds install no log sink, so nothing is written there.
 */
object DemoLog {
    const val TAG = "DEMO"

    fun scheduleApplied(version: Long) = Logger.i(TAG, "schedule applied version=$version")

    fun reminderFired(fired: FiredReminder) = Logger.i(
        TAG,
        "reminder fired id=${fired.reminder.id} occurrenceAt=${fired.occurrenceAt.toEpochMilli()} " +
            "snoozeCount=${fired.snoozeCount}"
    )

    fun ackSent(action: AckAction, reminderId: String, occurrenceAt: Instant) = Logger.i(
        TAG,
        "ack sent action=${action.name} reminderId=$reminderId occurrenceAt=${occurrenceAt.toEpochMilli()}"
    )

    fun ackNotSent(action: AckAction, reminderId: String, reason: String) =
        Logger.i(TAG, "ack not sent action=${action.name} reminderId=$reminderId reason=$reason")
}

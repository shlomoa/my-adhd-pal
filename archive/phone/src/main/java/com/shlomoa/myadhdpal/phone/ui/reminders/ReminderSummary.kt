package com.shlomoa.myadhdpal.phone.ui.reminders

import com.shlomoa.myadhdpal.phone.data.TaskReminder

/** How a reminder's days read: every day, Monday to Friday, or the listed days (1 Monday to 7 Sunday, in order). */
sealed interface DaysSummary {
    data object EveryDay : DaysSummary

    data object Weekdays : DaysSummary

    data class Some(val days: List<Int>) : DaysSummary
}

/** [time] is the time of day in 24 hours, as `HH:mm`. */
data class ReminderSummary(val time: String, val days: DaysSummary)

private const val MINUTES_PER_HOUR = 60
private val ALL_DAYS = (1..7).toList()
private val WEEKDAYS = (1..5).toList()

fun TaskReminder.summary(): ReminderSummary {
    val sorted = days.sorted()
    return ReminderSummary(
        time = "%02d:%02d".format(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR),
        days = when (sorted) {
            ALL_DAYS -> DaysSummary.EveryDay
            WEEKDAYS -> DaysSummary.Weekdays
            else -> DaysSummary.Some(sorted)
        }
    )
}

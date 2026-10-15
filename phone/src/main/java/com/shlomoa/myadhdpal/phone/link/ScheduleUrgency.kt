package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.ScheduleLogic
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The item catalog says `/schedule` is urgent only if it changes the next 30 minutes. */
object ScheduleUrgency {
    private val window = Duration.ofMinutes(30)

    /**
     * True when the reminders and checkpoints that fire within 30 minutes after [now] differ between [previous] and
     * [next] (an addition, a removal or a time change), and always when there is no [previous] schedule.
     */
    fun changesNextHalfHour(previous: Schedule?, next: Schedule, now: Instant, zone: ZoneId): Boolean =
        previous == null || occurrences(previous, now, zone) != occurrences(next, now, zone)

    private fun occurrences(schedule: Schedule, now: Instant, zone: ZoneId): Set<Triple<String, String, Instant>> {
        val end = now.plus(window)
        val found = mutableSetOf<Triple<String, String, Instant>>()
        for (reminder in schedule.reminders) {
            val single = schedule.copy(reminders = listOf(reminder), checkpoints = emptyList())
            ScheduleLogic.nextReminder(single, now, zone)?.takeIf { it.at <= end }?.let {
                found += Triple("reminder", reminder.id, it.at)
            }
        }
        for (checkpoint in schedule.checkpoints) {
            val single = schedule.copy(reminders = emptyList(), checkpoints = listOf(checkpoint))
            ScheduleLogic.nextCheckpoint(single, now, zone)?.takeIf { it.at <= end }?.let {
                found += Triple("checkpoint", checkpoint.id, it.at)
            }
        }
        return found
    }
}

package com.shlomoa.myadhdpal.wear.scheduler

import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.ScheduleLogic
import java.time.Instant
import java.time.ZoneId

/** The reminder that is due next: [at] is when it fires and [occurrenceAt] the scheduled time of its occurrence. */
data class Due(val reminder: Reminder, val at: Instant, val occurrenceAt: Instant)

/**
 * The earliest of the next scheduled reminder and the pending snoozes of reminders that are still in the schedule; a
 * snooze wins a tie. The scheduler arms its alarm for this and the tile shows it, so both always agree.
 */
fun nextDue(schedule: Schedule?, snoozes: Map<String, SnoozeEntry>, now: Instant, zone: ZoneId): Due? {
    if (schedule == null) return null
    val scheduled = ScheduleLogic.nextReminder(schedule, now, zone)?.let { Due(it.reminder, it.at, it.at) }
    val byId = schedule.reminders.associateBy { it.id }
    val snoozed = snoozes
        .mapNotNull { (id, entry) -> byId[id]?.let { Due(it, entry.dueAt, entry.occurrenceAt) } }
        .minByOrNull { it.at }
    return if (snoozed != null && (scheduled == null || snoozed.at <= scheduled.at)) snoozed else scheduled
}

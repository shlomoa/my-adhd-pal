package com.shlomoa.myadhdpal.wear.scheduler

import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.ScheduleLogic
import com.shlomoa.myadhdpal.protocol.SnoozeDenial
import com.shlomoa.myadhdpal.protocol.SnoozeResult
import com.shlomoa.myadhdpal.wear.log.Logger
import java.time.Instant
import java.time.ZoneId

/** The platform alarm for this app: one exact alarm at a time, carrying the id of the reminder it is for. */
interface AlarmPort {
    /** False when the app may not set exact alarms (the permission was not granted or was revoked). */
    fun canScheduleExact(): Boolean

    /** Sets the alarm for [reminderId] at [at], replacing the previous one. Inexact alarms may fire late. */
    fun set(at: Instant, reminderId: String, exact: Boolean)

    fun cancel()
}

/**
 * A snoozed reminder: when it is due again, how many snoozes the occurrence has used and the scheduled time of that
 * occurrence (which acknowledgements report, also after several snoozes).
 */
data class SnoozeEntry(val dueAt: Instant, val count: Int, val occurrenceAt: Instant)

/** Keeps pending snoozes so that they survive a restart or reboot. */
interface SnoozeStore {
    fun load(): Map<String, SnoozeEntry>

    fun save(entries: Map<String, SnoozeEntry>)
}

sealed interface SchedulerStatus {
    /** Nothing to remind about. */
    data object Idle : SchedulerStatus

    /** An alarm is set; with [exact] false it is inexact because exact alarms are not allowed, so it may fire late. */
    data class Armed(val at: Instant, val reminderId: String, val exact: Boolean = true) : SchedulerStatus
}

/** A reminder that is due: [occurrenceAt] is the scheduled time of its occurrence, not of a later snooze. */
data class FiredReminder(val reminder: Reminder, val snoozeCount: Int, val occurrenceAt: Instant)

/**
 * Keeps one exact alarm set for the earliest of the next scheduled reminder and the pending snoozes. [rearm] is the
 * only place that sets alarms: call it when a schedule is applied, after a boot, after an alarm fires and after a
 * snooze.
 * [schedule] supplies the schedule in use; the clock and zone are explicit so that the logic can be tested.
 */
class ReminderScheduler(
    private val schedule: () -> Schedule?,
    private val alarms: AlarmPort,
    private val snoozes: SnoozeStore,
    private val now: () -> Instant,
    private val zone: ZoneId
) {
    var status: SchedulerStatus = SchedulerStatus.Idle
        private set

    fun rearm(): SchedulerStatus {
        status = computeAndSet()
        return status
    }

    /**
     * Snoozes [reminderId] (whose occurrence scheduled for [occurrenceAt] has used [snoozeCount] snoozes), persists the
     * new due time and re-arms. A denied snooze changes nothing.
     */
    fun snooze(reminderId: String, snoozeCount: Int, occurrenceAt: Instant): SnoozeResult {
        val settings = schedule()?.snooze ?: return SnoozeResult.Denied(SnoozeDenial.AT_CAP)
        val result = ScheduleLogic.snooze(settings, snoozeCount, now(), expiresAt = null)
        if (result is SnoozeResult.Until) {
            snoozes.save(snoozes.load() + (reminderId to SnoozeEntry(result.at, result.snoozeCount, occurrenceAt)))
            rearm()
        }
        return result
    }

    /** Forgets the pending snooze of [reminderId] (it was done or dismissed) and re-arms. */
    fun clearSnooze(reminderId: String) {
        snoozes.save(snoozes.load() - reminderId)
        rearm()
    }

    /**
     * The alarm for [reminderId] fired for the time [at]. Returns the reminder to show with the snoozes its occurrence
     * has used, or null when it is no longer in the schedule. Always arms the next alarm.
     */
    fun onAlarm(reminderId: String, at: Instant): FiredReminder? {
        val entries = snoozes.load()
        val snoozed = entries[reminderId]?.takeIf { it.dueAt == at }
        if (snoozed != null) snoozes.save(entries - reminderId)
        val reminder = schedule()?.reminders?.firstOrNull { it.id == reminderId }
        if (reminder == null) Logger.w(TAG, "alarm for a reminder that is no longer scheduled")
        rearm()
        return reminder?.let { FiredReminder(it, snoozed?.count ?: 0, snoozed?.occurrenceAt ?: at) }
    }

    private fun computeAndSet(): SchedulerStatus {
        val due = nextDue(schedule(), snoozes.load(), now(), zone)
        if (due == null) {
            alarms.cancel()
            return SchedulerStatus.Idle
        }
        val exact = alarms.canScheduleExact()
        alarms.set(due.at, due.reminder.id, exact)
        return SchedulerStatus.Armed(due.at, due.reminder.id, exact)
    }

    private companion object {
        const val TAG = "ReminderScheduler"
    }
}

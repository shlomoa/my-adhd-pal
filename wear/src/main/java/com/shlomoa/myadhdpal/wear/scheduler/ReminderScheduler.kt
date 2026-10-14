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
    /** False when the app may not set exact alarms (the permission is missing or was revoked). */
    fun canScheduleExact(): Boolean

    /** Sets the alarm for [reminderId] at [at], replacing the previous one. */
    fun set(at: Instant, reminderId: String)

    fun cancel()
}

/** A snoozed reminder: when it is due again and how many snoozes the occurrence has used. */
data class SnoozeEntry(val dueAt: Instant, val count: Int)

/** Keeps pending snoozes so that they survive a restart or reboot. */
interface SnoozeStore {
    fun load(): Map<String, SnoozeEntry>

    fun save(entries: Map<String, SnoozeEntry>)
}

sealed interface SchedulerStatus {
    /** Nothing to remind about. */
    data object Idle : SchedulerStatus

    data class Armed(val at: Instant, val reminderId: String) : SchedulerStatus

    /** Exact alarms are not allowed; reminders cannot fire until the permission is granted. */
    data object PermissionMissing : SchedulerStatus
}

data class FiredReminder(val reminder: Reminder, val snoozeCount: Int)

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
     * Snoozes [reminderId] (whose current occurrence has used [snoozeCount] snoozes), persists the new due time and
     * re-arms. A denied snooze changes nothing.
     */
    fun snooze(reminderId: String, snoozeCount: Int): SnoozeResult {
        val settings = schedule()?.snooze ?: return SnoozeResult.Denied(SnoozeDenial.AT_CAP)
        val result = ScheduleLogic.snooze(settings, snoozeCount, now(), expiresAt = null)
        if (result is SnoozeResult.Until) {
            snoozes.save(snoozes.load() + (reminderId to SnoozeEntry(result.at, result.snoozeCount)))
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
        return reminder?.let { FiredReminder(it, snoozed?.count ?: 0) }
    }

    private fun computeAndSet(): SchedulerStatus {
        val current = schedule()
        val nextScheduled = current?.let { ScheduleLogic.nextReminder(it, now(), zone) }
        val knownIds = current?.reminders?.map { it.id }?.toSet().orEmpty()
        val nextSnooze = snoozes.load().filterKeys { it in knownIds }.minByOrNull { it.value.dueAt }
        val useSnooze = nextSnooze != null && (nextScheduled == null || nextSnooze.value.dueAt <= nextScheduled.at)
        val target = if (useSnooze) {
            SchedulerStatus.Armed(nextSnooze!!.value.dueAt, nextSnooze.key)
        } else {
            nextScheduled?.let { SchedulerStatus.Armed(it.at, it.reminder.id) }
        }
        return when {
            target == null -> SchedulerStatus.Idle.also { alarms.cancel() }
            !alarms.canScheduleExact() -> SchedulerStatus.PermissionMissing.also { alarms.cancel() }
            else -> target.also { alarms.set(it.at, it.reminderId) }
        }
    }

    private companion object {
        const val TAG = "ReminderScheduler"
    }
}

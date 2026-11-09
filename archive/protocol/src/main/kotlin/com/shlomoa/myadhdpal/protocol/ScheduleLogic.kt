package com.shlomoa.myadhdpal.protocol

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ReminderOccurrence(val reminder: Reminder, val at: Instant)

data class CheckpointOccurrence(val checkpoint: Checkpoint, val at: Instant)

enum class SnoozeDenial { AT_CAP, PAST_EXPIRY }

sealed interface SnoozeResult {
    /** The reminder is moved to [at]; [snoozeCount] is the number of snoozes used for this occurrence afterwards. */
    data class Until(val at: Instant, val snoozeCount: Int) : SnoozeResult

    data class Denied(val reason: SnoozeDenial) : SnoozeResult
}

/**
 * Pure schedule logic with an explicit clock (`now`) and time zone, so it is testable with a fixed clock.
 * Reminders and checkpoints are wall-clock times in [ZoneId]; on a daylight-saving change a time in the skipped hour
 * moves forward by the gap (java.time rule) and a time in the repeated hour fires at its first occurrence only.
 */
object ScheduleLogic {
    private const val DAYS_TO_SCAN = 8L

    /** The first reminder strictly after [now], or null when there are none. Ties go to the earlier list entry. */
    fun nextReminder(schedule: Schedule, now: Instant, zone: ZoneId): ReminderOccurrence? {
        var best: ReminderOccurrence? = null
        for (reminder in schedule.reminders) {
            val at = nextOccurrence(reminder.minuteOfDay, reminder.days, now, zone) ?: continue
            if (best == null || at < best.at) best = ReminderOccurrence(reminder, at)
        }
        return best
    }

    /** The first checkpoint strictly after [now] (checkpoints repeat every day), or null when there are none. */
    fun nextCheckpoint(schedule: Schedule, now: Instant, zone: ZoneId): CheckpointOccurrence? {
        var best: CheckpointOccurrence? = null
        for (checkpoint in schedule.checkpoints) {
            val at = nextOccurrence(checkpoint.minuteOfDay, ALL_DAYS, now, zone) ?: continue
            if (best == null || at < best.at) best = CheckpointOccurrence(checkpoint, at)
        }
        return best
    }

    /**
     * A snooze moves the reminder to [now] plus the configured minutes. It is denied at the cap (`snoozeCount` has
     * reached `maxCount`, so `maxCount` 0 turns snoozing off) and, if the occurrence expires, when the new time would
     * be at or after [expiresAt]. The cap is reported first.
     */
    fun snooze(settings: SnoozeSettings, snoozeCount: Int, now: Instant, expiresAt: Instant?): SnoozeResult {
        val until = now.plus(Duration.ofMinutes(settings.minutes.toLong()))
        return when {
            snoozeCount >= settings.maxCount -> SnoozeResult.Denied(SnoozeDenial.AT_CAP)
            expiresAt != null && until >= expiresAt -> SnoozeResult.Denied(SnoozeDenial.PAST_EXPIRY)
            else -> SnoozeResult.Until(until, snoozeCount + 1)
        }
    }

    /** A schedule applies only if its version is greater than the one in use; equal and older versions are ignored. */
    fun shouldApply(currentVersion: Long?, incomingVersion: Long): Boolean =
        currentVersion == null || incomingVersion > currentVersion

    private val ALL_DAYS = listOf(1, 2, 3, 4, 5, 6, 7)

    private fun nextOccurrence(minuteOfDay: Int, days: List<Int>, now: Instant, zone: ZoneId): Instant? {
        val time = LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
        val today = ZonedDateTime.ofInstant(now, zone).toLocalDate()
        for (offset in 0L until DAYS_TO_SCAN) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek.value !in days) continue
            val at = ZonedDateTime.of(date, time, zone).toInstant()
            if (at > now) return at
        }
        return null
    }

    private const val MINUTES_PER_HOUR = 60
}

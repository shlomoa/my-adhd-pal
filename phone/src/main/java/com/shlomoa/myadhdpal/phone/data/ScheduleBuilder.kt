package com.shlomoa.myadhdpal.phone.data

import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Limits
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule

/** Pure function from the phone's data to the wire model; shared by every [ScheduleRepository]. */
object ScheduleBuilder {
    /** Reminders of tasks that no longer exist are left out; at most `Limits.MAX_REMINDERS` are sent. */
    fun build(version: Long, tasks: List<Task>, reminders: List<TaskReminder>, settings: AppSettings): Schedule {
        val titles = tasks.associate { it.id to it.title }
        val wire = reminders.mapNotNull { r ->
            titles[r.taskId]?.let { Reminder(r.id, it, r.minuteOfDay, r.days) }
        }.take(Limits.MAX_REMINDERS)
        return Schedule(
            version = version,
            reminders = wire,
            checkpoints = emptyList(),
            snooze = settings.snooze,
            alerts = AlertSettings(
                settings.thresholds,
                settings.cooldownSeconds,
                settings.offsetBpm,
                settings.initialBpm
            )
        )
    }
}

package com.shlomoa.myadhdpal.phone.debug

import com.shlomoa.myadhdpal.phone.data.PublishOutcome
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import java.time.Clock
import java.time.ZonedDateTime

/**
 * Debug builds only (lives in a `debug` package, see `docs/conventions.md`). Puts one reminder a few minutes ahead
 * into the phone's data and publishes the schedule through the real [ScheduleRepository], so a demo needs no UI.
 * The ids are fixed, so repeating replaces the demo reminder instead of adding another.
 */
class DemoSchedulePublisher(
    private val tasks: TaskRepository,
    private val schedule: ScheduleRepository,
    private val clock: Clock
) {
    suspend fun publish(minutesAhead: Int = DEFAULT_MINUTES_AHEAD, title: String = DEFAULT_TITLE): PublishOutcome {
        require(minutesAhead in 1..MAX_MINUTES_AHEAD) { "minutesAhead must be 1 to $MAX_MINUTES_AHEAD" }
        val at = ZonedDateTime.now(clock).plusMinutes(minutesAhead.toLong())
        tasks.save(Task(TASK_ID, title))
        tasks.save(
            TaskReminder(REMINDER_ID, TASK_ID, at.hour * MINUTES_PER_HOUR + at.minute, listOf(at.dayOfWeek.value))
        )
        return schedule.publish()
    }

    companion object {
        const val DEFAULT_MINUTES_AHEAD = 2
        const val DEFAULT_TITLE = "Demo reminder"
        const val MAX_MINUTES_AHEAD = 1_440
        private const val MINUTES_PER_HOUR = 60
        const val TASK_ID = "demo-task"
        const val REMINDER_ID = "demo"
    }
}

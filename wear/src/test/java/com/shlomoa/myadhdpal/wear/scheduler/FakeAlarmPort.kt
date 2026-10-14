package com.shlomoa.myadhdpal.wear.scheduler

import java.time.Instant

/** Holds the single alarm the scheduler may have set, like the platform's alarm for this app. */
class FakeAlarmPort(var canSchedule: Boolean = true) : AlarmPort {
    data class Alarm(val at: Instant, val reminderId: String)

    var alarm: Alarm? = null
        private set
    var setCount = 0
        private set

    override fun canScheduleExact(): Boolean = canSchedule

    override fun set(at: Instant, reminderId: String) {
        alarm = Alarm(at, reminderId)
        setCount++
    }

    override fun cancel() {
        alarm = null
    }
}

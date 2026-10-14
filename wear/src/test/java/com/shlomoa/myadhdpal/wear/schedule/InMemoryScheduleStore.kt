package com.shlomoa.myadhdpal.wear.schedule

class InMemoryScheduleStore : ScheduleStore {
    private var stored: StoredSchedule? = null

    override fun load(): StoredSchedule? = stored

    override fun save(schedule: StoredSchedule) {
        stored = schedule
    }
}

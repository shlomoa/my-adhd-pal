package com.shlomoa.myadhdpal.wear

import android.app.Application
import android.content.Context
import com.shlomoa.myadhdpal.wear.reminder.ReminderNotifier
import com.shlomoa.myadhdpal.wear.schedule.FileScheduleStore
import com.shlomoa.myadhdpal.wear.scheduler.AndroidAlarmPort
import com.shlomoa.myadhdpal.wear.scheduler.FileSnoozeStore
import com.shlomoa.myadhdpal.wear.scheduler.ReminderScheduler
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** The pieces the receivers and screens share, wired by hand (the app is small enough not to need a framework). */
class AppGraph(context: Context) {
    val scheduleStore = FileScheduleStore(File(context.filesDir, "schedule.txt"))
    val notifier = ReminderNotifier(context)
    val scheduler = ReminderScheduler(
        schedule = { scheduleStore.load()?.schedule },
        alarms = AndroidAlarmPort(context),
        snoozes = FileSnoozeStore(File(context.filesDir, "snoozes.txt")),
        now = { Instant.now() },
        zone = ZoneId.systemDefault()
    )
}

open class WatchApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

package com.shlomoa.myadhdpal.wear

import android.app.Application
import android.content.Context
import androidx.wear.tiles.TileService
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.wear.link.LinkView
import com.shlomoa.myadhdpal.wear.reminder.AndroidTimerPort
import com.shlomoa.myadhdpal.wear.reminder.HapticPlayer
import com.shlomoa.myadhdpal.wear.reminder.ReminderActionHandler
import com.shlomoa.myadhdpal.wear.reminder.ReminderNotifier
import com.shlomoa.myadhdpal.wear.schedule.FileScheduleStore
import com.shlomoa.myadhdpal.wear.scheduler.AndroidAlarmPort
import com.shlomoa.myadhdpal.wear.scheduler.FileSnoozeStore
import com.shlomoa.myadhdpal.wear.scheduler.FiredReminder
import com.shlomoa.myadhdpal.wear.scheduler.ReminderScheduler
import com.shlomoa.myadhdpal.wear.tile.ReminderTileService
import com.shlomoa.myadhdpal.wear.tile.TileModel
import com.shlomoa.myadhdpal.wear.tile.TileUpdateTriggers
import com.shlomoa.myadhdpal.wear.tile.tileModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** The pieces the receivers and screens share, wired by hand (the app is small enough not to need a framework). */
class AppGraph(context: Context) {
    val scheduleStore = FileScheduleStore(File(context.filesDir, "schedule.txt"))
    val notifier = ReminderNotifier(context)
    val haptics = HapticPlayer(context)

    /** The reminder whose notification was shown last (used by the debug demo action). */
    @Volatile
    var lastFired: FiredReminder? = null

    /** Asks the system for a new tile; the schedule receiver and the link client report here once they exist (1.5). */
    val tileTriggers = TileUpdateTriggers {
        TileService.getUpdater(context).requestUpdate(ReminderTileService::class.java)
    }

    /** Set by the Data Layer wiring once it exists (step 1.5); until then acknowledgements are not sent. */
    @Volatile
    var transport: LinkTransport? = null
    private val snoozeStore = FileSnoozeStore(File(context.filesDir, "snoozes.txt"))
    val scheduler = ReminderScheduler(
        schedule = { scheduleStore.load()?.schedule },
        alarms = AndroidAlarmPort(context),
        snoozes = snoozeStore,
        now = { Instant.now() },
        zone = ZoneId.systemDefault()
    )
    val actionHandler = ReminderActionHandler(
        scheduler = scheduler,
        transport = { transport },
        timer = AndroidTimerPort(context),
        now = { Instant.now() },
        newId = { UUID.randomUUID().toString() },
        onHandled = tileTriggers::onActionHandled
    )

    /** The link as the tile and the home screen show it: the transport's state (Away without one) and the last sync. */
    fun linkView() = LinkView(transport?.linkState ?: LinkState.AWAY, scheduleStore.load()?.appliedAt)

    fun tileModel(): TileModel =
        tileModel(scheduleStore.load()?.schedule, snoozeStore.load(), linkView(), Instant.now(), ZoneId.systemDefault())
}

open class WatchApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

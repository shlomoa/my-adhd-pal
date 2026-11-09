package com.shlomoa.myadhdpal.wear

import android.app.Application
import android.content.Context
import androidx.wear.tiles.TileService
import com.shlomoa.myadhdpal.protocol.AlertRules
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.TransportListener
import com.shlomoa.myadhdpal.wear.datalayer.DataLayerTransport
import com.shlomoa.myadhdpal.wear.link.FanOutListener
import com.shlomoa.myadhdpal.wear.link.LinkClient
import com.shlomoa.myadhdpal.wear.link.LinkView
import com.shlomoa.myadhdpal.wear.log.Logger
import com.shlomoa.myadhdpal.wear.monitor.HapticAlertResponder
import com.shlomoa.myadhdpal.wear.monitor.MonitorHub
import com.shlomoa.myadhdpal.wear.monitor.StressMonitor
import com.shlomoa.myadhdpal.wear.monitor.TransportAlertSender
import com.shlomoa.myadhdpal.wear.prompt.PromptInbox
import com.shlomoa.myadhdpal.wear.reminder.AndroidTimerPort
import com.shlomoa.myadhdpal.wear.reminder.HapticPlayer
import com.shlomoa.myadhdpal.wear.reminder.ReminderActionHandler
import com.shlomoa.myadhdpal.wear.reminder.ReminderNotifier
import com.shlomoa.myadhdpal.wear.schedule.FileScheduleStore
import com.shlomoa.myadhdpal.wear.schedule.ScheduleReceiver
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
class AppGraph(private val context: Context) {
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

    /**
     * Every heart-rate reading, from the Monitoring service or the debug injector, goes through this hub: evaluation,
     * the local response (vibration) and the urgent alert to the phone. The alert settings are the defaults until the
     * schedule carries them.
     */
    val monitorHub: MonitorHub by lazy {
        MonitorHub(
            monitor = StressMonitor(
                scheduleStore.load()?.schedule?.alerts ?: AlertSettings(
                    AlertRules.DEFAULT_COOLDOWN_SECONDS
                ),
                {
                    UUID.randomUUID().toString()
                }
            ),
            responder = HapticAlertResponder(haptics),
            sender = TransportAlertSender({
                transport
            }, { UUID.randomUUID().toString() }, { System.currentTimeMillis() }),
            now = { System.currentTimeMillis() },
            log = { Logger.i("DEMO", "monitor $it") }
        )
    }

    private val dataLayerHolder = lazy { buildDataLayer() }

    /** The Data Layer link, built on first use: by the app at start, the listener service or an alarm receiver. */
    fun dataLayer(): DataLayerTransport = dataLayerHolder.value

    /** The link to the phone, null only while it is being built. */
    val transport: LinkTransport?
        get() = dataLayer().transport

    lateinit var linkClient: LinkClient
        private set
    lateinit var promptInbox: PromptInbox
        private set
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

    private fun buildDataLayer(): DataLayerTransport {
        val dataLayer = DataLayerTransport(context)
        val link = dataLayer.transport
        val scheduleReceiver = ScheduleReceiver(link, scheduleStore, now = { Instant.now() }) { _, appliedAt ->
            scheduler.rearm()
            linkClient.onScheduleApplied(appliedAt)
            tileTriggers.onScheduleApplied()
        }
        linkClient = LinkClient(
            link,
            appVersion(),
            newId = { UUID.randomUUID().toString() },
            now = { Instant.now() },
            lastSyncAt = scheduleStore.load()?.appliedAt
        )
        linkClient.setOnChange(tileTriggers::onLinkChanged)
        promptInbox = PromptInbox(link, now = { Instant.now() })
        val episodeEnds = object : TransportListener {
            override fun onItemChanged(item: StoredItem) = monitorHub.onItem(item)

            override fun onItemDeleted(path: String) = Unit

            override fun onLinkStateChanged(state: LinkState) = Unit
        }
        link.setListener(FanOutListener(linkClient, scheduleReceiver, promptInbox, episodeEnds))
        linkClient.start()
        dataLayer.start {
            scheduleReceiver.start()
            promptInbox.start()
        }
        return dataLayer
    }

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** The link as the tile and the home screen show it: the transport's state (Away without one) and the last sync. */
    fun linkView() = LinkView(transport?.linkState ?: LinkState.AWAY, scheduleStore.load()?.appliedAt)

    fun tileModel(): TileModel =
        tileModel(scheduleStore.load()?.schedule, snoozeStore.load(), linkView(), Instant.now(), ZoneId.systemDefault())
}

open class WatchApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    /** Builds the Data Layer link in every process start (alarm, boot, listener service) so acks always have one. */
    override fun onCreate() {
        super.onCreate()
        graph.dataLayer()
    }
}

package com.shlomoa.myadhdpal.wear.monitor

import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.EpisodeEnd
import com.shlomoa.myadhdpal.protocol.HeartRatePermission
import com.shlomoa.myadhdpal.protocol.ItemPath
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.StoredItem
import com.shlomoa.myadhdpal.protocol.WatchStatus
import com.shlomoa.myadhdpal.protocol.alerts.Reading
import kotlin.math.roundToInt

/** What the Monitoring screen shows. */
data class MonitorState(
    val baselineBpm: Double?,
    val thresholdBpm: Double,
    val latestBpm: Double?,
    val stress: Boolean,
    val level: Int,
    val episodeId: String?,
    val readings: Int
)

/** The local response on the watch to an alert (vibration; the screen shows [MonitorState]). */
fun interface AlertResponder {
    fun respond(alert: Alert)
}

/** The way to the phone: the urgent alert, and the status (baseline, threshold) that is not urgent. */
interface AlertSender {
    fun send(alert: Alert)

    fun sendStatus(status: WatchStatus) = Unit
}

/**
 * Puts an alert as an `/alert/<id>` item (a fresh UUID per alert) on the link; the Data Layer buffers it while the
 * phone is away.
 */
class TransportAlertSender(
    private val transport: () -> LinkTransport?,
    private val newId: () -> String,
    private val now: () -> Long
) : AlertSender {
    override fun send(alert: Alert) {
        val id = newId()
        transport()?.put(Paths.alert(id), Codec.encode(id, now(), alert))
    }

    override fun sendStatus(status: WatchStatus) {
        transport()?.put(Paths.STATUS_WATCH, Codec.encode(newId(), now(), status))
    }
}

/**
 * The one path every heart-rate reading takes, whether the foreground service delivers it from Health Services or the
 * debug injector feeds a synthetic one: [feed] evaluates it ([StressMonitor]), and for each alert it raised first the
 * local response and then the urgent send to the phone, so a stress event is acted on at once and a missing link only
 * delays the phone. Calls are serialised. Logs hold counts, ids and times, never a heart-rate value.
 */
class MonitorHub(
    private val monitor: StressMonitor,
    private val responder: AlertResponder,
    private val sender: AlertSender,
    private val now: () -> Long,
    private val log: (String) -> Unit
) {
    private val listeners = mutableListOf<(MonitorState) -> Unit>()
    private var readingCount = 0
    private var level = 0
    private var lastStatus: Pair<Int?, Int>? = null

    @Volatile
    var state: MonitorState = stateOf(monitor.status(now()))
        private set

    fun addListener(listener: (MonitorState) -> Unit) = synchronized(this) { listeners += listener }

    fun removeListener(listener: (MonitorState) -> Unit) = synchronized(this) { listeners -= listener }

    fun feed(readings: List<Reading>) {
        if (readings.isEmpty()) return
        synchronized(this) {
            if (readingCount == 0) log("first reading at=${readings.minOf { it.at }}")
            readingCount += readings.size
            val update = monitor.onReadings(readings, now())
            update.alerts.forEach { handle(it) }
            if (update.episodeId == null) level = 0
            publish(update)
        }
    }

    /** Ends the episode with [id]; false when it is not the running one. */
    fun endEpisode(id: String): Boolean = synchronized(this) {
        val ended = monitor.endEpisode(id)
        if (ended) {
            level = 0
            log("episode ended id=$id")
            publish(monitor.status(now()))
        }
        ended
    }

    /** "I'm OK" on the watch. */
    fun imOk() {
        val id = synchronized(this) { monitor.activeEpisodeId } ?: return
        endEpisode(id)
    }

    /** An item written by the phone; only an end of episode is of interest. */
    fun onItem(item: StoredItem) {
        if (Paths.parse(item.path) !is ItemPath.EpisodeEnd) return
        val decoded = Codec.decodeIfCompatible(item.path, item.envelope) as? DecodeResult.Ok ?: return
        (decoded.item.message as? EpisodeEnd)?.let { endEpisode(it.episodeId) }
    }

    private fun handle(alert: Alert) {
        level = alert.level
        log("event level=${alert.level} episode=${alert.episodeId} at=${alert.at}")
        responder.respond(alert)
        log("response level=${alert.level} at=${now()}")
        runCatching { sender.send(alert) }
            .onSuccess { log("alert sent level=${alert.level} at=${now()}") }
            .onFailure { log("alert not sent level=${alert.level} reason=${it.javaClass.simpleName}") }
    }

    private fun publish(update: MonitorUpdate) {
        state = stateOf(update)
        val status = update.baselineBpm?.roundToInt() to update.thresholdBpm.roundToInt()
        if (status != lastStatus) {
            lastStatus = status
            runCatching {
                sender.sendStatus(WatchStatus(status.first, status.second, HeartRatePermission.GRANTED))
            }
        }
        listeners.toList().forEach { it(state) }
    }

    private fun stateOf(update: MonitorUpdate) = MonitorState(
        baselineBpm = update.baselineBpm,
        thresholdBpm = update.thresholdBpm,
        latestBpm = update.latestBpm,
        stress = update.episodeId != null,
        level = level,
        episodeId = update.episodeId,
        readings = readingCount
    )
}

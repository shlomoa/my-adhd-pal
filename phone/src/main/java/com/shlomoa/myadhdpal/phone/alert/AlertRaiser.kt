package com.shlomoa.myadhdpal.phone.alert

import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.PublishResult

/** Shows and stops the alert on the device. The Android implementation is [AndroidAlertNotifier]. */
interface AlertNotifier {
    fun raise(alert: HistoryEntry.AlertRaised, plan: AlertPlan)

    fun stop()
}

/** The ids of the alerts already raised, in the order they were raised. Null until the feature has run once. */
interface RaisedAlertsStore {
    suspend fun load(): Set<String>?

    suspend fun save(ids: Set<String>)
}

/** What "I'm OK" did beyond stopping the phone's own alert. */
sealed interface DismissResult {
    /** The watch was told to end the episode. */
    data object Sent : DismissResult

    /** The alert carried no episode id (a watch on protocol 1.1), so there was nothing to tell the watch. */
    data object NoEpisode : DismissResult

    /** The phone's alert is stopped but the watch could not be told. */
    data class NotSent(val reason: String) : DismissResult
}

/**
 * Raises the phone alert for every alert the watch sent (`docs/phone-alert.md`). It reads the stored alert rows, so an
 * alert stored just before the process died, or delivered late, is still raised. Alerts are told apart by id, not by
 * time: the watch's clock is not the phone's. Several new alerts at once raise only the newest (one alert at a time).
 *
 * The first time it ever runs, the alerts already in the history are not news and are not raised. It keeps only the ids
 * of alerts still in the history. The id is saved after raising: if the process dies in between, the alert is raised
 * again at the next start, which is better than missing it.
 */
class AlertRaiser(
    private val history: HistoryRepository,
    private val store: RaisedAlertsStore,
    private val notifier: AlertNotifier,
    private val prefsProvider: suspend () -> AlertPrefs,
    private val hasAmplitudeControl: () -> Boolean,
    private val endEpisode: suspend (episodeId: String) -> PublishResult
) {
    private var known: Set<String>? = null

    suspend fun run() {
        history.history(HistoryType.ALERT).collect { handle(it.filterIsInstance<HistoryEntry.AlertRaised>()) }
    }

    /**
     * "I'm OK": stops the phone's own alert at once, whatever the link does, then tells the watch to end the episode
     * [episodeId] (protocol 1.2; the watch ignores an episode that is already over).
     */
    suspend fun dismiss(episodeId: String?): DismissResult {
        notifier.stop()
        if (episodeId == null) return DismissResult.NoEpisode
        return when (val result = endEpisode(episodeId)) {
            PublishResult.Sent -> DismissResult.Sent
            is PublishResult.Failed -> DismissResult.NotSent(result.reason)
        }
    }

    private suspend fun handle(alerts: List<HistoryEntry.AlertRaised>) {
        val present = alerts.map { it.id }.toSet()
        val raisedBefore = known ?: store.load()
        if (raisedBefore == null) {
            known = present
            store.save(present)
            return
        }
        val fresh = alerts.filter { it.id !in raisedBefore }
        val kept = LinkedHashSet(raisedBefore.filter { it in present }).apply { addAll(fresh.map { it.id }) }
        if (fresh.isNotEmpty()) {
            val newest = fresh.maxBy { it.at }
            notifier.raise(newest, AlertPlanner.plan(newest.level, prefsProvider(), hasAmplitudeControl()))
        }
        if (kept != raisedBefore) store.save(kept)
        known = kept
    }
}

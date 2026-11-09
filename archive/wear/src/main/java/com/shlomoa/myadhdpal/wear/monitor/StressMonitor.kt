package com.shlomoa.myadhdpal.wear.monitor

import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.alerts.BaselineTracker
import com.shlomoa.myadhdpal.protocol.alerts.EpisodeEvaluator
import com.shlomoa.myadhdpal.protocol.alerts.HourlyLow
import com.shlomoa.myadhdpal.protocol.alerts.Reading

/**
 * What one call of [StressMonitor.onReadings] found: the baseline and threshold now, the alerts the readings raised.
 */
data class MonitorUpdate(
    val baselineBpm: Double?,
    val thresholdBpm: Double,
    val latestBpm: Double?,
    val alerts: List<Alert>,
    val episodeId: String?
)

/**
 * The continuous comparison of every heart-rate reading with the resting baseline (the user's definition of stress
 * detection): each call adds the readings to the [BaselineTracker] and evaluates them with the [EpisodeEvaluator]
 * against the baseline known at [now]. Not thread-safe; [MonitorHub] serialises the calls.
 */
class StressMonitor(settings: AlertSettings, newEpisodeId: () -> String, restoredLows: List<HourlyLow> = emptyList()) {
    private val baselines = BaselineTracker(restoredLows)
    private val evaluator = EpisodeEvaluator(settings, newEpisodeId)
    private var latest: Double? = null

    fun onReadings(readings: List<Reading>, now: Long): MonitorUpdate {
        baselines.add(readings)
        val baseline = baselines.baseline(now)
        val alerts = evaluator.evaluate(readings, baseline)
        readings.maxByOrNull { it.at }?.let { latest = it.value }
        return update(baseline, alerts)
    }

    /** The state without new readings (the screen before the first reading). */
    fun status(now: Long): MonitorUpdate = update(baselines.baseline(now), emptyList())

    /** Ends the episode with [id]; false when it is not the running one. */
    fun endEpisode(id: String): Boolean = evaluator.endEpisode(id)

    /** The id of the running episode, or null. */
    val activeEpisodeId: String? get() = evaluator.activeEpisodeId

    /** The hourly lows, the only part of the readings that may be saved. */
    fun hourlyLows(): List<HourlyLow> = baselines.hourlyLows()

    private fun update(baseline: Double?, alerts: List<Alert>) =
        MonitorUpdate(baseline, evaluator.thresholdBpm(baseline), latest, alerts, evaluator.activeEpisodeId)
}

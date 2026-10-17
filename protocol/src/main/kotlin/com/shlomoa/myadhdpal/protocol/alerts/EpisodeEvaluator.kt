package com.shlomoa.myadhdpal.protocol.alerts

import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.AlertRules
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Metric

/**
 * The decided alert rules of `docs/plans/biometrics.md` section 3, as the watch applies them to heart-rate readings:
 * - the threshold is the resting baseline plus [AlertSettings.offsetBpm], or [AlertSettings.initialBpm] until a
 *   baseline exists; only readings above it count (direction ABOVE);
 * - the first reading above the threshold starts an episode and raises a level 1 [Alert];
 * - once [AlertSettings.cooldownSeconds] have passed since that alert, the next reading above the threshold raises a
 *   level 2 alert, and then one every [AlertRules.ESCALATION_REPEAT_SECONDS] while the rate stays above, at most
 *   [AlertRules.MAX_LEVEL2_REPEATS] level 2 alerts per episode;
 * - a reading at or below the threshold ends the episode, and so does [endEpisode] ("I'm OK" on either device); the
 *   next breach starts a new episode at level 1.
 *
 * Times are the reading times (epoch milliseconds on the watch clock), so batched delivery is evaluated in timestamp
 * order. State is in memory; the caller keeps its own record if it must survive a process restart. Not thread-safe.
 */
class EpisodeEvaluator(private val settings: AlertSettings, private val newEpisodeId: () -> String) {
    private var episodeId: String? = null
    private var lastAlertAt = 0L
    private var level2Count = 0

    /** The id of the running episode, or null when none is running. */
    val activeEpisodeId: String? get() = episodeId

    /** The threshold in bpm for the given baseline (null until one exists). */
    fun thresholdBpm(baselineBpm: Double?): Double =
        if (baselineBpm == null) settings.initialBpm.toDouble() else baselineBpm + settings.offsetBpm

    fun evaluate(readings: List<Reading>, baselineBpm: Double?): List<Alert> {
        val threshold = thresholdBpm(baselineBpm)
        return readings.filter { it.metric == Metric.HEART_RATE }.sortedBy { it.at }.mapNotNull { reading ->
            if (reading.value > threshold) alertFor(reading, threshold) else endEpisodeQuietly()
        }
    }

    /** Ends the episode with [id]; false (and no effect) when it is not the running one. */
    fun endEpisode(id: String): Boolean {
        if (id != episodeId) return false
        endEpisodeQuietly()
        return true
    }

    private fun alertFor(reading: Reading, threshold: Double): Alert? {
        val id = episodeId
        val level = when {
            id == null -> AlertRules.LEVEL_1

            level2Count == 0 && sinceLastAlert(reading) >= settings.cooldownSeconds * MILLIS_PER_SECOND ->
                AlertRules.LEVEL_2

            level2Count in 1 until AlertRules.MAX_LEVEL2_REPEATS &&
                sinceLastAlert(reading) >= AlertRules.ESCALATION_REPEAT_SECONDS * MILLIS_PER_SECOND ->
                AlertRules.LEVEL_2

            else -> return null
        }
        val episode = id ?: newEpisodeId().also {
            episodeId = it
            level2Count = 0
        }
        if (level == AlertRules.LEVEL_2) level2Count++
        lastAlertAt = reading.at
        return Alert(reading.metric, reading.value, threshold, reading.at, level, episode)
    }

    private fun sinceLastAlert(reading: Reading) = reading.at - lastAlertAt

    private fun endEpisodeQuietly(): Alert? {
        episodeId = null
        level2Count = 0
        return null
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

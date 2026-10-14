package com.shlomoa.myadhdpal.protocol.alerts

import com.shlomoa.myadhdpal.protocol.Alert
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Direction
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.Threshold

/** One measurement; [at] is epoch milliseconds on the clock of the device that measured it. */
data class Reading(val metric: Metric, val value: Double, val at: Long)

/**
 * Turns readings into alerts. Thresholds and the cooldown come from [settings] (set on the phone, synced in the
 * schedule), so no limit is hard-coded here. A threshold alerts at most once per cooldown, counted between reading
 * times; batches are evaluated in timestamp order. State is in memory, so a caller that must survive a process
 * restart keeps its own record. Not thread-safe: call it from one thread.
 */
class AlertEvaluator(private val settings: AlertSettings) {
    private val lastAlertAt = mutableMapOf<Threshold, Long>()

    fun evaluate(readings: List<Reading>): List<Alert> = readings.sortedBy { it.at }.flatMap { reading ->
        settings.thresholds
            .filter { it.metric == reading.metric && it.isBreachedBy(reading.value) && !inCooldown(it, reading.at) }
            .map { threshold ->
                lastAlertAt[threshold] = reading.at
                Alert(reading.metric, reading.value, threshold.value, reading.at)
            }
    }

    private fun inCooldown(threshold: Threshold, at: Long): Boolean {
        val last = lastAlertAt[threshold] ?: return false
        return at - last < settings.cooldownSeconds * MILLIS_PER_SECOND
    }

    private fun Threshold.isBreachedBy(value: Double) = when (direction) {
        Direction.ABOVE -> value > this.value
        Direction.BELOW -> value < this.value
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

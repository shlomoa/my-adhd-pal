package com.shlomoa.myadhdpal.phone.data

import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.AlertRules
import com.shlomoa.myadhdpal.protocol.Direction
import com.shlomoa.myadhdpal.protocol.Limits
import com.shlomoa.myadhdpal.protocol.Metric
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.protocol.Threshold

/** A thing to remember. Title validation (blank, length up to `Limits.MAX_TEXT_LENGTH`) is the ViewModel's job. */
data class Task(val id: String, val title: String)

/** A reminder for [taskId] at [minuteOfDay] (0 to 1439) on [days] (1 = Monday to 7 = Sunday). */
data class TaskReminder(val id: String, val taskId: String, val minuteOfDay: Int, val days: List<Int>)

enum class HistoryType { ACKNOWLEDGEMENT, SELECTION, ALERT }

/** A stored event from the watch. [at] is the time of the user's action (or of the reading) on the watch clock. */
sealed interface HistoryEntry {
    val id: String
    val at: Long

    /** [reminderId] and [occurrenceAt]: the reminder and scheduled fire time answered, if the watch sent them. */
    data class Acknowledged(
        override val id: String,
        override val at: Long,
        val action: AckAction,
        val reminderId: String? = null,
        val occurrenceAt: Long? = null
    ) : HistoryEntry

    data class Selected(
        override val id: String,
        override val at: Long,
        val key: String,
        val promptTitle: String? = null
    ) : HistoryEntry

    data class AlertRaised(
        override val id: String,
        override val at: Long,
        val metric: Metric,
        val value: Double,
        val threshold: Double,
        val level: Int = 1,
        val episodeId: String? = null
    ) : HistoryEntry
}

sealed interface StoreResult {
    data object Stored : StoreResult

    /** The same id was stored before; nothing changed. */
    data object Duplicate : StoreResult

    /** The event is not history (a hello). */
    data object Ignored : StoreResult
}

/** The settings the user can change. They are published to the watch inside the schedule. */
data class AppSettings(
    val thresholds: List<Threshold>,
    val cooldownSeconds: Int,
    val snooze: SnoozeSettings,
    val cadenceMinutes: Int,
    val offsetBpm: Int = AlertRules.DEFAULT_OFFSET_BPM,
    val initialBpm: Int = AlertRules.DEFAULT_INITIAL_BPM
)

/** Accepted ranges. */
object SettingsLimits {
    val THRESHOLD_VALUE: ClosedFloatingPointRange<Double> = 30.0..220.0
    val COOLDOWN_SECONDS: IntRange = 60..86_400
    val CADENCE_MINUTES: IntRange = 5..1_440
    val OFFSET_BPM: IntRange = AlertRules.MIN_OFFSET_BPM..AlertRules.MAX_OFFSET_BPM
    val INITIAL_BPM: IntRange = AlertRules.MIN_BPM..AlertRules.MAX_INITIAL_BPM
    const val MAX_THRESHOLDS: Int = Limits.MAX_THRESHOLDS
}

/**
 * Defaults on first run. The heart-rate threshold is a placeholder until the metrics and thresholds are agreed with
 * the user (step 6.2).
 */
object SettingsDefaults {
    val settings = AppSettings(
        thresholds = listOf(Threshold(Metric.HEART_RATE, Direction.ABOVE, 150.0)),
        cooldownSeconds = AlertRules.DEFAULT_COOLDOWN_SECONDS,
        snooze = SnoozeSettings(minutes = 10, maxCount = 3),
        cadenceMinutes = 60
    )
}

/** The one place that decides whether settings are acceptable; every [SettingsRepository] uses it. */
object SettingsValidator {
    fun validate(settings: AppSettings): Set<SettingsError> = buildSet {
        if (settings.thresholds.size > SettingsLimits.MAX_THRESHOLDS) add(SettingsError.TOO_MANY_THRESHOLDS)
        if (settings.thresholds.any { it.value !in SettingsLimits.THRESHOLD_VALUE }) {
            add(SettingsError.THRESHOLD_OUT_OF_RANGE)
        }
        if (settings.cooldownSeconds !in SettingsLimits.COOLDOWN_SECONDS) add(SettingsError.COOLDOWN_OUT_OF_RANGE)
        if (settings.cadenceMinutes !in SettingsLimits.CADENCE_MINUTES) add(SettingsError.CADENCE_OUT_OF_RANGE)
        if (settings.offsetBpm !in SettingsLimits.OFFSET_BPM) add(SettingsError.OFFSET_OUT_OF_RANGE)
        if (settings.initialBpm !in SettingsLimits.INITIAL_BPM) add(SettingsError.INITIAL_OUT_OF_RANGE)
    }
}

enum class SettingsError {
    THRESHOLD_OUT_OF_RANGE,
    TOO_MANY_THRESHOLDS,
    COOLDOWN_OUT_OF_RANGE,
    CADENCE_OUT_OF_RANGE,
    OFFSET_OUT_OF_RANGE,
    INITIAL_OUT_OF_RANGE
}

sealed interface SettingsResult {
    data object Saved : SettingsResult

    /** Nothing was saved; the old value is kept. */
    data class Invalid(val errors: Set<SettingsError>) : SettingsResult
}

sealed interface PublishOutcome {
    data class Published(val version: Long) : PublishOutcome

    /** The schedule is identical to the last published one; nothing was sent. */
    data object Unchanged : PublishOutcome

    /** Not counted as sent: the version is not consumed. */
    data class Failed(val reason: String) : PublishOutcome
}

enum class PromptError { BLANK_TITLE, TITLE_TOO_LONG, TOO_FEW_OPTIONS, TOO_MANY_OPTIONS, EXPIRY_IN_PAST }

sealed interface PromptPublishResult {
    data class Published(val id: String) : PromptPublishResult

    data class Rejected(val errors: Set<PromptError>) : PromptPublishResult

    data class Failed(val reason: String) : PromptPublishResult
}

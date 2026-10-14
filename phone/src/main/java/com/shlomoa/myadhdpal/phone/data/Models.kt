package com.shlomoa.myadhdpal.phone.data

import com.shlomoa.myadhdpal.protocol.AckAction
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

    data class Acknowledged(override val id: String, override val at: Long, val action: AckAction) : HistoryEntry

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
        val threshold: Double
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
    val cadenceMinutes: Int
)

/** Accepted ranges. */
object SettingsLimits {
    val THRESHOLD_VALUE: ClosedFloatingPointRange<Double> = 30.0..220.0
    val COOLDOWN_SECONDS: IntRange = 60..86_400
    val CADENCE_MINUTES: IntRange = 5..1_440
    const val MAX_THRESHOLDS: Int = Limits.MAX_THRESHOLDS
}

/**
 * Defaults on first run. The heart-rate threshold is a placeholder until the metrics and thresholds are agreed with
 * the user (step 6.2).
 */
object SettingsDefaults {
    val settings = AppSettings(
        thresholds = listOf(Threshold(Metric.HEART_RATE, Direction.ABOVE, 150.0)),
        cooldownSeconds = 600,
        snooze = SnoozeSettings(minutes = 10, maxCount = 3),
        cadenceMinutes = 60
    )
}

enum class SettingsError { THRESHOLD_OUT_OF_RANGE, TOO_MANY_THRESHOLDS, COOLDOWN_OUT_OF_RANGE, CADENCE_OUT_OF_RANGE }

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

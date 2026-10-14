package com.shlomoa.myadhdpal.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The body of an item. Every model validates its limits on construction, so a decoded body is always legal. */
sealed interface Message

@Serializable
enum class Metric { HEART_RATE }

@Serializable
enum class Direction { ABOVE, BELOW }

@Serializable
enum class AckAction { DONE, SNOOZE, START_2_MIN, DISMISS }

/** A reminder at a minute of the day (0 to 1439) on the given days (1 = Monday to 7 = Sunday). */
@Serializable
data class Reminder(val id: String, val title: String, val minuteOfDay: Int, val days: List<Int>) {
    init {
        requireText(id, Limits.MAX_KEY_LENGTH, "reminder id")
        requireText(title, Limits.MAX_TEXT_LENGTH, "reminder title")
        require(minuteOfDay in 0..MINUTES_PER_DAY - 1) { "minuteOfDay out of range" }
        require(days.size in 1..DAYS_PER_WEEK && days.all { it in 1..DAYS_PER_WEEK }) { "days out of range" }
    }
}

/** A checkpoint (a time of day at which the watch shows a check-in). */
@Serializable
data class Checkpoint(val id: String, val label: String, val minuteOfDay: Int) {
    init {
        requireText(id, Limits.MAX_KEY_LENGTH, "checkpoint id")
        requireText(label, Limits.MAX_TEXT_LENGTH, "checkpoint label")
        require(minuteOfDay in 0..MINUTES_PER_DAY - 1) { "minuteOfDay out of range" }
    }
}

@Serializable
data class SnoozeSettings(val minutes: Int, val maxCount: Int) {
    init {
        require(minutes in 1..Limits.MAX_SNOOZE_MINUTES) { "snooze minutes out of range" }
        require(maxCount in 0..Limits.MAX_SNOOZE_COUNT) { "snooze count out of range" }
    }
}

@Serializable
data class Threshold(val metric: Metric, val direction: Direction, val value: Double) {
    init {
        require(value.isFinite()) { "threshold must be finite" }
    }
}

/** Alert thresholds and the alert cooldown, set on the phone and evaluated on the watch. */
@Serializable
data class AlertSettings(val thresholds: List<Threshold>, val cooldownSeconds: Int) {
    init {
        require(thresholds.size <= Limits.MAX_THRESHOLDS) { "too many thresholds" }
        require(cooldownSeconds in 0..Limits.MAX_COOLDOWN_SECONDS) { "cooldown out of range" }
    }
}

/** `/schedule`. [version] grows monotonically; the watch applies a schedule only if its version is greater. */
@Serializable
@SerialName("schedule")
data class Schedule(
    val version: Long,
    val reminders: List<Reminder>,
    val checkpoints: List<Checkpoint>,
    val snooze: SnoozeSettings,
    val alerts: AlertSettings
) : Message {
    init {
        require(version >= 0) { "version must not be negative" }
        require(reminders.size <= Limits.MAX_REMINDERS) { "too many reminders" }
        require(checkpoints.size <= Limits.MAX_CHECKPOINTS) { "too many checkpoints" }
    }
}

@Serializable
data class PromptOption(val key: String, val label: String) {
    init {
        requireText(key, Limits.MAX_KEY_LENGTH, "option key")
        requireText(label, Limits.MAX_TEXT_LENGTH, "option label")
    }
}

/** `/prompt/<id>`. [expiresAt] is epoch milliseconds on the phone clock, or null for no expiry. */
@Serializable
@SerialName("prompt")
data class Prompt(val title: String, val options: List<PromptOption>, val expiresAt: Long? = null) : Message {
    init {
        requireText(title, Limits.MAX_TEXT_LENGTH, "prompt title")
        require(options.size in 1..Limits.MAX_OPTIONS) { "options must be 1 to ${Limits.MAX_OPTIONS}" }
        require(options.map { it.key }.toSet().size == options.size) { "option keys must be unique" }
    }
}

/** `/ack/<id>`. [at] is the time of the user's action in epoch milliseconds on the watch clock. */
@Serializable
@SerialName("ack")
data class Acknowledgement(val action: AckAction, val at: Long) : Message

/** `/selection/<id>`. [key] is the chosen option key of the prompt with the same id. */
@Serializable
@SerialName("selection")
data class Selection(val key: String, val at: Long) : Message {
    init {
        requireText(key, Limits.MAX_KEY_LENGTH, "selection key")
    }
}

/** `/alert/<id>`. */
@Serializable
@SerialName("alert")
data class Alert(val metric: Metric, val value: Double, val threshold: Double, val at: Long) : Message {
    init {
        require(value.isFinite() && threshold.isFinite()) { "alert numbers must be finite" }
    }
}

/** `/hello/phone` or `/hello/watch`. */
@Serializable
@SerialName("hello")
data class Hello(val side: Side, val protocolVersion: String, val appVersion: String) : Message {
    init {
        requireText(protocolVersion, Limits.MAX_KEY_LENGTH, "protocol version")
        requireText(appVersion, Limits.MAX_KEY_LENGTH, "app version")
    }
}

@Serializable
data class BiometricSample(val at: Long, val heartRate: Double)

/** `/debug/biometrics` (debug builds only). */
@Serializable
@SerialName("biometrics")
data class BiometricSamples(val samples: List<BiometricSample>) : Message {
    init {
        require(samples.size <= Limits.MAX_SAMPLES) { "too many samples" }
    }
}

internal const val MINUTES_PER_DAY = 1440
internal const val DAYS_PER_WEEK = 7

private fun requireText(value: String, max: Int, what: String) {
    require(value.isNotBlank() && value.length <= max) { "$what must be 1 to $max characters" }
}

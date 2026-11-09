package com.shlomoa.myadhdpal.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The body of an item. Every model validates its limits on construction, so a decoded body is always legal. */
sealed interface Message

@Serializable
enum class Metric { HEART_RATE }

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

/**
 * The alert cooldown and the heart-rate threshold, set on the phone and evaluated on the watch. The threshold is
 * relative (since protocol 1.2): the resting baseline plus [offsetBpm], and [initialBpm] until the watch has a
 * baseline. Both have defaults, so a 1.1 body without them still decodes. Protocol 1.3 dropped the absolute
 * thresholds list; a body that still has one decodes and the list is ignored.
 */
@Serializable
data class AlertSettings(
    val cooldownSeconds: Int,
    val offsetBpm: Int = AlertRules.DEFAULT_OFFSET_BPM,
    val initialBpm: Int = AlertRules.DEFAULT_INITIAL_BPM
) {
    init {
        require(cooldownSeconds in 0..Limits.MAX_COOLDOWN_SECONDS) { "cooldown out of range" }
        require(offsetBpm in AlertRules.MIN_OFFSET_BPM..AlertRules.MAX_OFFSET_BPM) { "offset out of range" }
        require(initialBpm in AlertRules.MIN_BPM..AlertRules.MAX_INITIAL_BPM) { "initial threshold out of range" }
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

/**
 * `/ack/<id>`. [at] is the time of the user's action in epoch milliseconds on the watch clock. Since protocol 1.1,
 * [reminderId] (the `Reminder.id` the ack answers) and [occurrenceAt] (the scheduled fire time it answers, epoch
 * milliseconds on the watch clock, so snoozes and repeated days are distinguishable) are optional; a 1.0 body without
 * them still decodes. The `<id>` of the path stays a fresh UUID per ack event.
 */
@Serializable
@SerialName("ack")
data class Acknowledgement(
    val action: AckAction,
    val at: Long,
    val reminderId: String? = null,
    val occurrenceAt: Long? = null
) : Message {
    init {
        if (reminderId != null) requireText(reminderId, Limits.MAX_KEY_LENGTH, "reminder id")
        require(occurrenceAt == null || occurrenceAt >= 0) { "occurrenceAt must not be negative" }
    }
}

/** `/selection/<id>`. [key] is the chosen option key of the prompt with the same id. */
@Serializable
@SerialName("selection")
data class Selection(val key: String, val at: Long) : Message {
    init {
        requireText(key, Limits.MAX_KEY_LENGTH, "selection key")
    }
}

/**
 * `/alert/<id>`. Since protocol 1.2, [level] is 1 (first breach) or 2 (escalation, the stronger alert; the default 1
 * is what a 1.1 body without it means) and [episodeId] groups all alerts of one episode, so [EpisodeEnd] can name it.
 */
@Serializable
@SerialName("alert")
data class Alert(
    val metric: Metric,
    val value: Double,
    val threshold: Double,
    val at: Long,
    val level: Int = AlertRules.LEVEL_1,
    val episodeId: String? = null
) : Message {
    init {
        require(value.isFinite() && threshold.isFinite()) { "alert numbers must be finite" }
        require(level in AlertRules.LEVEL_1..AlertRules.LEVEL_2) { "alert level must be 1 or 2" }
        require(episodeId == null || Paths.isValidId(episodeId)) { "episode id must be a canonical UUID" }
    }
}

/**
 * `/episode-end/<id>`, phone to watch: the user tapped "I'm OK" on the phone. [episodeId] is the `episodeId` of the
 * alert answered; the watch ends that episode and ignores the item when it is unknown or already over. [at] is epoch
 * milliseconds on the phone clock.
 */
@Serializable
@SerialName("episode-end")
data class EpisodeEnd(val episodeId: String, val at: Long) : Message {
    init {
        require(Paths.isValidId(episodeId)) { "episode id must be a canonical UUID" }
        require(at >= 0) { "at must not be negative" }
    }
}

/** Whether the watch has the heart-rate permission. */
@Serializable
enum class HeartRatePermission { GRANTED, DENIED, UNKNOWN }

/**
 * `/status/watch`, watch to phone, overwritten, not urgent: the resting [baselineBpm] and the resulting [thresholdBpm]
 * when known, and the permission state, for the phone's settings screen.
 */
@Serializable
@SerialName("watch-status")
data class WatchStatus(
    val baselineBpm: Int? = null,
    val thresholdBpm: Int? = null,
    val heartRatePermission: HeartRatePermission
) : Message {
    init {
        require(baselineBpm == null || baselineBpm in AlertRules.MIN_BPM..AlertRules.MAX_STATUS_BPM) {
            "baseline out of range"
        }
        require(thresholdBpm == null || thresholdBpm in AlertRules.MIN_BPM..AlertRules.MAX_STATUS_BPM) {
            "threshold out of range"
        }
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

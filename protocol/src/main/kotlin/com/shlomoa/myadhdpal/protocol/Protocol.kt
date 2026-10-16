package com.shlomoa.myadhdpal.protocol

import kotlinx.serialization.Serializable

/** Protocol version. The major version is the compatibility boundary (see `docs/protocol.md`). */
object Protocol {
    const val MAJOR_VERSION = 1
    const val VERSION = "1.2"
}

/** Keys of a Data Layer item. The `DataMap` adapters in `:wear` and `:phone` use exactly these. */
object ItemKeys {
    const val VERSION = "v"
    const val ID = "id"
    const val TIMESTAMP = "ts"
    const val BODY = "body"
}

/** Content limits. Together they keep every item far below the 100 KB DataItem limit. */
object Limits {
    const val MAX_REMINDERS = 100
    const val MAX_CHECKPOINTS = 24
    const val MAX_THRESHOLDS = 8
    const val MAX_OPTIONS = 6
    const val MAX_TEXT_LENGTH = 200
    const val MAX_KEY_LENGTH = 32
    const val MAX_SNOOZE_MINUTES = 240
    const val MAX_SNOOZE_COUNT = 10
    const val MAX_COOLDOWN_SECONDS = 86_400
    const val MAX_SAMPLES = 500
}

/** Decided values of the biometric alerts (`docs/plans/biometrics.md`, issue 55) that phone and watch share. */
object AlertRules {
    const val DEFAULT_COOLDOWN_SECONDS = 300
    const val DEFAULT_OFFSET_BPM = 30
    const val MIN_OFFSET_BPM = 20
    const val MAX_OFFSET_BPM = 50
    const val DEFAULT_INITIAL_BPM = 110
    const val MIN_BPM = 30
    const val MAX_INITIAL_BPM = 220
    const val MAX_STATUS_BPM = 250
    const val ESCALATION_REPEAT_SECONDS = 120
    const val MAX_LEVEL2_REPEATS = 6
    const val LEVEL_1 = 1
    const val LEVEL_2 = 2
}

/** Which device wrote a hello item. */
@Serializable
enum class Side { PHONE, WATCH }

package com.shlomoa.myadhdpal.wear.debug.probe

/**
 * Debug only. The delivery variants of the M2 probe (milestone M2: readings that keep arriving with the screen off).
 * [MEASURE] is the plain live route of E1; [WAKELOCK] adds a partial wake lock held by the service (a control only: the
 * Wear power guide says to avoid wake locks); [EXERCISE] reads heart rate through an exercise session; [EXERCISE_5S]
 * is the same with the 5 second heart-rate reporting that Health Services documents for exercises while the screen is off.
 * Chosen with `adb shell am start -n .../ProbeMeasureActivity --es mode <wire>`.
 */
enum class ProbeMode(
    val wire: String,
    val wakeLock: Boolean = false,
    val exercise: Boolean = false,
    val fiveSeconds: Boolean = false
) {
    MEASURE("measure"),
    WAKELOCK("wakelock", wakeLock = true),
    EXERCISE("exercise", exercise = true),
    EXERCISE_5S("exercise5s", exercise = true, fiveSeconds = true);

    /** The live route's callback is used by every variant that is not an exercise session. */
    val measureClient: Boolean get() = !exercise

    fun logLine(): String = "PROBE measure mode=$wire"
}

/** The mode named [text]; the plain live route when it is missing or unknown. */
fun probeModeOf(text: String?): ProbeMode = ProbeMode.entries.firstOrNull { it.wire == text } ?: ProbeMode.MEASURE

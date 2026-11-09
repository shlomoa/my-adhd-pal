package com.shlomoa.myadhdpal.wear.debug.probe

private const val API_WEAR_OS_6 = 36
private const val API_BACKGROUND_SENSORS = 33
private const val BODY_SENSORS = "android.permission.BODY_SENSORS"
private const val BODY_SENSORS_BACKGROUND = "android.permission.BODY_SENSORS_BACKGROUND"
private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
private const val READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

/**
 * The permissions passive heart rate needs (`docs/research/watch.md` section 4), foreground first: the health ones on
 * Wear OS 6 (API 36) and later, `BODY_SENSORS` and its background permission on API 33 to 35, `BODY_SENSORS` below.
 */
fun requiredPermissions(sdkInt: Int): List<String> = when {
    sdkInt >= API_WEAR_OS_6 -> listOf(READ_HEART_RATE, READ_HEALTH_DATA_IN_BACKGROUND)
    sdkInt >= API_BACKGROUND_SENSORS -> listOf(BODY_SENSORS, BODY_SENSORS_BACKGROUND)
    else -> listOf(BODY_SENSORS)
}

fun missingPermissions(required: List<String>, isGranted: (String) -> Boolean): List<String> =
    required.filterNot(isGranted)

/** One line per permission: `PROBE permission granted: <name>` or `PROBE permission missing: <name>`. */
fun permissionReport(required: List<String>, isGranted: (String) -> Boolean): List<String> =
    required.map { "PROBE permission ${if (isGranted(it)) "granted" else "missing"}: $it" }

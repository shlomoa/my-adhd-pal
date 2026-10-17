package com.shlomoa.myadhdpal.wear.debug.probe

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.view.Display
import java.io.File

/** The live probe's record in `filesDir`: a line per reading and per availability change, and the run's start. */
object MeasureLog {
    private val lock = Any()

    private fun file(context: Context, name: String) = File(context.filesDir, name)

    /** The state of the display now: on, doze (always-on display), off or other. */
    fun displayNow(context: Context): String = displayName(
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state
            ?: Display.STATE_UNKNOWN
    )

    fun batteryPercent(context: Context): Int =
        context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    /** Starts a new run: forgets the old one and notes when it began and the battery level. */
    fun begin(context: Context, elapsedMs: Long, batteryPercent: Int) = synchronized(lock) {
        clear(context)
        file(context, START).writeText("$elapsedMs|$batteryPercent")
    }

    /** The elapsed-realtime start and the battery percent of the current run, or null when none was started. */
    fun start(context: Context): Pair<Long, Int>? = synchronized(lock) {
        val parts = file(context, START).takeIf { it.exists() }?.readText()?.split("|").orEmpty()
        val elapsed = parts.getOrNull(0)?.toLongOrNull()
        val battery = parts.getOrNull(1)?.toIntOrNull()
        if (elapsed != null && battery != null) elapsed to battery else null
    }

    fun record(context: Context, reading: MeasureReading) = synchronized(lock) {
        file(context, READINGS).appendText(reading.toLine() + "\n")
    }

    fun recordAvailability(context: Context, event: AvailabilityEvent) = synchronized(lock) {
        file(context, AVAILABILITY).appendText(event.toLine() + "\n")
    }

    fun readings(context: Context): List<MeasureReading> = synchronized(lock) {
        file(context, READINGS).takeIf { it.exists() }?.readLines().orEmpty().mapNotNull(::parseMeasureReading)
    }

    fun availability(context: Context): List<AvailabilityEvent> = synchronized(lock) {
        file(context, AVAILABILITY).takeIf { it.exists() }?.readLines().orEmpty().mapNotNull(::parseAvailability)
    }

    fun clear(context: Context) = synchronized(lock) {
        listOf(READINGS, AVAILABILITY, START).forEach { file(context, it).delete() }
    }

    private const val READINGS = "measure.log"
    private const val AVAILABILITY = "measure-availability.log"
    private const val START = "measure-start.txt"
}

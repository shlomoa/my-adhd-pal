package com.shlomoa.myadhdpal.wear.debug.probe

import android.content.Context
import android.util.Log
import java.io.File

/** The probe's record: one line per delivery in `filesDir/probe.log` (counts and times only), and `PROBE` in logcat. */
object ProbeLog {
    const val TAG = "PROBE"
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, "probe.log")

    fun note(message: String) {
        Log.i(TAG, message)
    }

    fun record(context: Context, delivery: ProbeDelivery) = synchronized(lock) {
        file(context).appendText(delivery.toLine() + "\n")
    }

    fun deliveries(context: Context): List<ProbeDelivery> = synchronized(lock) {
        file(context).takeIf { it.exists() }?.readLines().orEmpty().mapNotNull(::parseDelivery)
    }

    fun clear(context: Context) = synchronized(lock) { file(context).delete() }
}

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

    fun record(context: Context, batch: ProbeBatch) = synchronized(lock) {
        file(context).appendText(batch.toLine() + "\n")
    }

    fun batches(context: Context): List<ProbeBatch> = synchronized(lock) {
        file(context).takeIf { it.exists() }?.readLines().orEmpty().mapNotNull(::parseBatch)
    }

    fun clear(context: Context) = synchronized(lock) { file(context).delete() }
}

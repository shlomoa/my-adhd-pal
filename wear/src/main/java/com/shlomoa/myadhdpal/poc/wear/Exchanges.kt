package com.shlomoa.myadhdpal.poc.wear

import android.content.Context

/**
 * Every ping/pong exchange of the current run, per path, as `t0,phoneNanos,t3` in `exchanges-<label>-<path>.csv`.
 * The test script computes the clock offset from the exchange with the shortest round trip of the whole run, which is
 * more accurate than the estimate available while the run is still going.
 */
object Exchanges {
    @Volatile
    var label: String = ""

    fun log(context: Context, path: String, t0: Long, phoneNanos: Long, t3: Long) {
        val current = label
        if (current.isNotEmpty()) PocFiles.append(context, "exchanges-$current-$path.csv", "$t0,$phoneNanos,$t3")
    }
}

package com.shlomoa.myadhdpal.poc.phone

import android.content.Context
import android.os.SystemClock
import com.shlomoa.myadhdpal.poc.core.LiveFormat
import com.shlomoa.myadhdpal.poc.core.LiveReading
import com.shlomoa.myadhdpal.poc.core.LiveStats
import com.shlomoa.myadhdpal.poc.core.Sample
import java.io.File

/** What the screen shows, plus the CSV logs. Result files are read by the test script with `adb shell run-as`. */
object PhoneState {
    val live = LiveStats()

    @Volatile
    var received: Long = 0

    @Volatile
    var receivedBt: Long = 0

    @Volatile
    var receivedDl: Long = 0

    @Volatile
    var bluetoothText: String = "Bluetooth: starting..."

    @Volatile
    private var lastLogNanos = 0L

    /** Logs one reading that arrived over [path] (`dl` = Data Layer, `bt` = direct Bluetooth). */
    fun record(context: Context, path: String, sample: Sample, arrivalNanos: Long) {
        received++
        if (path == "bt") receivedBt++ else receivedDl++
        val latency = if (sample.offsetNanos == Sample.NO_OFFSET) {
            null
        } else {
            (arrivalNanos - (sample.sourceNanos + sample.offsetNanos)) / NANOS_PER_MS
        }
        live.add(
            LiveReading(
                sample.sourceNanos,
                arrivalNanos,
                sample.bpm,
                sample.accuracy,
                latency,
                path,
                sample.route,
                sample.screen
            )
        )
        // label,seq,bpm,sourceNanos,sendNanos,offsetNanos,phoneArrivalNanos,screen,accuracy
        append(
            context,
            "phone-${sample.label}-$path.csv",
            "${sample.label},${sample.seq},${sample.bpm},${sample.sourceNanos},${sample.sendNanos}," +
                "${sample.offsetNanos},$arrivalNanos,${sample.screen},${sample.accuracy}"
        )
        // What the live indicator shows, once every 5 s, so the test report can state it.
        if (arrivalNanos - lastLogNanos >= LOG_EVERY_NANOS) {
            lastLogNanos = arrivalNanos
            append(
                context,
                "live-log.txt",
                "${SystemClock.elapsedRealtime() / MS_PER_S}s " +
                    LiveFormat.logLine(live.snapshot(arrivalNanos))
            )
        }
    }

    fun event(context: Context, text: String) {
        bluetoothText = "Bluetooth: $text"
        append(context, "events.txt", "${SystemClock.elapsedRealtime() / MS_PER_S}s $text")
    }

    @Synchronized
    fun append(context: Context, name: String, line: String) {
        File(context.filesDir, name).appendText(line + "\n")
    }

    @Synchronized
    fun write(context: Context, name: String, text: String) {
        File(context.filesDir, name).writeText(text)
    }

    private const val MS_PER_S = 1000L
    private const val NANOS_PER_MS = 1_000_000.0
    private const val LOG_EVERY_NANOS = 5_000_000_000L
}

package com.shlomoa.myadhdpal.poc.phone

import android.content.Context
import android.os.SystemClock
import com.shlomoa.myadhdpal.poc.core.Sample
import java.io.File

/** What the screen shows, plus the CSV logs. Result files are read by the test script with `adb shell run-as`. */
object PhoneState {
    @Volatile
    var lastText: String = "waiting for the watch..."

    @Volatile
    var received: Long = 0

    @Volatile
    var bluetoothText: String = "Bluetooth: starting..."

    /** Logs one reading that arrived over [path] (`dl` = Data Layer, `bt` = direct Bluetooth). */
    fun record(context: Context, path: String, sample: Sample, arrivalNanos: Long) {
        received++
        lastText = "${sample.bpm.toInt()} bpm (${sample.label} #${sample.seq} via $path)"
        // label,seq,bpm,sourceNanos,sendNanos,offsetNanos,phoneArrivalNanos,screen
        append(
            context,
            "phone-${sample.label}-$path.csv",
            "${sample.label},${sample.seq},${sample.bpm},${sample.sourceNanos},${sample.sendNanos}," +
                "${sample.offsetNanos},$arrivalNanos,${sample.screen}"
        )
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
}

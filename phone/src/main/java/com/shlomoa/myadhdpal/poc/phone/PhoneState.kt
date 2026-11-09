package com.shlomoa.myadhdpal.poc.phone

import android.content.Context
import java.io.File

/** What the screen shows, plus the CSV log. Result files are read by the test script with `adb shell run-as`. */
object PhoneState {
    @Volatile
    var lastText: String = "waiting for the watch..."

    @Volatile
    var received: Long = 0

    @Synchronized
    fun append(context: Context, name: String, line: String) {
        File(context.filesDir, name).appendText(line + "\n")
    }

    @Synchronized
    fun write(context: Context, name: String, text: String) {
        File(context.filesDir, name).writeText(text)
    }
}

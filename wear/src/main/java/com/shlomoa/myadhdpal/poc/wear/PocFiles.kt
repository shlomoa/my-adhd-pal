package com.shlomoa.myadhdpal.poc.wear

import android.content.Context
import java.io.File

/** Result files live in the app's private files dir; the test script reads them with `adb shell run-as`. */
object PocFiles {
    @Synchronized
    fun append(context: Context, name: String, line: String) {
        File(context.filesDir, name).appendText(line + "\n")
    }

    @Synchronized
    fun write(context: Context, name: String, text: String) {
        File(context.filesDir, name).writeText(text)
    }
}

package com.shlomoa.myadhdpal.wear.debug

import android.util.Log
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.log.LogLevel
import com.shlomoa.myadhdpal.wear.log.LogSink
import com.shlomoa.myadhdpal.wear.log.Logger

/** Installs the debug log sink; registered only by the debug manifest. */
class DebugApplication : WatchApplication() {
    override fun onCreate() {
        super.onCreate()
        Logger.sink = DebugLog.sink
    }
}

object DebugLog {
    private val logcat =
        LogSink { level, tag, message ->
            when (level) {
                LogLevel.Debug -> Log.d(tag, message)
                LogLevel.Info -> Log.i(tag, message)
                LogLevel.Warn -> Log.w(tag, message)
                LogLevel.Error -> Log.e(tag, message)
            }
        }

    val sink = RingBufferLogSink(mirror = logcat)
}

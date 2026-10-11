package com.shlomoa.myadhdpal.wear.log

enum class LogLevel { Debug, Info, Warn, Error }

fun interface LogSink {
    fun log(level: LogLevel, tag: String, message: String)
}

/**
 * App-wide logging facade. Release builds never install a sink, so nothing is logged; debug builds install one from
 * the `debug` source set. Never pass raw biometric values here from main code (plan.md section 2).
 */
object Logger {
    @Volatile
    var sink: LogSink? = null

    fun d(tag: String, message: String) = sink?.log(LogLevel.Debug, tag, message)

    fun i(tag: String, message: String) = sink?.log(LogLevel.Info, tag, message)

    fun w(tag: String, message: String) = sink?.log(LogLevel.Warn, tag, message)

    fun e(tag: String, message: String) = sink?.log(LogLevel.Error, tag, message)
}

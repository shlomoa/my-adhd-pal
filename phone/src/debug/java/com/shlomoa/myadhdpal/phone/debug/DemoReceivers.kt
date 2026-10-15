package com.shlomoa.myadhdpal.phone.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun logLine(line: String) {
    Log.i(DemoActions.LOG_TAG, line)
}

/** Runs [block] off the main thread and keeps the broadcast alive until it ends. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending?.finish()
        }
    }
}

/** Builds the commands from the app's real repositories and runs one for the expected action. */
abstract class DemoReceiver(private val action: String) : BroadcastReceiver() {
    @Inject lateinit var tasks: TaskRepository

    @Inject lateinit var schedule: ScheduleRepository

    @Inject lateinit var history: HistoryRepository

    @Inject lateinit var clock: Clock

    protected abstract suspend fun run(intent: Intent, commands: DemoCommands)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != action) return
        val commands = DemoCommands(DemoSchedulePublisher(tasks, schedule, clock), history, ::logLine)
        async { run(intent, commands) }
    }
}

/**
 * `adb shell am broadcast -a com.shlomoa.myadhdpal.debug.PUBLISH_DEMO_SCHEDULE
 *   -n com.shlomoa.myadhdpal/.phone.debug.DemoScheduleReceiver --ei minutes_ahead 2 --es title "..."`
 */
@AndroidEntryPoint
class DemoScheduleReceiver : DemoReceiver(DemoActions.PUBLISH_DEMO_SCHEDULE) {
    override suspend fun run(intent: Intent, commands: DemoCommands) {
        val minutes = if (intent.hasExtra(DemoActions.EXTRA_MINUTES_AHEAD)) {
            intent.getIntExtra(DemoActions.EXTRA_MINUTES_AHEAD, 0)
        } else {
            null
        }
        commands.publishSchedule(minutes, intent.getStringExtra(DemoActions.EXTRA_TITLE))
    }
}

/**
 * `adb shell am broadcast -a com.shlomoa.myadhdpal.debug.DUMP_HISTORY
 *   -n com.shlomoa.myadhdpal/.phone.debug.DemoHistoryReceiver`
 */
@AndroidEntryPoint
class DemoHistoryReceiver : DemoReceiver(DemoActions.DUMP_HISTORY) {
    override suspend fun run(intent: Intent, commands: DemoCommands) = commands.dumpHistory()
}

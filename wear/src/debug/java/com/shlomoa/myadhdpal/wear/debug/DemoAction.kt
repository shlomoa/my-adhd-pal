package com.shlomoa.myadhdpal.wear.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.reminder.ActionOutcome
import com.shlomoa.myadhdpal.wear.reminder.ReminderAction
import com.shlomoa.myadhdpal.wear.scheduler.FiredReminder
import java.time.Instant

/** Applies the action called [actionName] to [fired] as a tap would; null if there is nothing to do. */
fun performDemoAction(
    actionName: String?,
    fired: FiredReminder?,
    handle: (ReminderAction, String, Int, Instant) -> ActionOutcome
): ActionOutcome? {
    val action = ReminderAction.entries.firstOrNull { it.name == actionName }
    return if (action == null || fired == null) {
        null
    } else {
        handle(action, fired.reminder.id, fired.snoozeCount, fired.occurrenceAt)
    }
}

/**
 * Debug-only, for the demo script: does what a tap on the last reminder notification would do, without a tap:
 * `adb shell am broadcast -n com.shlomoa.myadhdpal/.wear.debug.DemoActionReceiver`
 * followed by `--es action DONE` (DONE, SNOOZE, START_2_MIN or DISMISS).
 */
class DemoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as WatchApplication).graph
        performDemoAction(intent.getStringExtra("action"), graph.lastFired, graph.actionHandler::handle)
    }
}

package com.shlomoa.myadhdpal.wear.tile

import android.app.Activity
import android.os.Bundle
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.log.Logger
import com.shlomoa.myadhdpal.wear.reminder.ReminderAction

/**
 * Opened by the tile button, does the action and closes at once. The tile can only start an exported activity, so any
 * app could start this one: the extras are validated and only a reminder that is in the schedule is acted on.
 */
class TileActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        val action = TileAction.fromExtras(
            mapOf(
                TileAction.EXTRA_REMINDER_ID to extras?.getString(TileAction.EXTRA_REMINDER_ID),
                TileAction.EXTRA_OCCURRENCE_AT to extras?.getString(TileAction.EXTRA_OCCURRENCE_AT)
            )
        )
        val graph = (application as WatchApplication).graph
        val known = graph.scheduleStore.load()?.schedule?.reminders?.any { it.id == action?.reminderId } == true
        if (action != null && known) {
            graph.actionHandler.handle(ReminderAction.START_2_MIN, action.reminderId, 0, action.occurrenceAt)
        } else {
            Logger.w(TAG, "tile action ignored: malformed extras or unknown reminder")
        }
        finish()
    }

    private companion object {
        const val TAG = "TileActionActivity"
    }
}

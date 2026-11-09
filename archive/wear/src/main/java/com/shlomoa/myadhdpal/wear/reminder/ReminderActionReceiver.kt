package com.shlomoa.myadhdpal.wear.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.log.Logger
import java.time.Instant

/** Receives a tap on one of the notification actions and hands it to the [ReminderActionHandler]. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)?.let { name ->
            ReminderAction.entries.firstOrNull {
                it.name ==
                    name
            }
        }
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val occurrenceAt = intent.getLongExtra(EXTRA_OCCURRENCE_AT, NO_TIME)
        if (action == null || reminderId == null || occurrenceAt == NO_TIME) {
            Logger.w(TAG, "action intent without action, reminder id or occurrence time")
            return
        }
        val graph = (context.applicationContext as WatchApplication).graph
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        val outcome = graph.actionHandler.handle(action, reminderId, snoozeCount, Instant.ofEpochMilli(occurrenceAt))
        if (outcome is ActionOutcome.SnoozeDenied) {
            graph.haptics.play(Haptic.WARNING)
        } else {
            context.getSystemService(
                NotificationManager::class.java
            ).cancel(ReminderNotifier.notificationId(reminderId))
        }
    }

    companion object {
        private const val TAG = "ReminderAction"
        private const val EXTRA_ACTION = "reminder_action"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_SNOOZE_COUNT = "snooze_count"
        private const val EXTRA_OCCURRENCE_AT = "occurrence_at"
        private const val NO_TIME = -1L

        fun intent(
            context: Context,
            action: ReminderAction,
            reminderId: String,
            snoozeCount: Int,
            occurrenceAt: Instant
        ): Intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction("com.shlomoa.myadhdpal.wear.REMINDER_${action.name}")
            .putExtra(EXTRA_ACTION, action.name)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
            .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
            .putExtra(EXTRA_OCCURRENCE_AT, occurrenceAt.toEpochMilli())
    }
}

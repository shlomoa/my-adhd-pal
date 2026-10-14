package com.shlomoa.myadhdpal.wear.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shlomoa.myadhdpal.wear.log.Logger

/**
 * Receives a tap on one of the notification actions. For now it only closes the notification; the effects (snooze
 * locally and report it, start the 2-minute timer, acknowledge to the phone) are step 3.6.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)?.let { name ->
            ReminderAction.entries.firstOrNull {
                it.name ==
                    name
            }
        }
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        if (action == null || reminderId == null) {
            Logger.w(TAG, "action intent without action or reminder id")
            return
        }
        Logger.d(TAG, "action ${action.name} for a reminder")
        context.getSystemService(NotificationManager::class.java).cancel(ReminderNotifier.notificationId(reminderId))
    }

    companion object {
        private const val TAG = "ReminderAction"
        private const val EXTRA_ACTION = "reminder_action"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_SNOOZE_COUNT = "snooze_count"

        fun intent(context: Context, action: ReminderAction, reminderId: String, snoozeCount: Int): Intent =
            Intent(context, ReminderActionReceiver::class.java)
                .setAction("com.shlomoa.myadhdpal.wear.REMINDER_${action.name}")
                .putExtra(EXTRA_ACTION, action.name)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
    }
}

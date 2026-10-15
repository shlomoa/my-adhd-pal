package com.shlomoa.myadhdpal.wear.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.demo.DemoLog
import com.shlomoa.myadhdpal.wear.log.Logger
import com.shlomoa.myadhdpal.wear.reminder.Haptic
import java.time.Instant

/** The reminder alarm went off: show the reminder (if it is still scheduled) and arm the next alarm. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val atMillis = intent.getLongExtra(EXTRA_AT, NO_TIME)
        if (reminderId == null || atMillis == NO_TIME) {
            Logger.w(TAG, "alarm intent without reminder id or time")
            return
        }
        val graph = (context.applicationContext as WatchApplication).graph
        graph.scheduler.onAlarm(reminderId, Instant.ofEpochMilli(atMillis))?.let {
            graph.lastFired = it
            DemoLog.reminderFired(it)
            graph.notifier.show(it, Haptic.TICK)
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val ACTION = "com.shlomoa.myadhdpal.wear.REMINDER_ALARM"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_AT = "at"
        private const val NO_TIME = -1L

        /** The intent of the alarm; without arguments it matches any alarm of the app, for cancelling. */
        fun intent(context: Context, reminderId: String? = null, at: Instant? = null): Intent =
            Intent(context, AlarmReceiver::class.java).setAction(ACTION).apply {
                if (reminderId != null) putExtra(EXTRA_REMINDER_ID, reminderId)
                if (at != null) putExtra(EXTRA_AT, at.toEpochMilli())
            }
    }
}

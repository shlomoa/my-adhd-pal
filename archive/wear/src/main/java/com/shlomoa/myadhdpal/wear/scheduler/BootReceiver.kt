package com.shlomoa.myadhdpal.wear.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shlomoa.myadhdpal.wear.WatchApplication

/**
 * Alarms do not survive a reboot, and a changed clock, time zone, app update or exact-alarm permission changes when
 * reminders are due, so all of these rebuild the alarm from the stored schedule. Other actions are ignored.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in REARM_ACTIONS) return
        (context.applicationContext as WatchApplication).graph.scheduler.rearm()
    }

    private companion object {
        /** The string of `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, which needs API 31. */
        const val EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        val REARM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            EXACT_ALARM_PERMISSION_CHANGED
        )
    }
}

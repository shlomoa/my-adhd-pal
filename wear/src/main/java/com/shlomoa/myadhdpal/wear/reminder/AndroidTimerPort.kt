package com.shlomoa.myadhdpal.wear.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.shlomoa.myadhdpal.wear.WatchApplication

/**
 * The "Start 2 min" countdown on the platform alarm (it survives the screen turning off). It uses its own request code
 * so that it never replaces the reminder alarm, and an inexact alarm because a few seconds do not matter here.
 */
class AndroidTimerPort(private val context: Context) : TimerPort {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun start(reminderId: String, seconds: Int) {
        val intent = Intent(context, TimerReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val triggerAt = SystemClock.elapsedRealtime() + seconds * MILLIS_PER_SECOND
        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
    }

    companion object {
        const val ACTION = "com.shlomoa.myadhdpal.wear.TIMER_DONE"
        private const val REQUEST_CODE = 1
        private const val MILLIS_PER_SECOND = 1000L
    }
}

/** The two minutes are over: a calm haptic says so. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidTimerPort.ACTION) return
        (context.applicationContext as WatchApplication).graph.haptics.play(Haptic.CALM_BREATH)
    }
}

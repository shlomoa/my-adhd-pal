package com.shlomoa.myadhdpal.wear.debug.probe

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Debug only: the two routes that might get a vibration through Do Not Disturb, to test on the watch
 * (`docs/plans/biometrics.md` section 4):
 * - [notifyAlarm]: an alarm-category notification on a channel that asks to bypass Do Not Disturb, with a full-screen
 *   intent;
 * - [scheduleAlarmClock]: an alarm clock (`AlarmManager.setAlarmClock`) whose receiver vibrates with alarm usage.
 */
object ProbeAlarmRoutes {
    private const val CHANNEL = "probe_alarm"
    private const val NOTIFICATION_ID = 9001
    private const val ALARM_REQUEST = 9002
    private const val OPEN_REQUEST = 9003
    private const val ON_MS = 800L
    private const val OFF_MS = 150L
    const val ALARM_DELAY_MS = 15_000L

    fun notifyAlarm(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Probe alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, ON_MS, OFF_MS, ON_MS, OFF_MS, ON_MS)
            setSound(null, null)
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context,
            OPEN_REQUEST,
            Intent(context, ProbeAlertActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Probe alert")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        ProbeLog.note(
            "notify-alarm posted channelBypassDnd=${manager.getNotificationChannel(CHANNEL).canBypassDnd()} " +
                "policyAccess=${manager.isNotificationPolicyAccessGranted} " +
                "canUseFullScreenIntent=${canUseFullScreenIntent(manager)}"
        )
    }

    /** The check exists from API 34; below it the permission is granted at install. */
    private fun canUseFullScreenIntent(manager: NotificationManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

    fun scheduleAlarmClock(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + ALARM_DELAY_MS
        val fire = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST,
            Intent(context, ProbeAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val show = PendingIntent.getActivity(
            context,
            OPEN_REQUEST,
            Intent(context, ProbeAlertActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        ProbeLog.note("alarm-clock set for ${ALARM_DELAY_MS / MILLIS_PER_SECOND}s from now")
    }

    private const val MILLIS_PER_SECOND = 1000L
}

/** Debug only: fires the alarm clock set by [ProbeAlarmRoutes.scheduleAlarmClock] and vibrates with alarm usage. */
class ProbeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ProbeLog.note("alarm-clock fired")
        VibrationProbe.strong(context)
    }
}

/** Debug only: the full-screen target of the probe notification and alarm clock. */
class ProbeAlertActivity : ComponentActivity() {
    @SuppressLint("SetTextI18n") // debug-only screen, never shown to users and never translated
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(
            TextView(this).apply {
                text = "Probe alert\nTap to close"
                textSize = TEXT_SIZE
                setOnClickListener { finish() }
            }
        )
        ProbeLog.note("alert screen shown")
    }

    private companion object {
        const val TEXT_SIZE = 28f
    }
}

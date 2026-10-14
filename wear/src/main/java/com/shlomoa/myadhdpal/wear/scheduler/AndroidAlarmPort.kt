package com.shlomoa.myadhdpal.wear.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import java.time.Instant

/**
 * The [AlarmPort] on the platform's [AlarmManager]: one alarm for this app, delivered to [AlarmReceiver] even in Doze.
 * Exact alarms need the user's grant below API 33 (`SCHEDULE_EXACT_ALARM`) and are granted with the install from API 33
 * (`USE_EXACT_ALARM`); when they are not allowed the scheduler asks for an inexact alarm instead.
 */
class AndroidAlarmPort(private val context: Context) : AlarmPort {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    override fun set(at: Instant, reminderId: String, exact: Boolean) {
        val pending = pendingIntent(AlarmReceiver.intent(context, reminderId, at))
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    override fun cancel() = manager.cancel(pendingIntent(AlarmReceiver.intent(context)))

    private fun pendingIntent(intent: android.content.Intent): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val REQUEST_CODE = 0
    }
}

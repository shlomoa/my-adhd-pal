package com.shlomoa.myadhdpal.wear.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.shlomoa.myadhdpal.wear.R
import com.shlomoa.myadhdpal.wear.scheduler.FiredReminder

/** Shows a fired reminder as a notification with the four actions, and plays a haptic with it. */
class ReminderNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(fired: FiredReminder, haptic: Haptic = Haptic.TICK) {
        if (!manager.areNotificationsEnabled()) return
        ReminderChannel.ensureCreated(context)
        val builder = Notification.Builder(context, ReminderChannel.ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(fired.reminder.title)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
        ReminderAction.entries.forEach { builder.addAction(action(it, fired)) }
        manager.notify(notificationId(fired.reminder.id), builder.build())
        HapticPlayer(context).play(haptic)
    }

    private fun action(action: ReminderAction, fired: FiredReminder): Notification.Action {
        val intent = ReminderActionReceiver.intent(context, action, fired.reminder.id, fired.snoozeCount)
        val pending = PendingIntent.getBroadcast(
            context,
            "${fired.reminder.id}/${action.name}".hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val icon = Icon.createWithResource(context, iconRes(action))
        return Notification.Action.Builder(icon, context.getString(action.labelRes), pending).build()
    }

    private fun iconRes(action: ReminderAction): Int = when (action) {
        ReminderAction.DONE -> R.drawable.ic_action_done
        ReminderAction.SNOOZE -> R.drawable.ic_action_snooze
        ReminderAction.START_2_MIN -> R.drawable.ic_action_start
        ReminderAction.DISMISS -> R.drawable.ic_action_dismiss
    }

    companion object {
        fun notificationId(reminderId: String): Int = reminderId.hashCode()
    }
}

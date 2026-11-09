package com.shlomoa.myadhdpal.wear.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import com.shlomoa.myadhdpal.wear.R

/** The one notification channel of the app, for reminders. Creating a channel that exists is a no-op. */
object ReminderChannel {
    const val ID = "reminders"
    const val IMPORTANCE = NotificationManager.IMPORTANCE_HIGH

    @StringRes
    val NAME_RES = R.string.channel_reminders

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ID, context.getString(NAME_RES), IMPORTANCE))
    }
}

package com.shlomoa.myadhdpal.wear.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.wear.reminder.Haptic
import com.shlomoa.myadhdpal.wear.reminder.ReminderNotifier
import com.shlomoa.myadhdpal.wear.scheduler.FiredReminder
import java.time.Instant

/**
 * Debug-only. Shows a reminder notification now, with a haptic, to try the notification and the haptic catalogue:
 * `adb shell am broadcast -n com.shlomoa.myadhdpal/.wear.debug.FireReminderReceiver`
 * followed by `--es title Test --es haptic WARNING` (haptic is TICK, WARNING or CALM_BREATH).
 */
class FireReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Test reminder"
        val haptic = Haptic.entries.firstOrNull { it.name == intent.getStringExtra("haptic") } ?: Haptic.TICK
        val reminder = Reminder(id = "debug", title = title, minuteOfDay = 0, days = listOf(1))
        ReminderNotifier(context).show(FiredReminder(reminder, snoozeCount = 0, occurrenceAt = Instant.now()), haptic)
    }
}

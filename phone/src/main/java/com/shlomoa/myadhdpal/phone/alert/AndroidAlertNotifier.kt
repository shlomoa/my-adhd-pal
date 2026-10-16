package com.shlomoa.myadhdpal.phone.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.phone.data.HistoryEntry

/**
 * The phone alert on the device (`docs/phone-alert.md`): an alarm-category notification with a full-screen intent to
 * [AlertActivity], and the vibration. Both use alarm attributes, so Do Not Disturb treats them as an alarm (to be tried
 * on the A55). Two high-importance channels: with sound (the alarm sound, repeating until stopped or timed out) and
 * without. The vibration is started here, not by the channel.
 */
class AndroidAlertNotifier(private val context: Context) : AlertNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun hasAmplitudeControl(): Boolean = vibrator?.hasAmplitudeControl() == true

    override fun raise(alert: HistoryEntry.AlertRaised, plan: AlertPlan) {
        createChannels()
        val open = PendingIntent.getActivity(
            context,
            0,
            AlertActivity.intent(context, alert, plan),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, if (plan.sound) CHANNEL_SOUND else CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.alert_title))
            .setContentText(context.getString(R.string.alert_text, alert.value, alert.threshold))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .setTimeoutAfter(plan.durationMillis)
            .build()
        if (plan.sound) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(NOTIFICATION_ID, notification)
        vibrate(plan.vibration)
    }

    override fun stop() {
        manager.cancel(NOTIFICATION_ID)
        vibrator?.cancel()
    }

    private fun vibrate(plan: VibrationPlan) {
        val timings = plan.timings.toLongArray()
        val effect = plan.amplitudes?.let { VibrationEffect.createWaveform(timings, it.toIntArray(), NO_REPEAT) }
            ?: VibrationEffect.createWaveform(timings, NO_REPEAT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect, alarmAudio())
        }
    }

    private fun alarmAudio() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SOUND,
                context.getString(R.string.alert_channel_sound),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio())
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SILENT,
                context.getString(R.string.alert_channel_silent),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        const val CHANNEL_SOUND = "alerts"
        const val CHANNEL_SILENT = "alerts_silent"
        private const val NOTIFICATION_ID = 4_001
        private const val NO_REPEAT = -1
    }
}

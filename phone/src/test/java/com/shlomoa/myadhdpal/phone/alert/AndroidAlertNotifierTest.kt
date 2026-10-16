package com.shlomoa.myadhdpal.phone.alert

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.protocol.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidAlertNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val alert = HistoryEntry.AlertRaised("a-1", 1_000, Metric.HEART_RATE, 138.0, 110.0)

    @Test
    fun `TC-A-100 an alarm notification with a full-screen intent on a high-importance channel`() {
        AndroidAlertNotifier(context).raise(alert, AlertPlanner.plan(1, AlertPrefs(), true))
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(Notification.CATEGORY_ALARM, posted.category)
        assertNotNull(posted.fullScreenIntent)
        assertEquals(AndroidAlertNotifier.CHANNEL_SOUND, posted.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(posted.channelId).importance)
    }

    @Test
    fun `TC-A-100 sound off uses the silent channel and a new alert replaces the one showing`() {
        val notifier = AndroidAlertNotifier(context)
        notifier.raise(alert, AlertPlanner.plan(1, AlertPrefs(soundOn = false), true))
        notifier.raise(alert.copy(id = "a-2"), AlertPlanner.plan(2, AlertPrefs(soundOn = false), true))
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(AndroidAlertNotifier.CHANNEL_SILENT, posted.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(posted.channelId).importance)
        assertTrue(manager.getNotificationChannel(AndroidAlertNotifier.CHANNEL_SILENT).sound == null)
    }

    @Test
    fun `stop removes the notification`() {
        val notifier = AndroidAlertNotifier(context)
        notifier.raise(alert, AlertPlanner.plan(1, AlertPrefs(), true))
        notifier.stop()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `TC-A-101 the alert screen is not exported and the permissions are requested`() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
        )
        val activity = info.activities!!.single { it.name == "com.shlomoa.myadhdpal.phone.alert.AlertActivity" }
        assertFalse(activity.exported)
        val requested = info.requestedPermissions!!.toList()
        assertTrue(requested.containsAll(listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.VIBRATE"
        )))
    }
}

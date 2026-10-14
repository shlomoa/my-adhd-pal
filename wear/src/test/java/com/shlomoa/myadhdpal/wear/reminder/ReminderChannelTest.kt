package com.shlomoa.myadhdpal.wear.reminder

import android.app.NotificationManager
import com.shlomoa.myadhdpal.wear.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderChannelTest {
    @Test
    fun `TC-W-032 there is one reminder channel with high importance and a name`() {
        assertEquals("reminders", ReminderChannel.ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, ReminderChannel.IMPORTANCE)
        assertEquals(R.string.channel_reminders, ReminderChannel.NAME_RES)
    }
}

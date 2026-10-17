package com.shlomoa.myadhdpal.phone.ui.reminders

import com.shlomoa.myadhdpal.phone.data.TaskReminder
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure part of showing a reminder: its time and its days. */
class ReminderSummaryTest {
    private fun reminder(minute: Int, days: List<Int>) = TaskReminder("r", "t", minute, days)

    @Test
    fun `TC-A-120 the time is shown in 24 hours`() {
        assertEquals("09:00", reminder(540, listOf(1)).summary().time)
        assertEquals("00:00", reminder(0, listOf(1)).summary().time)
        assertEquals("23:59", reminder(1439, listOf(1)).summary().time)
        assertEquals("13:05", reminder(13 * 60 + 5, listOf(1)).summary().time)
    }

    @Test
    fun `TC-A-120 the days are every day, weekdays, or the listed days in order`() {
        assertEquals(DaysSummary.EveryDay, reminder(540, (1..7).toList()).summary().days)
        assertEquals(DaysSummary.EveryDay, reminder(540, (7 downTo 1).toList()).summary().days)
        assertEquals(DaysSummary.Weekdays, reminder(540, (1..5).toList()).summary().days)
        assertEquals(DaysSummary.Some(listOf(1, 3, 5)), reminder(540, listOf(5, 1, 3)).summary().days)
        assertEquals(DaysSummary.Some(listOf(6, 7)), reminder(540, listOf(7, 6)).summary().days)
    }
}

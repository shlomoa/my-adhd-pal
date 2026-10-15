package com.shlomoa.myadhdpal.wear.debug

import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.wear.reminder.ActionOutcome
import com.shlomoa.myadhdpal.wear.reminder.ReminderAction
import com.shlomoa.myadhdpal.wear.scheduler.FiredReminder
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoActionTest {
    private val occurrence = Instant.parse("2026-10-15T09:00:00Z")
    private val fired =
        FiredReminder(Reminder("demo", "Demo reminder", 0, listOf(1)), snoozeCount = 1, occurrenceAt = occurrence)
    private val calls = mutableListOf<List<Any>>()
    private val handle = { action: ReminderAction, id: String, snoozeCount: Int, at: Instant ->
        calls += listOf(action, id, snoozeCount, at)
        ActionOutcome.Done
    }

    @Test
    fun `TC-W-101 the demo action is applied to the last fired reminder with its occurrence and snooze count`() {
        val outcome = performDemoAction("DONE", fired, handle)
        assertEquals(ActionOutcome.Done, outcome)
        assertEquals(listOf(listOf(ReminderAction.DONE, "demo", 1, occurrence)), calls)
    }

    @Test
    fun `TC-W-101 an unknown action name or no fired reminder does nothing`() {
        assertEquals(null, performDemoAction("NOPE", fired, handle))
        assertEquals(null, performDemoAction("DONE", null, handle))
        assertEquals(emptyList<List<Any>>(), calls)
    }
}

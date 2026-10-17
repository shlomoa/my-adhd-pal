package com.shlomoa.myadhdpal.wear.tile

import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.wear.link.LinkView
import com.shlomoa.myadhdpal.wear.scheduler.SnoozeEntry
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileModelTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-14T08:00:00Z")
    private val everyDay = listOf(1, 2, 3, 4, 5, 6, 7)
    private val water = Reminder("water", "Drink water", 8 * 60 + 30, everyDay)
    private val meds = Reminder("meds", "Take medication", 9 * 60, everyDay)
    private val link = LinkView(LinkState.RELAYED, Instant.parse("2026-10-14T07:45:00Z"))

    private fun schedule(vararg reminders: Reminder) =
        Schedule(1, reminders.toList(), emptyList(), SnoozeSettings(10, 2), AlertSettings(0))

    @Test
    fun `TC-W-060 the tile shows the next reminder with its title and time`() {
        val model = tileModel(schedule(meds, water), emptyMap(), link, now, zone)
        assertEquals("Drink water", model.reminder?.title)
        assertEquals(Instant.parse("2026-10-14T08:30:00Z"), model.reminder?.at)
    }

    @Test
    fun `TC-W-060 a snoozed reminder shows its snooze time instead of its schedule`() {
        val snoozed =
            mapOf(
                "meds" to SnoozeEntry(Instant.parse("2026-10-14T08:10:00Z"), 1, Instant.parse("2026-10-14T07:55:00Z"))
            )
        val model = tileModel(schedule(meds, water), snoozed, link, now, zone)
        assertEquals("Take medication", model.reminder?.title)
        assertEquals(Instant.parse("2026-10-14T08:10:00Z"), model.reminder?.at)
    }

    @Test
    fun `TC-W-061 the primary action starts the two minutes for the shown reminder and occurrence`() {
        val model = tileModel(schedule(water), emptyMap(), link, now, zone)
        assertEquals(TileAction("water", Instant.parse("2026-10-14T08:30:00Z")), model.action)
    }

    @Test
    fun `TC-W-061 the action survives the round trip through intent extras`() {
        val action = TileAction("water", Instant.parse("2026-10-14T08:30:00Z"))
        assertEquals(action, TileAction.fromExtras(action.toExtras()))
        assertNull(TileAction.fromExtras(mapOf("reminder_id" to "water")))
        assertNull(TileAction.fromExtras(emptyMap()))
    }

    @Test
    fun `TC-W-062 the tile carries the link state and the last sync time`() {
        val model = tileModel(schedule(water), emptyMap(), link, now, zone)
        assertEquals(LinkState.RELAYED, model.link.state)
        assertEquals(Instant.parse("2026-10-14T07:45:00Z"), model.link.lastSyncAt)
    }

    @Test
    fun `TC-W-063 with no reminders the tile has an empty state and no action`() {
        val model = tileModel(schedule(), emptyMap(), link, now, zone)
        assertNull(model.reminder)
        assertNull(model.action)
        assertNull(tileModel(null, emptyMap(), link, now, zone).reminder)
    }
}

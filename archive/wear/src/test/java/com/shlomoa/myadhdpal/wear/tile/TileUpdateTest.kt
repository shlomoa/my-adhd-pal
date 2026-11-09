package com.shlomoa.myadhdpal.wear.tile

import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.protocol.fake.FakeLink
import com.shlomoa.myadhdpal.wear.link.FanOutListener
import com.shlomoa.myadhdpal.wear.link.LinkClient
import com.shlomoa.myadhdpal.wear.reminder.FakeTimerPort
import com.shlomoa.myadhdpal.wear.reminder.ReminderAction
import com.shlomoa.myadhdpal.wear.reminder.ReminderActionHandler
import com.shlomoa.myadhdpal.wear.schedule.InMemoryScheduleStore
import com.shlomoa.myadhdpal.wear.schedule.ScheduleReceiver
import com.shlomoa.myadhdpal.wear.scheduler.FakeAlarmPort
import com.shlomoa.myadhdpal.wear.scheduler.InMemorySnoozeStore
import com.shlomoa.myadhdpal.wear.scheduler.ReminderScheduler
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TileUpdateTest {
    private val link = FakeLink()
    private val now = Instant.parse("2026-10-14T08:00:00Z")
    private val water = Reminder("water", "Drink water", 8 * 60 + 30, listOf(1, 2, 3, 4, 5, 6, 7))
    private var schedule: Schedule? = null
    private var refreshes = 0
    private val triggers = TileUpdateTriggers { refreshes++ }
    private val scheduler =
        ReminderScheduler({ schedule }, FakeAlarmPort(), InMemorySnoozeStore(), { now }, ZoneId.of("UTC"))
    private val client = LinkClient(link.watch, "0.1.0", { "77777777-7777-4777-8777-777777777777" }, { now }, null)
        .also { it.setOnChange(triggers::onLinkChanged) }
    private val receiver = ScheduleReceiver(link.watch, InMemoryScheduleStore(), { now }) { applied, at ->
        schedule = applied
        client.onScheduleApplied(at)
        triggers.onScheduleApplied()
    }
    private val handler = ReminderActionHandler(
        scheduler,
        { link.watch },
        FakeTimerPort(),
        { now },
        { "88888888-8888-4888-8888-888888888888" },
        onHandled = triggers::onActionHandled
    )

    private fun newSchedule(version: Long) =
        Schedule(version, listOf(water), emptyList(), SnoozeSettings(10, 2), AlertSettings(0))

    @Test
    fun `TC-W-064 a schedule change, a handled action and a link change each request a tile update`() {
        link.watch.setListener(FanOutListener(client, receiver))
        client.start()
        val afterStart = refreshes

        link.connect(nearby = true)
        assertEquals(afterStart + 1, refreshes)

        link.phone.put(
            Paths.SCHEDULE,
            Codec.encode("99999999-9999-4999-8999-999999999999", now.toEpochMilli(), newSchedule(1))
        )
        assertEquals(afterStart + 1 + 2, refreshes)

        handler.handle(ReminderAction.DONE, "water", 0, Instant.parse("2026-10-14T08:30:00Z"))
        assertEquals(afterStart + 1 + 2 + 1, refreshes)
    }

    @Test
    fun `TC-W-064 an ignored or refused action requests no update`() {
        schedule = newSchedule(1)
        link.connect(nearby = true)
        val at = Instant.parse("2026-10-14T08:30:00Z")
        handler.handle(ReminderAction.DONE, "water", 0, at)
        val after = refreshes
        handler.handle(ReminderAction.DONE, "water", 0, at)
        handler.handle(ReminderAction.SNOOZE, "water", 2, at)
        assertEquals(after, refreshes)
    }

    @Test
    fun `TC-W-065 the tile refreshes when the next reminder is due`() {
        val due = now.plusSeconds(30 * 60)
        assertEquals(Duration.ofMinutes(30), tileFreshness(due, now))
    }

    @Test
    fun `TC-W-065 the refresh time is never shorter than a minute or longer than a day`() {
        assertEquals(Duration.ofMinutes(1), tileFreshness(now.plusSeconds(5), now))
        assertEquals(Duration.ofMinutes(1), tileFreshness(now.minusSeconds(60), now))
        assertEquals(Duration.ofHours(24), tileFreshness(now.plus(Duration.ofDays(3)), now))
        assertEquals(Duration.ofHours(24), tileFreshness(null, now))
    }
}

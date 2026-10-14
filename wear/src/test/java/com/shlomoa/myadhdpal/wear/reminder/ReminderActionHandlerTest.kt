package com.shlomoa.myadhdpal.wear.reminder

import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Acknowledgement
import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Codec
import com.shlomoa.myadhdpal.protocol.DecodeResult
import com.shlomoa.myadhdpal.protocol.Envelope
import com.shlomoa.myadhdpal.protocol.ItemPath
import com.shlomoa.myadhdpal.protocol.LinkTransport
import com.shlomoa.myadhdpal.protocol.Paths
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.SnoozeDenial
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import com.shlomoa.myadhdpal.protocol.fake.FakeLink
import com.shlomoa.myadhdpal.wear.scheduler.FakeAlarmPort
import com.shlomoa.myadhdpal.wear.scheduler.InMemorySnoozeStore
import com.shlomoa.myadhdpal.wear.scheduler.ReminderScheduler
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderActionHandlerTest {
    private val link = FakeLink()
    private var now = Instant.parse("2026-10-14T08:30:05Z")
    private val occurrence = Instant.parse("2026-10-14T08:30:00Z")
    private val water = Reminder("water", "Drink water", 8 * 60 + 30, listOf(1, 2, 3, 4, 5, 6, 7))
    private val schedule = Schedule(1, listOf(water), emptyList(), SnoozeSettings(10, 2), AlertSettings(emptyList(), 0))
    private val alarms = FakeAlarmPort()
    private val snoozes = InMemorySnoozeStore()
    private val scheduler = ReminderScheduler({ schedule }, alarms, snoozes, { now }, ZoneId.of("UTC"))
    private val timer = FakeTimerPort()
    private val ids = ArrayDeque((1..6).map { "00000000-0000-4000-8000-00000000000$it" })

    private fun handler(transport: LinkTransport? = link.watch) =
        ReminderActionHandler(scheduler, { transport }, timer, { now }, { ids.removeFirst() })

    private fun acksOnPhone(): List<Acknowledgement> =
        link.phone.items().filter { Paths.parse(it.path) is ItemPath.Ack }.map {
            (Codec.decode(it.path, it.envelope) as DecodeResult.Ok).item.message as Acknowledgement
        }

    @Test
    fun `TC-W-035 Done and Dismiss send an ack with the action, the time, the reminder and the occurrence`() {
        link.connect(nearby = true)
        val doneAt = now
        handler().handle(ReminderAction.DONE, "water", snoozeCount = 0, occurrenceAt = occurrence)
        now = now.plusSeconds(60)
        handler().handle(ReminderAction.DISMISS, "water", snoozeCount = 0, occurrenceAt = occurrence.plusSeconds(1))
        val first = Acknowledgement(AckAction.DONE, doneAt.toEpochMilli(), "water", occurrence.toEpochMilli())
        val second =
            Acknowledgement(AckAction.DISMISS, now.toEpochMilli(), "water", occurrence.plusSeconds(1).toEpochMilli())
        assertEquals(listOf(first, second), acksOnPhone())
        assertEquals(2, link.phone.items().count { Paths.parse(it.path) is ItemPath.Ack })
    }

    @Test
    fun `TC-W-035 each ack is a fresh item and Done clears a pending snooze`() {
        link.connect(nearby = true)
        scheduler.snooze("water", 0, occurrence)
        handler().handle(ReminderAction.DONE, "water", 1, occurrence)
        assertTrue(snoozes.load().isEmpty())
        assertEquals(listOf(Paths.ack("00000000-0000-4000-8000-000000000001")), link.phone.items().map { it.path })
    }

    @Test
    fun `TC-W-036 snooze moves the alarm before the ack is sent and the ack arrives on reconnect`() {
        var alarmWhenSent: FakeAlarmPort.Alarm? = null
        val spy = object : LinkTransport by link.watch {
            override fun put(path: String, envelope: Envelope) {
                alarmWhenSent = alarms.alarm
                link.watch.put(path, envelope)
            }
        }
        val outcome = handler(spy).handle(ReminderAction.SNOOZE, "water", snoozeCount = 0, occurrenceAt = occurrence)

        val due = Instant.parse("2026-10-14T08:40:05Z")
        assertEquals(ActionOutcome.Done, outcome)
        assertEquals(FakeAlarmPort.Alarm(due, "water", exact = true), alarmWhenSent)
        assertTrue(acksOnPhone().isEmpty())

        link.connect(nearby = false)
        assertEquals(listOf(AckAction.SNOOZE), acksOnPhone().map { it.action })
        assertEquals(occurrence.toEpochMilli(), acksOnPhone().single().occurrenceAt)
    }

    @Test
    fun `TC-W-036 a snooze beyond the cap is denied, nothing is sent and nothing is moved`() {
        link.connect(nearby = true)
        val outcome = handler().handle(ReminderAction.SNOOZE, "water", snoozeCount = 2, occurrenceAt = occurrence)
        assertEquals(ActionOutcome.SnoozeDenied(SnoozeDenial.AT_CAP), outcome)
        assertTrue(acksOnPhone().isEmpty())
        assertNull(alarms.alarm)
    }

    @Test
    fun `TC-W-037 Start 2 min starts a two minute timer and reports the action`() {
        link.connect(nearby = true)
        handler().handle(ReminderAction.START_2_MIN, "water", snoozeCount = 0, occurrenceAt = occurrence)
        assertEquals(listOf(FakeTimerPort.Started("water", 120)), timer.started)
        assertEquals(listOf(AckAction.START_2_MIN), acksOnPhone().map { it.action })
    }

    @Test
    fun `TC-W-038 tapping the same action on the same notification twice sends one ack`() {
        link.connect(nearby = true)
        val handler = handler()
        handler.handle(ReminderAction.DONE, "water", 0, occurrence)
        val second = handler.handle(ReminderAction.DONE, "water", 0, occurrence)
        assertEquals(ActionOutcome.Duplicate, second)
        assertEquals(1, acksOnPhone().size)
    }

    @Test
    fun `TC-W-036 without a transport the local effect still happens and nothing crashes`() {
        val outcome = handler(transport = null).handle(ReminderAction.SNOOZE, "water", 0, occurrence)
        assertEquals(ActionOutcome.Done, outcome)
        assertEquals(Instant.parse("2026-10-14T08:40:05Z"), alarms.alarm?.at)
    }
}

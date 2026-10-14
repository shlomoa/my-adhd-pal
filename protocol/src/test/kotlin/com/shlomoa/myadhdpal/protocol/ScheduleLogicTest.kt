package com.shlomoa.myadhdpal.protocol

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleLogicTest {
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val utc = ZoneId.of("UTC")
    private val weekdays = listOf(1, 2, 3, 4, 5)
    private val everyDay = listOf(1, 2, 3, 4, 5, 6, 7)

    private fun at(zone: ZoneId, text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun local(instant: Instant?, zone: ZoneId): String? = instant?.let {
        ZonedDateTime.ofInstant(it, zone).toLocalDateTime().toString()
    }

    private fun schedule(
        reminders: List<Reminder> = emptyList(),
        checkpoints: List<Checkpoint> = emptyList(),
        snooze: SnoozeSettings = SnoozeSettings(minutes = 10, maxCount = 3)
    ) = Schedule(1, reminders, checkpoints, snooze, AlertSettings(emptyList(), 600))

    private fun reminder(id: String, minute: Int, days: List<Int> = everyDay) = Reminder(id, "Title $id", minute, days)

    // 2026-10-12 is a Monday.
    @Test
    fun `TC-S-067 next reminder is the first one strictly after now`() {
        val s = schedule(listOf(reminder("a", 8 * 60, weekdays), reminder("b", 20 * 60, weekdays)))
        assertEquals(
            "2026-10-12T08:00",
            local(ScheduleLogic.nextReminder(s, at(utc, "2026-10-12T07:00:00"), utc)?.at, utc)
        )
        assertEquals(
            "2026-10-12T20:00",
            local(ScheduleLogic.nextReminder(s, at(utc, "2026-10-12T08:00:00"), utc)?.at, utc)
        )
        assertEquals("b", ScheduleLogic.nextReminder(s, at(utc, "2026-10-12T08:00:00"), utc)?.reminder?.id)
        assertEquals(
            "2026-10-13T08:00",
            local(ScheduleLogic.nextReminder(s, at(utc, "2026-10-12T20:00:00"), utc)?.at, utc)
        )
    }

    @Test
    fun `TC-S-067 next reminder skips days that are not selected`() {
        val s = schedule(listOf(reminder("a", 8 * 60, weekdays)))
        // Friday evening, Saturday and Sunday all give Monday 08:00
        for (now in listOf("2026-10-16T09:00:00", "2026-10-17T12:00:00", "2026-10-18T23:59:00")) {
            assertEquals(now, "2026-10-19T08:00", local(ScheduleLogic.nextReminder(s, at(utc, now), utc)?.at, utc))
        }
        val sundayOnly = schedule(listOf(reminder("s", 9 * 60, listOf(7))))
        assertEquals(
            "2026-10-18T09:00",
            local(ScheduleLogic.nextReminder(sundayOnly, at(utc, "2026-10-12T10:00:00"), utc)?.at, utc)
        )
        // exactly a week ahead
        assertEquals(
            "2026-10-25T09:00",
            local(ScheduleLogic.nextReminder(sundayOnly, at(utc, "2026-10-18T09:00:00"), utc)?.at, utc)
        )
    }

    @Test
    fun `TC-S-067 the earliest of several reminders wins and an empty schedule has none`() {
        val s = schedule(listOf(reminder("late", 22 * 60), reminder("early", 6 * 60), reminder("mid", 12 * 60)))
        assertEquals("early", ScheduleLogic.nextReminder(s, at(utc, "2026-10-12T05:00:00"), utc)?.reminder?.id)
        assertEquals("mid", ScheduleLogic.nextReminder(s, at(utc, "2026-10-12T06:00:00"), utc)?.reminder?.id)
        assertEquals("late", ScheduleLogic.nextReminder(s, at(utc, "2026-10-12T12:00:00"), utc)?.reminder?.id)
        assertEquals("early", ScheduleLogic.nextReminder(s, at(utc, "2026-10-12T22:00:00"), utc)?.reminder?.id)
        assertNull(ScheduleLogic.nextReminder(schedule(), at(utc, "2026-10-12T05:00:00"), utc))
    }

    @Test
    fun `TC-S-068 next checkpoint inside the day and across midnight`() {
        val s =
            schedule(
                checkpoints = listOf(
                    Checkpoint("c1", "Midday", 12 * 60),
                    Checkpoint("c2", "Late", 23 * 60 + 30),
                    Checkpoint("c3", "Early", 30)
                )
            )
        assertEquals(
            "2026-10-12T00:30",
            local(ScheduleLogic.nextCheckpoint(s, at(utc, "2026-10-12T00:00:00"), utc)?.at, utc)
        )
        assertEquals(
            "2026-10-12T12:00",
            local(ScheduleLogic.nextCheckpoint(s, at(utc, "2026-10-12T00:30:00"), utc)?.at, utc)
        )
        assertEquals(
            "2026-10-12T23:30",
            local(ScheduleLogic.nextCheckpoint(s, at(utc, "2026-10-12T12:00:00"), utc)?.at, utc)
        )
        // after the last one of the day, the next is the first one after midnight
        val across = ScheduleLogic.nextCheckpoint(s, at(utc, "2026-10-12T23:45:00"), utc)
        assertEquals("2026-10-13T00:30", local(across?.at, utc))
        assertEquals("c3", across?.checkpoint?.id)
        assertNull(ScheduleLogic.nextCheckpoint(schedule(), at(utc, "2026-10-12T05:00:00"), utc))
    }

    @Test
    fun `TC-S-069 a snooze moves the reminder by the configured minutes`() {
        val now = Instant.parse("2026-10-12T08:00:00Z")
        val result = ScheduleLogic.snooze(SnoozeSettings(10, 3), snoozeCount = 0, now = now, expiresAt = null)
        assertEquals(SnoozeResult.Until(now.plusSeconds(600), snoozeCount = 1), result)
    }

    @Test
    fun `TC-S-069 repeated snoozes count up and stop at the cap`() {
        val settings = SnoozeSettings(5, 2)
        var now = Instant.parse("2026-10-12T08:00:00Z")
        var count = 0
        repeat(2) {
            val result = ScheduleLogic.snooze(settings, count, now, null)
            assertEquals(SnoozeResult.Until(now.plusSeconds(300), count + 1), result)
            now = now.plusSeconds(300)
            count += 1
        }
        assertEquals(SnoozeResult.Denied(SnoozeDenial.AT_CAP), ScheduleLogic.snooze(settings, count, now, null))
        assertEquals(SnoozeResult.Denied(SnoozeDenial.AT_CAP), ScheduleLogic.snooze(SnoozeSettings(5, 0), 0, now, null))
    }

    @Test
    fun `TC-S-069 a snooze that would end at or after the expiry is denied`() {
        val now = Instant.parse("2026-10-12T08:00:00Z")
        val settings = SnoozeSettings(10, 3)
        assertEquals(
            SnoozeResult.Until(now.plusSeconds(600), 1),
            ScheduleLogic.snooze(settings, 0, now, now.plusSeconds(601))
        )
        assertEquals(
            SnoozeResult.Denied(SnoozeDenial.PAST_EXPIRY),
            ScheduleLogic.snooze(settings, 0, now, now.plusSeconds(600))
        )
        assertEquals(
            SnoozeResult.Denied(SnoozeDenial.PAST_EXPIRY),
            ScheduleLogic.snooze(settings, 0, now, now.minusSeconds(1))
        )
        // at the cap AND past expiry: the cap is reported first
        assertEquals(
            SnoozeResult.Denied(SnoozeDenial.AT_CAP),
            ScheduleLogic.snooze(settings, 3, now, now.minusSeconds(1))
        )
    }

    @Test
    fun `TC-S-070 a reminder in the skipped hour of a spring-forward day fires at the first valid time after it`() {
        // New York 2026-03-08: 02:00 EST jumps to 03:00 EDT, so 02:30 does not exist and becomes 03:30 EDT.
        val s = schedule(listOf(reminder("a", 2 * 60 + 30)))
        val before = ScheduleLogic.nextReminder(s, at(newYork, "2026-03-08T01:00:00"), newYork)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), before?.at)
        assertEquals("2026-03-08T03:30", local(before?.at, newYork))
        // the day after it is 02:30 EDT again
        val after = ScheduleLogic.nextReminder(s, before!!.at, newYork)
        assertEquals("2026-03-09T02:30", local(after?.at, newYork))
        // one day before, a normal day
        assertEquals(
            "2026-03-07T02:30",
            local(ScheduleLogic.nextReminder(s, at(newYork, "2026-03-07T00:00:00"), newYork)?.at, newYork)
        )
    }

    @Test
    fun `TC-S-070 a reminder in the repeated hour of a fall-back day fires once, at the first occurrence`() {
        // New York 2026-11-01: 02:00 EDT falls back to 01:00 EST, so 01:30 happens twice.
        val s = schedule(listOf(reminder("a", 60 + 30)))
        val first = ScheduleLogic.nextReminder(s, at(newYork, "2026-11-01T00:00:00"), newYork)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), first?.at)
        // after the first 01:30 (EDT) has passed, the repeated 01:30 (EST) is skipped; the next one is the next day
        val next = ScheduleLogic.nextReminder(s, first!!.at, newYork)
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), next?.at)
        assertEquals("2026-11-02T01:30", local(next?.at, newYork))
    }

    @Test
    fun `TC-S-070 a time-zone change recomputes the next reminder on the wall clock`() {
        val s = schedule(listOf(reminder("a", 8 * 60)))
        val now = Instant.parse("2026-06-01T12:00:00Z")
        // 12:00 UTC is 08:00 in New York (EDT), so the next 08:00 is tomorrow;
        // in Tokyo it is 21:00, so the next 08:00 is tomorrow morning
        assertEquals(Instant.parse("2026-06-02T12:00:00Z"), ScheduleLogic.nextReminder(s, now, newYork)?.at)
        assertEquals(Instant.parse("2026-06-01T23:00:00Z"), ScheduleLogic.nextReminder(s, now, tokyo)?.at)
    }

    @Test
    fun `TC-S-071 a schedule applies only if its version is greater`() {
        assertEquals(true, ScheduleLogic.shouldApply(currentVersion = null, incomingVersion = 0))
        assertEquals(true, ScheduleLogic.shouldApply(currentVersion = 4, incomingVersion = 5))
        assertEquals(false, ScheduleLogic.shouldApply(currentVersion = 5, incomingVersion = 5))
        assertEquals(false, ScheduleLogic.shouldApply(currentVersion = 5, incomingVersion = 4))
        assertEquals(true, ScheduleLogic.shouldApply(currentVersion = 5, incomingVersion = Long.MAX_VALUE))
    }
}

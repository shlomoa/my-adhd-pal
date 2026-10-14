package com.shlomoa.myadhdpal.wear.scheduler

import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.SnoozeDenial
import com.shlomoa.myadhdpal.protocol.SnoozeResult
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import java.io.File
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReminderSchedulerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("UTC")
    private var now = Instant.parse("2026-10-14T08:00:00Z")
    private val everyDay = listOf(1, 2, 3, 4, 5, 6, 7)
    private val medsOccurrence = Instant.parse("2026-10-14T07:59:00Z")
    private val water = Reminder("water", "Drink water", 8 * 60 + 30, everyDay)
    private val meds = Reminder("meds", "Take medication", 9 * 60, everyDay)
    private var schedule: Schedule? = schedule(1, meds, water)
    private val alarms = FakeAlarmPort()
    private val snoozes = InMemorySnoozeStore()

    private fun schedule(version: Long, vararg reminders: Reminder, snooze: SnoozeSettings = SnoozeSettings(10, 2)) =
        Schedule(version, reminders.toList(), emptyList(), snooze, AlertSettings(emptyList(), 0))

    private fun scheduler(store: SnoozeStore = snoozes) = ReminderScheduler({ schedule }, alarms, store, { now }, zone)

    @Test
    fun `TC-W-020 the alarm is set for the earliest future reminder`() {
        val status = scheduler().rearm()
        assertEquals(FakeAlarmPort.Alarm(Instant.parse("2026-10-14T08:30:00Z"), "water"), alarms.alarm)
        assertEquals(SchedulerStatus.Armed(Instant.parse("2026-10-14T08:30:00Z"), "water"), status)
    }

    @Test
    fun `TC-W-020 with no reminders there is no alarm and the status is idle`() {
        schedule = schedule(1)
        assertEquals(SchedulerStatus.Idle, scheduler().rearm())
        assertNull(alarms.alarm)
    }

    @Test
    fun `TC-W-021 a new schedule replaces the stale alarm and leaves exactly one`() {
        val scheduler = scheduler()
        scheduler.rearm()
        schedule = schedule(2, meds)
        scheduler.rearm()
        assertEquals(FakeAlarmPort.Alarm(Instant.parse("2026-10-14T09:00:00Z"), "meds"), alarms.alarm)
        schedule = schedule(3)
        scheduler.rearm()
        assertNull(alarms.alarm)
    }

    @Test
    fun `TC-W-022 after a boot the alarm is rebuilt from the stored schedule and snoozes`() {
        scheduler().snooze("meds", snoozeCount = 0, occurrenceAt = medsOccurrence)
        val afterBoot = FakeAlarmPort()
        ReminderScheduler({ schedule }, afterBoot, snoozes, { now }, zone).rearm()
        assertEquals(FakeAlarmPort.Alarm(Instant.parse("2026-10-14T08:10:00Z"), "meds"), afterBoot.alarm)
    }

    @Test
    fun `TC-W-023 without exact alarms an inexact alarm is still set and the status says so`() {
        alarms.canSchedule = false
        val at = Instant.parse("2026-10-14T08:30:00Z")
        assertEquals(SchedulerStatus.Armed(at, "water", exact = false), scheduler().rearm())
        assertEquals(FakeAlarmPort.Alarm(at, "water", exact = false), alarms.alarm)
        alarms.canSchedule = true
        assertEquals(SchedulerStatus.Armed(at, "water", exact = true), scheduler().rearm())
        assertEquals(FakeAlarmPort.Alarm(at, "water", exact = true), alarms.alarm)
    }

    @Test
    fun `TC-W-024 a snooze moves the reminder to now plus the snooze minutes and persists`() {
        val scheduler = scheduler()
        val result = scheduler.snooze("meds", snoozeCount = 0, occurrenceAt = medsOccurrence)
        assertEquals(SnoozeResult.Until(Instant.parse("2026-10-14T08:10:00Z"), 1), result)
        assertEquals(FakeAlarmPort.Alarm(Instant.parse("2026-10-14T08:10:00Z"), "meds"), alarms.alarm)

        val file = File(folder.root, "snoozes.txt")
        FileSnoozeStore(file).save(snoozes.load())
        assertEquals(snoozes.load(), FileSnoozeStore(file).load())
    }

    @Test
    fun `TC-W-024 a snooze beyond the cap is denied and sets nothing new`() {
        val scheduler = scheduler()
        val result = scheduler.snooze("meds", snoozeCount = 2, occurrenceAt = medsOccurrence)
        assertEquals(SnoozeResult.Denied(SnoozeDenial.AT_CAP), result)
        assertEquals(0, alarms.setCount)
    }

    @Test
    fun `TC-W-024 when the alarm fires the reminder is returned with its snooze count and the next one is armed`() {
        val scheduler = scheduler()
        scheduler.snooze("meds", snoozeCount = 0, occurrenceAt = medsOccurrence)
        now = Instant.parse("2026-10-14T08:10:00Z")
        val fired = scheduler.onAlarm("meds", Instant.parse("2026-10-14T08:10:00Z"))
        assertEquals(FiredReminder(meds, snoozeCount = 1, occurrenceAt = medsOccurrence), fired)
        assertEquals(FakeAlarmPort.Alarm(Instant.parse("2026-10-14T08:30:00Z"), "water"), alarms.alarm)
        assertEquals(emptyMap<String, SnoozeEntry>(), snoozes.load())
    }

    @Test
    fun `TC-W-020 a scheduled alarm returns the reminder with its own fire time as the occurrence`() {
        val scheduler = scheduler()
        scheduler.rearm()
        val at = Instant.parse("2026-10-14T08:30:00Z")
        assertEquals(FiredReminder(water, snoozeCount = 0, occurrenceAt = at), scheduler.onAlarm("water", at))
    }

    @Test
    fun `TC-W-020 an alarm for a reminder that is no longer in the schedule fires nothing`() {
        val scheduler = scheduler()
        scheduler.rearm()
        schedule = schedule(2, meds)
        assertNull(scheduler.onAlarm("water", Instant.parse("2026-10-14T08:30:00Z")))
    }

    @Test
    fun `TC-W-020 acknowledging a reminder clears its pending snooze`() {
        val scheduler = scheduler()
        scheduler.snooze("meds", snoozeCount = 0, occurrenceAt = medsOccurrence)
        scheduler.clearSnooze("meds")
        assertEquals(emptyMap<String, SnoozeEntry>(), snoozes.load())
        assertEquals(Instant.parse("2026-10-14T08:30:00Z"), alarms.alarm?.at)
    }
}

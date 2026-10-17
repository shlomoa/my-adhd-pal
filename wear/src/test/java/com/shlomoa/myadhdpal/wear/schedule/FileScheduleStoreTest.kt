package com.shlomoa.myadhdpal.wear.schedule

import com.shlomoa.myadhdpal.protocol.AlertSettings
import com.shlomoa.myadhdpal.protocol.Reminder
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.SnoozeSettings
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileScheduleStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val schedule = Schedule(
        version = 7,
        reminders = listOf(Reminder("r1", "Take medication", 8 * 60, listOf(1, 2, 3, 4, 5))),
        checkpoints = emptyList(),
        snooze = SnoozeSettings(10, 3),
        alerts = AlertSettings(600)
    )
    private val appliedAt = Instant.parse("2026-10-14T08:00:00Z")

    private fun file(): File = File(folder.root, "schedule.json")

    @Test
    fun `TC-W-013 the schedule and its apply time are read back by a new store instance`() {
        FileScheduleStore(file()).save(StoredSchedule(schedule, appliedAt))
        val loaded = FileScheduleStore(file()).load()
        assertEquals(StoredSchedule(schedule, appliedAt), loaded)
    }

    @Test
    fun `TC-W-013 nothing stored reads as null`() {
        assertNull(FileScheduleStore(file()).load())
    }

    @Test
    fun `TC-W-013 a corrupt file reads as null instead of crashing`() {
        file().writeText("garbage")
        assertNull(FileScheduleStore(file()).load())
    }

    @Test
    fun `TC-W-013 saving again replaces the old schedule`() {
        val store = FileScheduleStore(file())
        store.save(StoredSchedule(schedule, appliedAt))
        store.save(StoredSchedule(schedule.copy(version = 8), appliedAt.plusSeconds(60)))
        assertEquals(8L, FileScheduleStore(file()).load()?.schedule?.version)
    }
}

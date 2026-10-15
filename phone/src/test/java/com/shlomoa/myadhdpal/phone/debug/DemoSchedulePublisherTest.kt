package com.shlomoa.myadhdpal.phone.debug

import com.shlomoa.myadhdpal.phone.data.PublishOutcome
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.fake.InMemorySettingsRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The debug-only demo publisher: one reminder a few minutes ahead, through the real `ScheduleRepository`. */
class DemoSchedulePublisherTest {
    private val tasks: TaskRepository = InMemoryTaskRepository()
    private val source = FakeWatchLinkDataSource()
    private val schedule = InMemoryScheduleRepository(
        tasks,
        InMemorySettingsRepository(),
        source,
        InMemoryScheduleVersionStore()
    )

    private fun publisher(at: String) =
        DemoSchedulePublisher(tasks, schedule, Clock.fixed(Instant.parse(at), ZoneOffset.UTC))

    // 2026-10-14 is a Wednesday (day 3).
    private val noon = "2026-10-14T12:00:00Z"

    @Test
    fun `TC-A-073 a reminder is set two minutes ahead by default`() = runTest {
        publisher(noon).publish()
        val reminder = source.publishedSchedules.single().reminders.single()
        assertEquals("demo", reminder.id)
        assertEquals(12 * 60 + 2, reminder.minuteOfDay)
        assertEquals(listOf(3), reminder.days)
    }

    @Test
    fun `TC-A-074 crossing midnight uses the next weekday and Sunday wraps to Monday`() = runTest {
        publisher("2026-10-14T23:59:00Z").publish()
        assertEquals(1, source.publishedSchedules.single().reminders.single().minuteOfDay)
        assertEquals(listOf(4), source.publishedSchedules.single().reminders.single().days)
        // 2026-10-18 is a Sunday (day 7).
        publisher("2026-10-18T23:59:00Z").publish()
        assertEquals(listOf(1), source.publishedSchedules.last().reminders.single().days)
    }

    @Test
    fun `TC-A-075 title and minutes come from the arguments`() = runTest {
        publisher(noon).publish(minutesAhead = 10, title = "Drink water")
        val reminder = source.publishedSchedules.single().reminders.single()
        assertEquals("Drink water", reminder.title)
        assertEquals(12 * 60 + 10, reminder.minuteOfDay)
    }

    @Test
    fun `TC-A-076 repeating replaces the demo reminder and an identical publish sends nothing`() = runTest {
        val demo = publisher(noon)
        demo.publish(minutesAhead = 2)
        demo.publish(minutesAhead = 5)
        assertEquals(1, tasks.reminders.first().size)
        assertEquals(1, tasks.tasks.first().size)
        assertEquals(2, source.publishedSchedules.size)
        assertEquals(PublishOutcome.Unchanged, demo.publish(minutesAhead = 5))
        assertEquals(2, source.publishedSchedules.size)
    }

    @Test
    fun `TC-A-077 a failed publish is returned with its reason`() = runTest {
        source.failNextPublish("link down")
        assertEquals(PublishOutcome.Failed("link down"), publisher(noon).publish())
    }

    @Test
    fun `TC-A-078 an offset outside 1 to 1440 minutes is rejected`() = runTest {
        for (minutes in listOf(0, -1, 1441)) {
            try {
                publisher(noon).publish(minutesAhead = minutes)
                fail("$minutes minutes should be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("1"))
            }
        }
        assertTrue(source.publishedSchedules.isEmpty())
        assertTrue(tasks.tasks.first().isEmpty())
    }
}

/** Catalogue cases with no JVM test: CI (TC-A-079) and manual on the devices (TC-A-080). */
internal object DemoManualCases {
    val ids = listOf("TC-A-079", "TC-A-080")
}

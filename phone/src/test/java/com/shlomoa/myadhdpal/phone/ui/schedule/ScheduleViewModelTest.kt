package com.shlomoa.myadhdpal.phone.ui.schedule

import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.fake.InMemorySettingsRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import com.shlomoa.myadhdpal.phone.ui.FIXED_CLOCK
import com.shlomoa.myadhdpal.phone.ui.ListState
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Step 4.2: the schedule builder lists reminders and publishes a versioned `/schedule`. Now is Wed 12:00 UTC. */
class ScheduleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val tasks = InMemoryTaskRepository()
    private val settings = InMemorySettingsRepository()
    private val source = FakeWatchLinkDataSource()
    private val versions = InMemoryScheduleVersionStore()
    private val vm =
        ScheduleViewModel(tasks, InMemoryScheduleRepository(tasks, settings, source, versions), FIXED_CLOCK)

    private suspend fun seed() {
        tasks.save(Task("t-1", "Pills"))
        tasks.save(TaskReminder("early", "t-1", 8 * 60, listOf(1, 2, 3, 4, 5, 6, 7)))
        tasks.save(TaskReminder("noon-plus", "t-1", 13 * 60, listOf(1, 2, 3, 4, 5, 6, 7)))
        tasks.save(TaskReminder("evening", "t-1", 18 * 60, listOf(3)))
    }

    @Test
    fun `TC-A-024 reminders are listed in order of their next occurrence`() = runTest {
        seed()
        val content = vm.uiState.value.reminders as ListState.Content
        assertEquals(listOf("noon-plus", "evening", "early"), content.items.map { it.reminder.id })
        assertEquals(Instant.parse("2026-10-15T08:00:00Z"), content.items.last().nextAt)
        assertEquals("Pills", content.items.first().title)
    }

    @Test
    fun `TC-A-025 publish sends one schedule`() = runTest {
        seed()
        vm.publish()
        assertEquals(1, source.publishedSchedules.size)
        assertEquals(PublishUiState.Published(1L), vm.uiState.value.publish)
    }

    @Test
    fun `TC-A-026 the version grows with each changed publish`() = runTest {
        seed()
        vm.publish()
        tasks.save(TaskReminder("late", "t-1", 22 * 60, listOf(1)))
        vm.publish()
        assertEquals(listOf(1L, 2L), source.publishedSchedules.map { it.version })
    }

    @Test
    fun `TC-A-027 the published schedule carries thresholds and cooldown`() = runTest {
        seed()
        val changed: AppSettings = settings.settings.first().copy(cooldownSeconds = 900)
        settings.update(changed)
        vm.publish()
        val sent = source.publishedSchedules.single()
        assertEquals(900, sent.alerts.cooldownSeconds)
        assertEquals(changed.thresholds, sent.alerts.thresholds)
    }

    @Test
    fun `TC-A-028 a failed publish is reported and consumes no version`() = runTest {
        seed()
        source.failNextPublish("link down")
        vm.publish()
        assertEquals(PublishUiState.Failed("link down"), vm.uiState.value.publish)
        vm.publish()
        assertEquals(PublishUiState.Published(1L), vm.uiState.value.publish)
    }

    @Test
    fun `TC-A-029 publishing an unchanged schedule sends nothing and says so`() = runTest {
        seed()
        vm.publish()
        vm.publish()
        assertEquals(1, source.publishedSchedules.size)
        assertEquals(PublishUiState.Unchanged, vm.uiState.value.publish)
    }

    @Test
    fun `TC-A-055 no reminders is the Empty state`() {
        assertEquals(ListState.Empty, vm.uiState.value.reminders)
    }
}

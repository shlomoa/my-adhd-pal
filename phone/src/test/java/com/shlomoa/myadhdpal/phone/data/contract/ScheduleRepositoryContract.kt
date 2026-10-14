package com.shlomoa.myadhdpal.phone.data.contract

import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.PublishOutcome
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.ScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.fake.InMemorySettingsRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every [ScheduleRepository] must pass these, wired to the in-memory collaborators. */
abstract class ScheduleRepositoryContract {
    abstract fun create(
        tasks: TaskRepository,
        settings: SettingsRepository,
        source: WatchLinkDataSource,
        store: ScheduleVersionStore
    ): ScheduleRepository

    private class Rig(contract: ScheduleRepositoryContract) {
        val tasks = InMemoryTaskRepository()
        val settings = InMemorySettingsRepository()
        val source = FakeWatchLinkDataSource()
        val store = InMemoryScheduleVersionStore()
        val repo = contract.create(tasks, settings, source, store)
    }

    private suspend fun Rig.seed() {
        tasks.save(Task("t1", "Take medication"))
        tasks.save(TaskReminder("r1", "t1", 8 * 60, listOf(1, 2, 3, 4, 5)))
    }

    private suspend fun TaskRepository.save(reminder: TaskReminder) = this.save(reminder)

    @Test
    fun `TC-S-092 schedule repository publishes a versioned schedule with the settings`() = runTest {
        val rig = Rig(this@ScheduleRepositoryContract)
        rig.seed()
        assertEquals(PublishOutcome.Published(1), rig.repo.publish())
        val sent = rig.source.publishedSchedules.single()
        assertEquals(1L, sent.version)
        assertEquals(listOf("Take medication"), sent.reminders.map { it.title })
        val settings: AppSettings = rig.settings.settings.first()
        assertEquals(settings.thresholds, sent.alerts.thresholds)
        assertEquals(settings.cooldownSeconds, sent.alerts.cooldownSeconds)
        assertEquals(1L, rig.repo.lastPublishedVersion.first())
    }

    @Test
    fun `TC-S-092 schedule repository publishing an unchanged schedule is a no-op`() = runTest {
        val rig = Rig(this@ScheduleRepositoryContract)
        rig.seed()
        rig.repo.publish()
        assertEquals(PublishOutcome.Unchanged, rig.repo.publish())
        assertEquals(1, rig.source.publishedSchedules.size)
    }

    @Test
    fun `TC-S-092 schedule repository versions grow when the content changes`() = runTest {
        val rig = Rig(this@ScheduleRepositoryContract)
        rig.seed()
        rig.repo.publish()
        rig.tasks.save(TaskReminder("r2", "t1", 20 * 60, listOf(7)))
        assertEquals(PublishOutcome.Published(2), rig.repo.publish())
        rig.settings.update(rig.settings.settings.first().copy(cooldownSeconds = 1200))
        assertEquals(PublishOutcome.Published(3), rig.repo.publish())
        assertEquals(listOf(1L, 2L, 3L), rig.source.publishedSchedules.map { it.version })
    }

    @Test
    fun `TC-S-092 schedule repository a failed publish is reported and the version is not consumed`() = runTest {
        val rig = Rig(this@ScheduleRepositoryContract)
        rig.seed()
        rig.source.failNextPublish("no link")
        assertEquals(PublishOutcome.Failed("no link"), rig.repo.publish())
        assertEquals(null, rig.repo.lastPublishedVersion.first())
        assertEquals(PublishOutcome.Published(1), rig.repo.publish())
    }

    @Test
    fun `TC-S-092 schedule repository versions stay monotonic across a restart`() = runTest {
        val rig = Rig(this@ScheduleRepositoryContract)
        rig.seed()
        rig.repo.publish()
        rig.tasks.save(TaskReminder("r2", "t1", 20 * 60, listOf(7)))
        rig.repo.publish()
        val restarted = create(rig.tasks, rig.settings, rig.source, rig.store)
        assertEquals(2L, restarted.lastPublishedVersion.first())
        rig.tasks.save(TaskReminder("r3", "t1", 21 * 60, listOf(7)))
        assertEquals(PublishOutcome.Published(3), restarted.publish())
    }
}

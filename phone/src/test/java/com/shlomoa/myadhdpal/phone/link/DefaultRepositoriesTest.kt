package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.DefaultLinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.DefaultPromptRepository
import com.shlomoa.myadhdpal.phone.data.DefaultScheduleRepository
import com.shlomoa.myadhdpal.phone.data.LinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.PromptPublishResult
import com.shlomoa.myadhdpal.phone.data.PromptRepository
import com.shlomoa.myadhdpal.phone.data.PublishedPrompt
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.ScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.contract.LinkStatusRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.PromptRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.ScheduleRepositoryContract
import com.shlomoa.myadhdpal.phone.data.fake.FakeWatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryPublishedPrompts
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// TC-S-092: the production repositories of step 2.5 pass the same contract tests as the in-memory references.

class DefaultScheduleRepositoryTest : ScheduleRepositoryContract() {
    override fun create(
        tasks: TaskRepository,
        settings: SettingsRepository,
        source: WatchLinkDataSource,
        store: ScheduleVersionStore
    ): ScheduleRepository = DefaultScheduleRepository(tasks, settings, source, store)
}

class DefaultPromptRepositoryTest : PromptRepositoryContract() {
    private var counter = 0

    override fun create(source: WatchLinkDataSource, now: Instant): PromptRepository = DefaultPromptRepository(
        source,
        InMemoryPublishedPrompts(),
        clock = { now },
        newId = { "123e4567-e89b-42d3-a456-4266141740%02d".format(++counter) }
    )
}

class DefaultLinkStatusRepositoryTest : LinkStatusRepositoryContract() {
    override fun create(source: WatchLinkDataSource): LinkStatusRepository = DefaultLinkStatusRepository(source)
}

class DefaultPromptRepositoryCleanupTest {
    private val now = Instant.parse("2026-10-17T10:00:00Z")
    private val source = FakeWatchLinkDataSource()
    private val prompts = InMemoryPublishedPrompts()
    private val repo = DefaultPromptRepository(source, prompts, clock = { now }, newId = { "p-1" })

    @Test
    fun `TC-S-194 a published prompt is remembered with its expiry so it can be removed later`() = runTest {
        val expiry = now.plusSeconds(600)
        assertEquals(PromptPublishResult.Published("p-1"), repo.publish("Mood?", listOf("Good", "Bad"), expiry))
        assertEquals(listOf(PublishedPrompt("p-1", expiry)), prompts.all())
    }

    @Test
    fun `TC-S-194 a prompt that was not sent, or was rejected, is not remembered`() = runTest {
        source.failNextPublish("offline")
        assertTrue(repo.publish("Mood?", listOf("Good", "Bad"), null) is PromptPublishResult.Failed)
        assertTrue(repo.publish("", listOf("Good", "Bad"), null) is PromptPublishResult.Rejected)
        assertTrue(prompts.all().isEmpty())
    }
}

package com.shlomoa.myadhdpal.phone.data.fake

import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.LinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.PromptRepository
import com.shlomoa.myadhdpal.phone.data.PublishedPrompts
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.ScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.contract.HistoryRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.LinkStatusRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.PromptRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.PublishedPromptsContract
import com.shlomoa.myadhdpal.phone.data.contract.ScheduleRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.SettingsRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.TaskRepositoryContract
import java.time.Instant

// TC-S-092: the in-memory fakes satisfy the contract tests of the repository interfaces.

class InMemoryTaskRepositoryTest : TaskRepositoryContract() {
    override fun create(): TaskRepository = InMemoryTaskRepository()
}

class InMemoryHistoryRepositoryTest : HistoryRepositoryContract() {
    override fun create(): HistoryRepository = InMemoryHistoryRepository()
}

class InMemorySettingsRepositoryTest : SettingsRepositoryContract() {
    override fun create(): SettingsRepository = InMemorySettingsRepository()
}

class InMemoryScheduleRepositoryTest : ScheduleRepositoryContract() {
    override fun create(
        tasks: TaskRepository,
        settings: SettingsRepository,
        source: WatchLinkDataSource,
        store: ScheduleVersionStore
    ): ScheduleRepository = InMemoryScheduleRepository(tasks, settings, source, store)
}

class InMemoryPromptRepositoryTest : PromptRepositoryContract() {
    private var counter = 0

    override fun create(source: WatchLinkDataSource, now: Instant): PromptRepository =
        InMemoryPromptRepository(source, InMemoryPublishedPrompts(), clock = {
            now
        }, newId = { "123e4567-e89b-42d3-a456-4266141740%02d".format(++counter) })
}

class InMemoryLinkStatusRepositoryTest : LinkStatusRepositoryContract() {
    override fun create(source: WatchLinkDataSource): LinkStatusRepository = InMemoryLinkStatusRepository(source)
}

class InMemoryPublishedPromptsTest : PublishedPromptsContract() {
    override fun create(): PublishedPrompts = InMemoryPublishedPrompts()
}

package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.DefaultLinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.DefaultPromptRepository
import com.shlomoa.myadhdpal.phone.data.DefaultScheduleRepository
import com.shlomoa.myadhdpal.phone.data.LinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.PromptRepository
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.ScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.phone.data.contract.LinkStatusRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.PromptRepositoryContract
import com.shlomoa.myadhdpal.phone.data.contract.ScheduleRepositoryContract
import java.time.Instant

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
        clock = { now },
        newId = { "123e4567-e89b-42d3-a456-4266141740%02d".format(++counter) }
    )
}

class DefaultLinkStatusRepositoryTest : LinkStatusRepositoryContract() {
    override fun create(source: WatchLinkDataSource): LinkStatusRepository = DefaultLinkStatusRepository(source)
}

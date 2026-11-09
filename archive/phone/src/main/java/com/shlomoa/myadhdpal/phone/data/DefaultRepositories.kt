package com.shlomoa.myadhdpal.phone.data

import com.shlomoa.myadhdpal.protocol.Limits
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.PromptOption
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class DefaultScheduleRepository(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val source: WatchLinkDataSource,
    private val store: ScheduleVersionStore
) : ScheduleRepository {
    private val version = MutableStateFlow<Long?>(null)

    override val lastPublishedVersion: Flow<Long?> = flow {
        if (version.value == null) version.value = store.lastVersion()
        emitAll(version)
    }

    override suspend fun publish(): PublishOutcome {
        val last = store.lastSchedule()
        val next = (last?.version ?: 0L) + 1
        val taskList = tasks.tasks.first()
        val schedule = ScheduleBuilder.build(next, taskList, tasks.reminders.first(), settings.settings.first())
        if (last != null && last.copy(version = 0) == schedule.copy(version = 0)) return PublishOutcome.Unchanged
        return when (val result = source.publishSchedule(schedule)) {
            PublishResult.Sent -> {
                store.save(schedule)
                version.value = next
                PublishOutcome.Published(next)
            }

            is PublishResult.Failed -> PublishOutcome.Failed(result.reason)
        }
    }
}

class DefaultPromptRepository(
    private val source: WatchLinkDataSource,
    private val published: PublishedPrompts,
    private val clock: () -> Instant = { Instant.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : PromptRepository {
    override suspend fun publish(title: String, optionLabels: List<String>, expiresAt: Instant?): PromptPublishResult {
        val errors = buildSet {
            if (title.isBlank()) add(PromptError.BLANK_TITLE)
            if (title.length > Limits.MAX_TEXT_LENGTH) add(PromptError.TITLE_TOO_LONG)
            if (optionLabels.size < 2) add(PromptError.TOO_FEW_OPTIONS)
            if (optionLabels.size > Limits.MAX_OPTIONS) add(PromptError.TOO_MANY_OPTIONS)
            if (expiresAt != null && !expiresAt.isAfter(clock())) add(PromptError.EXPIRY_IN_PAST)
        }
        if (errors.isNotEmpty()) return PromptPublishResult.Rejected(errors)
        val options = optionLabels.mapIndexed { index, label -> PromptOption((index + 1).toString(), label) }
        val id = newId()
        return when (val result = source.publishPrompt(id, Prompt(title, options, expiresAt?.toEpochMilli()))) {
            PublishResult.Sent -> {
                published.add(id, expiresAt)
                PromptPublishResult.Published(id)
            }

            is PublishResult.Failed -> PromptPublishResult.Failed(result.reason)
        }
    }
}

class DefaultLinkStatusRepository(source: WatchLinkDataSource) : LinkStatusRepository {
    override val linkState = source.linkState
    override val lastSync = source.lastSync
}

class DefaultWatchStatusRepository(source: WatchLinkDataSource) : WatchStatusRepository {
    override val status = source.watchStatus
}

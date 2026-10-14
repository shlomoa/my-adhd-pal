package com.shlomoa.myadhdpal.phone.data.fake

import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.LinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.PromptError
import com.shlomoa.myadhdpal.phone.data.PromptPublishResult
import com.shlomoa.myadhdpal.phone.data.PromptRepository
import com.shlomoa.myadhdpal.phone.data.PublishOutcome
import com.shlomoa.myadhdpal.phone.data.PublishResult
import com.shlomoa.myadhdpal.phone.data.ScheduleBuilder
import com.shlomoa.myadhdpal.phone.data.ScheduleRepository
import com.shlomoa.myadhdpal.phone.data.ScheduleVersionStore
import com.shlomoa.myadhdpal.phone.data.SettingsDefaults
import com.shlomoa.myadhdpal.phone.data.SettingsError
import com.shlomoa.myadhdpal.phone.data.SettingsLimits
import com.shlomoa.myadhdpal.phone.data.SettingsRepository
import com.shlomoa.myadhdpal.phone.data.SettingsResult
import com.shlomoa.myadhdpal.phone.data.SettingsValidator
import com.shlomoa.myadhdpal.phone.data.StoreResult
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.protocol.Limits
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.PromptOption
import com.shlomoa.myadhdpal.protocol.Schedule
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class InMemoryTaskRepository : TaskRepository {
    private val taskState = MutableStateFlow<List<Task>>(emptyList())
    private val reminderState = MutableStateFlow<List<TaskReminder>>(emptyList())

    override val tasks: Flow<List<Task>> = taskState
    override val reminders: Flow<List<TaskReminder>> = reminderState.map(::sorted)

    override fun remindersOf(taskId: String): Flow<List<TaskReminder>> =
        reminderState.map { list -> sorted(list.filter { it.taskId == taskId }) }

    override suspend fun task(id: String): Task? = taskState.value.firstOrNull { it.id == id }

    override suspend fun save(task: Task) {
        val list = taskState.value
        taskState.value =
            if (list.any { it.id == task.id }) list.map { if (it.id == task.id) task else it } else list + task
    }

    override suspend fun save(reminder: TaskReminder) {
        val list = reminderState.value.filterNot { it.id == reminder.id }
        reminderState.value = list + reminder
    }

    override suspend fun delete(taskId: String) {
        taskState.value = taskState.value.filterNot { it.id == taskId }
        reminderState.value = reminderState.value.filterNot { it.taskId == taskId }
    }

    override suspend fun deleteReminder(reminderId: String) {
        reminderState.value = reminderState.value.filterNot { it.id == reminderId }
    }

    private fun sorted(list: List<TaskReminder>) = list.sortedWith(compareBy({ it.minuteOfDay }, { it.id }))
}

class InMemoryHistoryRepository : HistoryRepository {
    private val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override fun history(type: HistoryType?): Flow<List<HistoryEntry>> = entries.map { list ->
        list.filter {
            type == null || typeOf(it) == type
        }.sortedWith(compareByDescending<HistoryEntry> { it.at }.thenBy { it.id })
    }

    override suspend fun store(event: WatchEvent): StoreResult {
        val entry = toEntry(event)
        return when {
            entry == null -> StoreResult.Ignored

            entries.value.any { it.id == entry.id } -> StoreResult.Duplicate

            else -> {
                entries.value = entries.value + entry
                StoreResult.Stored
            }
        }
    }

    private fun toEntry(event: WatchEvent): HistoryEntry? = when (event) {
        is WatchEvent.AckReceived ->
            with(event.ack) { HistoryEntry.Acknowledged(event.id, at, action, reminderId, occurrenceAt) }

        is WatchEvent.SelectionReceived -> HistoryEntry.Selected(event.id, event.selection.at, event.selection.key)

        is WatchEvent.AlertReceived ->
            with(event.alert) { HistoryEntry.AlertRaised(event.id, at, metric, value, threshold) }

        is WatchEvent.HelloReceived -> null
    }

    private fun typeOf(entry: HistoryEntry) = when (entry) {
        is HistoryEntry.Acknowledged -> HistoryType.ACKNOWLEDGEMENT
        is HistoryEntry.Selected -> HistoryType.SELECTION
        is HistoryEntry.AlertRaised -> HistoryType.ALERT
    }
}

class InMemorySettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(SettingsDefaults.settings)

    override val settings: Flow<AppSettings> = state

    override suspend fun update(settings: AppSettings): SettingsResult {
        val errors = SettingsValidator.validate(settings)
        if (errors.isNotEmpty()) return SettingsResult.Invalid(errors)
        state.value = settings
        return SettingsResult.Saved
    }
}

class InMemoryScheduleVersionStore : ScheduleVersionStore {
    private var schedule: Schedule? = null

    override suspend fun lastVersion(): Long? = schedule?.version

    override suspend fun lastSchedule(): Schedule? = schedule

    override suspend fun save(schedule: Schedule) {
        this.schedule = schedule
    }
}

class InMemoryScheduleRepository(
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

class InMemoryPromptRepository(
    private val source: WatchLinkDataSource,
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
            PublishResult.Sent -> PromptPublishResult.Published(id)
            is PublishResult.Failed -> PromptPublishResult.Failed(result.reason)
        }
    }
}

class InMemoryLinkStatusRepository(source: WatchLinkDataSource) : LinkStatusRepository {
    override val linkState = source.linkState
    override val lastSync = source.lastSync
}

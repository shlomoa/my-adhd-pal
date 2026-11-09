package com.shlomoa.myadhdpal.phone.data.fake

import com.shlomoa.myadhdpal.phone.data.AppSettings
import com.shlomoa.myadhdpal.phone.data.DefaultLinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.DefaultPromptRepository
import com.shlomoa.myadhdpal.phone.data.DefaultScheduleRepository
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.LinkStatusRepository
import com.shlomoa.myadhdpal.phone.data.PromptError
import com.shlomoa.myadhdpal.phone.data.PromptPublishResult
import com.shlomoa.myadhdpal.phone.data.PromptRepository
import com.shlomoa.myadhdpal.phone.data.PublishOutcome
import com.shlomoa.myadhdpal.phone.data.PublishResult
import com.shlomoa.myadhdpal.phone.data.PublishedPrompt
import com.shlomoa.myadhdpal.phone.data.PublishedPrompts
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
            with(event.alert) { HistoryEntry.AlertRaised(event.id, at, metric, value, threshold, level, episodeId) }

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

typealias InMemoryScheduleRepository = DefaultScheduleRepository

typealias InMemoryPromptRepository = DefaultPromptRepository

typealias InMemoryLinkStatusRepository = DefaultLinkStatusRepository

class InMemoryPublishedPrompts : PublishedPrompts {
    private val entries = linkedMapOf<String, Instant?>()

    override suspend fun add(id: String, expiresAt: Instant?) {
        entries[id] = expiresAt
    }

    override suspend fun remove(id: String) {
        entries.remove(id)
    }

    override suspend fun all(): List<PublishedPrompt> = entries.map { PublishedPrompt(it.key, it.value) }
}

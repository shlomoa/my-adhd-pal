package com.shlomoa.myadhdpal.phone.data

import com.shlomoa.myadhdpal.protocol.LinkState
import com.shlomoa.myadhdpal.protocol.Schedule
import com.shlomoa.myadhdpal.protocol.WatchStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Tasks and their reminders. Flows re-emit on every change; the single source of truth is the database (step 2.1). */
interface TaskRepository {
    /** Tasks in the order they were created. */
    val tasks: Flow<List<Task>>

    /** All reminders, ordered by time of day then id. */
    val reminders: Flow<List<TaskReminder>>

    fun remindersOf(taskId: String): Flow<List<TaskReminder>>

    suspend fun task(id: String): Task?

    /** Inserts, or replaces the task with the same id. */
    suspend fun save(task: Task)

    /** Inserts, or replaces the reminder with the same id. */
    suspend fun save(reminder: TaskReminder)

    /** Deletes the task and its reminders. */
    suspend fun delete(taskId: String)

    suspend fun deleteReminder(reminderId: String)
}

/** Acknowledgements, selections and alerts from the watch. The watch never edits master state through this. */
interface HistoryRepository {
    /** Entries newest first (by `at`, then id), optionally of one type. */
    fun history(type: HistoryType? = null): Flow<List<HistoryEntry>>

    /** Stores the event once per id (idempotent). Hello events are [StoreResult.Ignored]. */
    suspend fun store(event: WatchEvent): StoreResult
}

interface SettingsRepository {
    /** [SettingsDefaults] until something valid is saved. */
    val settings: Flow<AppSettings>

    suspend fun update(settings: AppSettings): SettingsResult
}

/** Remembers what was last published, so versions stay monotonic across restarts (DataStore in step 2.5). */
interface ScheduleVersionStore {
    suspend fun lastVersion(): Long?

    suspend fun lastSchedule(): Schedule?

    suspend fun save(schedule: Schedule)
}

/** Builds the `/schedule` from tasks, reminders and settings and publishes it with a growing version. */
interface ScheduleRepository {
    val lastPublishedVersion: Flow<Long?>

    suspend fun publish(): PublishOutcome
}

interface PromptRepository {
    /** Validates, gives the prompt a fresh id, and publishes it. [optionLabels] get the keys "1", "2", ... */
    suspend fun publish(title: String, optionLabels: List<String>, expiresAt: Instant?): PromptPublishResult
}

/** The watch's last report of its baseline, threshold and heart-rate permission (protocol 1.2), or null before it. */
interface WatchStatusRepository {
    val status: Flow<WatchStatus?>
}

/** What the link-state screen shows. */
interface LinkStatusRepository {
    val linkState: Flow<LinkState>
    val lastSync: Flow<Instant?>
}

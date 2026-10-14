package com.shlomoa.myadhdpal.phone.data.db

import androidx.room.withTransaction
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.data.StoreResult
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.protocol.AckAction
import com.shlomoa.myadhdpal.protocol.Metric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskRepository(private val db: AppDatabase) : TaskRepository {
    override val tasks: Flow<List<Task>> = db.taskDao().observeAll().map { list -> list.map { Task(it.id, it.title) } }

    override val reminders: Flow<List<TaskReminder>> = db.reminderDao().observeAll().map { it.map(::toReminder) }

    override fun remindersOf(taskId: String): Flow<List<TaskReminder>> =
        db.reminderDao().observeFor(taskId).map { it.map(::toReminder) }

    override suspend fun task(id: String): Task? = db.taskDao().get(id)?.let { Task(it.id, it.title) }

    override suspend fun save(task: Task) {
        db.withTransaction {
            val existing = db.taskDao().get(task.id)
            val position = existing?.position ?: ((db.taskDao().maxPosition() ?: -1) + 1)
            db.taskDao().upsert(TaskEntity(task.id, task.title, position))
        }
    }

    override suspend fun save(reminder: TaskReminder) {
        db.reminderDao().upsert(
            ReminderEntity(reminder.id, reminder.taskId, reminder.minuteOfDay, reminder.days.joinToString(","))
        )
    }

    override suspend fun delete(taskId: String) = db.taskDao().delete(taskId)

    override suspend fun deleteReminder(reminderId: String) = db.reminderDao().delete(reminderId)

    private fun toReminder(e: ReminderEntity) =
        TaskReminder(e.id, e.taskId, e.minuteOfDay, e.days.split(",").filter { it.isNotEmpty() }.map { it.toInt() })
}

class RoomHistoryRepository(private val db: AppDatabase) : HistoryRepository {
    override fun history(type: HistoryType?): Flow<List<HistoryEntry>> =
        db.historyDao().observe(type?.let(::typeName)).map { list -> list.map(::toEntry) }

    override suspend fun store(event: WatchEvent): StoreResult {
        val entity = when (event) {
            is WatchEvent.AckReceived -> with(event.ack) {
                HistoryEntity.ack(event.id, at, action.name, reminderId, occurrenceAt)
            }

            is WatchEvent.SelectionReceived -> HistoryEntity.selection(
                event.id,
                event.selection.at,
                event.selection.key,
                db.promptDao().titleOf(event.id)
            )

            is WatchEvent.AlertReceived -> with(event.alert) {
                HistoryEntity.alert(event.id, at, metric.name, value, threshold)
            }

            is WatchEvent.HelloReceived -> return StoreResult.Ignored
        }
        return if (db.historyDao().insertIgnore(entity) == DUPLICATE) StoreResult.Duplicate else StoreResult.Stored
    }

    private fun typeName(type: HistoryType) = when (type) {
        HistoryType.ACKNOWLEDGEMENT -> HistoryEntity.TYPE_ACK
        HistoryType.SELECTION -> HistoryEntity.TYPE_SELECTION
        HistoryType.ALERT -> HistoryEntity.TYPE_ALERT
    }

    private fun toEntry(e: HistoryEntity): HistoryEntry = when (e.type) {
        HistoryEntity.TYPE_SELECTION -> HistoryEntry.Selected(e.id, e.at, e.key.orEmpty(), e.promptTitle)

        HistoryEntity.TYPE_ALERT ->
            HistoryEntry.AlertRaised(e.id, e.at, Metric.valueOf(e.metric.orEmpty()), e.value ?: 0.0, e.threshold ?: 0.0)

        else -> HistoryEntry.Acknowledged(
            e.id,
            e.at,
            AckAction.valueOf(e.action.orEmpty()),
            e.reminderId,
            e.occurrenceAt
        )
    }

    private companion object {
        const val DUPLICATE = -1L
    }
}

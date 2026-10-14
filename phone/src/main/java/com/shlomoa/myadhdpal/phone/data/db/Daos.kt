package com.shlomoa.myadhdpal.phone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY position, id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT MAX(position) FROM tasks")
    suspend fun maxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    /** Also deletes the task's reminders (foreign key cascade). */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY minuteOfDay, id")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE taskId = :taskId ORDER BY minuteOfDay, id")
    fun observeFor(taskId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prompt: PromptEntity)

    @Query("SELECT title FROM prompts WHERE id = :id")
    suspend fun titleOf(id: String): String?
}

@Dao
interface HistoryDao {
    /** Newest first; [type] null means all types. */
    @Query("SELECT * FROM history WHERE (:type IS NULL OR type = :type) ORDER BY at DESC, id ASC")
    fun observe(type: String?): Flow<List<HistoryEntity>>

    /** Returns the new row id, or -1 if a row with this id already exists (nothing changes). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: HistoryEntity): Long
}

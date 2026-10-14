package com.shlomoa.myadhdpal.phone.data.contract

import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every [TaskRepository] (the in-memory fake now, the Room-backed one in 2.5) must pass these. */
abstract class TaskRepositoryContract {
    abstract fun create(): TaskRepository

    private val weekdays = listOf(1, 2, 3, 4, 5)

    @Test
    fun `TC-S-092 task repository saves, lists, finds and updates tasks`() = runTest {
        val repo = create()
        assertEquals(emptyList<Task>(), repo.tasks.first())
        repo.save(Task("t1", "Take medication"))
        repo.save(Task("t2", "Stretch"))
        assertEquals(listOf("t1", "t2"), repo.tasks.first().map { it.id })
        assertEquals(Task("t1", "Take medication"), repo.task("t1"))
        assertNull(repo.task("missing"))
        repo.save(Task("t1", "Take medication at 8"))
        assertEquals(2, repo.tasks.first().size)
        assertEquals("Take medication at 8", repo.task("t1")?.title)
    }

    @Test
    fun `TC-S-092 task repository deleting a task deletes its reminders`() = runTest {
        val repo = create()
        repo.save(Task("t1", "A"))
        repo.save(Task("t2", "B"))
        repo.save(TaskReminder("r1", "t1", 8 * 60, weekdays))
        repo.save(TaskReminder("r2", "t2", 9 * 60, weekdays))
        repo.delete("t1")
        assertEquals(listOf("t2"), repo.tasks.first().map { it.id })
        assertEquals(listOf("r2"), repo.reminders.first().map { it.id })
        assertNull(repo.task("t1"))
    }

    @Test
    fun `TC-S-092 task repository reminders are saved, queried by task, ordered by time and deleted`() = runTest {
        val repo = create()
        repo.save(Task("t1", "A"))
        repo.save(Task("t2", "B"))
        repo.save(TaskReminder("r1", "t1", 20 * 60, weekdays))
        repo.save(TaskReminder("r2", "t1", 8 * 60, weekdays))
        repo.save(TaskReminder("r3", "t2", 12 * 60, weekdays))
        assertEquals(listOf("r2", "r1"), repo.remindersOf("t1").first().map { it.id })
        assertEquals(listOf("r2", "r3", "r1"), repo.reminders.first().map { it.id })
        repo.save(TaskReminder("r1", "t1", 7 * 60, listOf(6, 7)))
        assertEquals(listOf(7 * 60, 8 * 60), repo.remindersOf("t1").first().map { it.minuteOfDay })
        repo.deleteReminder("r2")
        assertEquals(listOf("r1"), repo.remindersOf("t1").first().map { it.id })
    }
}

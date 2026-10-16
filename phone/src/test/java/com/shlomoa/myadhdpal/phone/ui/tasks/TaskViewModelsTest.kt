package com.shlomoa.myadhdpal.phone.ui.tasks

import androidx.lifecycle.SavedStateHandle
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import com.shlomoa.myadhdpal.phone.ui.ListState
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import com.shlomoa.myadhdpal.phone.ui.SequentialIds
import com.shlomoa.myadhdpal.protocol.Limits
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Steps 4.1 and 4.6 for tasks: create, edit, validate, delete, list and its empty, loading and error states. */
class TaskViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = InMemoryTaskRepository()
    private val ids = SequentialIds("task")

    private fun editor(handle: SavedStateHandle = SavedStateHandle()) =
        TaskEditViewModel(handle, repo, newId = ids::next)

    @Test
    fun `TC-A-013 create a task`() = runTest {
        val vm = editor()
        vm.setTitle("Take the pills")
        vm.save()
        assertEquals(listOf(Task("task-1", "Take the pills")), repo.tasks.first())
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `TC-A-014 edit a task keeps its id`() = runTest {
        repo.save(Task("t-9", "Old"))
        val vm = editor(SavedStateHandle(mapOf("taskId" to "t-9")))
        assertEquals("Old", vm.uiState.value.title)
        vm.setTitle("New")
        vm.save()
        assertEquals(listOf(Task("t-9", "New")), repo.tasks.first())
    }

    @Test
    fun `TC-A-015 a blank title is rejected and nothing is saved`() = runTest {
        val vm = editor()
        vm.setTitle("   ")
        vm.save()
        assertEquals(TitleError.BLANK, vm.uiState.value.error)
        assertTrue(repo.tasks.first().isEmpty())
    }

    @Test
    fun `TC-A-016 a title over the length limit is rejected`() = runTest {
        val vm = editor()
        vm.setTitle("x".repeat(Limits.MAX_TEXT_LENGTH))
        vm.save()
        assertNull(vm.uiState.value.error)
        val tooLong = editor()
        tooLong.setTitle("x".repeat(Limits.MAX_TEXT_LENGTH + 1))
        tooLong.save()
        assertEquals(TitleError.TOO_LONG, tooLong.uiState.value.error)
        assertEquals(1, repo.tasks.first().size)
    }

    @Test
    fun `TC-A-017 delete a task and its reminders`() = runTest {
        repo.save(Task("t-1", "A"))
        repo.save(TaskReminder("r-1", "t-1", 480, listOf(1)))
        val list = TaskListViewModel(repo)
        list.delete("t-1")
        assertTrue(repo.tasks.first().isEmpty())
        assertTrue(repo.reminders.first().isEmpty())
        assertEquals(ListState.Empty, list.uiState.value)
    }

    @Test
    fun `TC-A-023 an unsaved edit survives recreation`() {
        val handle = SavedStateHandle()
        editor(handle).setTitle("Half typed")
        assertEquals("Half typed", editor(handle).uiState.value.title)
    }

    @Test
    fun `TC-A-053 an empty task list is the Empty state`() {
        assertEquals(ListState.Empty, TaskListViewModel(repo).uiState.value)
    }

    @Test
    fun `TC-A-057 the list is Loading until the first emission`() {
        val never = object : TaskRepository by repo {
            override val tasks: Flow<List<Task>> = flow { kotlinx.coroutines.awaitCancellation() }
        }
        assertEquals(ListState.Loading, TaskListViewModel(never).uiState.value)
    }

    @Test
    fun `TC-A-056 a failing source is the Failed state and retry reloads`() {
        var broken = true
        val flaky = object : TaskRepository by repo {
            override val tasks: Flow<List<Task>> = flow {
                if (broken) throw IOException("disk")
                emit(listOf(Task("t-1", "A")))
            }
        }
        val vm = TaskListViewModel(flaky)
        assertTrue(vm.uiState.value is ListState.Failed)
        broken = false
        vm.retry()
        assertEquals(ListState.Content(listOf(Task("t-1", "A"))), vm.uiState.value)
    }
}

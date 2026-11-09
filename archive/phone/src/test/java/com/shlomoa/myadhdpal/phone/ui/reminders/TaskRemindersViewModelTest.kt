package com.shlomoa.myadhdpal.phone.ui.reminders

import androidx.lifecycle.SavedStateHandle
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import com.shlomoa.myadhdpal.phone.ui.ListState
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Step 4.1: the reminders of one task, shown in its form. */
class TaskRemindersViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = InMemoryTaskRepository()
    private val vm by lazy { TaskRemindersViewModel(SavedStateHandle(mapOf("taskId" to "t-1")), repo) }

    @Test
    fun `TC-A-117 the reminders of the task are listed in time order and only its own`() = runTest {
        repo.save(Task("t-1", "Pills"))
        repo.save(Task("t-2", "Water"))
        repo.save(TaskReminder("late", "t-1", 20 * 60, listOf(1)))
        repo.save(TaskReminder("early", "t-1", 8 * 60, listOf(1)))
        repo.save(TaskReminder("other", "t-2", 9 * 60, listOf(1)))
        val content = vm.uiState.value as ListState.Content
        assertEquals(listOf("early", "late"), content.items.map { it.id })
    }

    @Test
    fun `TC-A-118 a task without reminders shows the Empty state`() = runTest {
        repo.save(Task("t-1", "Pills"))
        assertEquals(ListState.Empty, vm.uiState.value)
    }

    @Test
    fun `a new reminder appears and a deleted one goes`() = runTest {
        repo.save(Task("t-1", "Pills"))
        assertEquals(ListState.Empty, vm.uiState.value)
        repo.save(TaskReminder("r-1", "t-1", 480, listOf(1)))
        assertEquals(listOf("r-1"), (vm.uiState.value as ListState.Content).items.map { it.id })
        repo.deleteReminder("r-1")
        assertEquals(ListState.Empty, vm.uiState.value)
    }
}

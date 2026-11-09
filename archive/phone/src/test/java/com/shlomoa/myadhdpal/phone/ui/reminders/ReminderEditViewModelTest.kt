package com.shlomoa.myadhdpal.phone.ui.reminders

import androidx.lifecycle.SavedStateHandle
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.fake.InMemoryTaskRepository
import com.shlomoa.myadhdpal.phone.ui.MainDispatcherRule
import com.shlomoa.myadhdpal.phone.ui.SequentialIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Step 4.1 for reminders. Time is minutes after midnight; days are 1 (Monday) to 7 (Sunday). */
class ReminderEditViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = InMemoryTaskRepository()
    private val ids = SequentialIds("rem")

    private suspend fun editor(extra: Map<String, Any?> = emptyMap()): ReminderEditViewModel {
        repo.save(Task("t-1", "Pills"))
        return ReminderEditViewModel(SavedStateHandle(mapOf("taskId" to "t-1") + extra), repo, newId = ids::next)
    }

    @Test
    fun `TC-A-018 create a reminder`() = runTest {
        val vm = editor()
        vm.setMinuteOfDay(8 * 60)
        vm.setDays(listOf(1, 3, 5))
        vm.save()
        assertEquals(listOf(TaskReminder("rem-1", "t-1", 480, listOf(1, 3, 5))), repo.reminders.first())
    }

    @Test
    fun `TC-A-019 edit a reminder`() = runTest {
        repo.save(TaskReminder("r-7", "t-1", 480, listOf(1)))
        val vm = editor(mapOf("reminderId" to "r-7"))
        assertEquals(480, vm.uiState.value.minuteOfDay)
        vm.setMinuteOfDay(9 * 60)
        vm.save()
        assertEquals(listOf(TaskReminder("r-7", "t-1", 540, listOf(1))), repo.reminders.first())
    }

    @Test
    fun `TC-A-020 a reminder without a time is rejected`() = runTest {
        val vm = editor()
        vm.setDays(listOf(1))
        vm.save()
        assertEquals(setOf(ReminderError.TIME_MISSING), vm.uiState.value.errors)
        assertTrue(repo.reminders.first().isEmpty())
    }

    @Test
    fun `TC-A-021 a malformed repeat pattern is rejected`() = runTest {
        for (days in listOf(emptyList(), listOf(0), listOf(8), listOf(1, 1))) {
            val vm = editor()
            vm.setMinuteOfDay(480)
            vm.setDays(days)
            vm.save()
            assertEquals("days $days", setOf(ReminderError.DAYS_INVALID), vm.uiState.value.errors)
        }
        assertTrue(repo.reminders.first().isEmpty())
    }

    @Test
    fun `TC-A-022 delete a reminder`() = runTest {
        repo.save(TaskReminder("r-7", "t-1", 480, listOf(1)))
        editor(mapOf("reminderId" to "r-7")).delete()
        assertTrue(repo.reminders.first().isEmpty())
    }
}

package com.shlomoa.myadhdpal.phone.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.ui.ListState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Step 4.1: the reminders of the task named by the `taskId` argument, in time order, shown in the task's form. */
@HiltViewModel
class TaskRemindersViewModel @Inject constructor(handle: SavedStateHandle, tasks: TaskRepository) : ViewModel() {
    private val taskId: String = requireNotNull(handle["taskId"]) { "a task's reminders need a taskId" }

    val uiState: StateFlow<ListState<TaskReminder>> = tasks.remindersOf(taskId)
        .map<List<TaskReminder>, ListState<TaskReminder>> {
            if (it.isEmpty()) ListState.Empty else ListState.Content(it.sortedBy { r -> r.minuteOfDay })
        }
        .onStart { emit(ListState.Loading) }
        .catch { emit(ListState.Failed(it.message ?: "reminders unavailable")) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListState.Loading)
}

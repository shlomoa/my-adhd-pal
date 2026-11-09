package com.shlomoa.myadhdpal.phone.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.phone.ui.ListState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Step 4.1: the tasks, with its loading, empty and failed states. Deleting a task deletes its reminders. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModel @Inject constructor(private val tasks: TaskRepository) : ViewModel() {
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<ListState<Task>> = attempt
        .flatMapLatest {
            tasks.tasks
                .map<List<Task>, ListState<Task>> { if (it.isEmpty()) ListState.Empty else ListState.Content(it) }
                .onStart { emit(ListState.Loading) }
                .catch { emit(ListState.Failed(it.message ?: "tasks unavailable")) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListState.Loading)

    fun delete(taskId: String) {
        viewModelScope.launch { tasks.delete(taskId) }
    }

    /** Reads again after a failure. */
    fun retry() {
        attempt.value++
    }
}

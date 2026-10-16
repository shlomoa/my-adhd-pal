package com.shlomoa.myadhdpal.phone.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import com.shlomoa.myadhdpal.protocol.Limits
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TitleError { BLANK, TOO_LONG }

data class TaskEditUiState(val title: String, val error: TitleError?, val saved: Boolean)

private const val KEY_TASK_ID = "taskId"
private const val KEY_TITLE = "title"

/**
 * Step 4.1: create a task, or edit the one named by the `taskId` argument. The unsaved title lives in the
 * [SavedStateHandle], so it survives recreation. Saving trims the title and rejects a blank or over-long one.
 */
@HiltViewModel
class TaskEditViewModel(
    private val handle: SavedStateHandle,
    private val tasks: TaskRepository,
    private val newId: () -> String
) : ViewModel() {
    @Inject
    constructor(handle: SavedStateHandle, tasks: TaskRepository) : this(handle, tasks, { UUID.randomUUID().toString() })

    private val taskId: String? = handle[KEY_TASK_ID]
    private val state = MutableStateFlow(TaskEditUiState(handle[KEY_TITLE] ?: "", null, false))
    val uiState: StateFlow<TaskEditUiState> = state.asStateFlow()

    init {
        if (taskId != null && handle.get<String>(KEY_TITLE) == null) {
            viewModelScope.launch {
                tasks.task(taskId)?.let { task -> state.update { it.copy(title = task.title) } }
            }
        }
    }

    fun setTitle(title: String) {
        handle[KEY_TITLE] = title
        state.update { it.copy(title = title, error = null) }
    }

    fun save() {
        val title = state.value.title.trim()
        val error = when {
            title.isEmpty() -> TitleError.BLANK
            title.length > Limits.MAX_TEXT_LENGTH -> TitleError.TOO_LONG
            else -> null
        }
        if (error != null) {
            state.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            tasks.save(Task(taskId ?: newId(), title))
            state.update { it.copy(error = null, saved = true) }
        }
    }
}

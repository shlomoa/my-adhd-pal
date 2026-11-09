package com.shlomoa.myadhdpal.phone.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.data.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReminderError { TIME_MISSING, DAYS_INVALID }

/** [minuteOfDay] is minutes after midnight (0 to 1439) or null until chosen; [days] are 1 (Monday) to 7 (Sunday). */
data class ReminderEditUiState(
    val minuteOfDay: Int?,
    val days: List<Int>,
    val errors: Set<ReminderError>,
    val saved: Boolean
)

private const val KEY_TASK_ID = "taskId"
private const val KEY_REMINDER_ID = "reminderId"
private const val KEY_MINUTE = "minuteOfDay"
private const val KEY_DAYS = "days"
private const val MINUTES_PER_DAY = 24 * 60
private val EVERY_DAY = (1..7).toList()

/**
 * Step 4.1: create a reminder for the task named by `taskId`, or edit the one named by `reminderId`. The unsaved time
 * and days live in the [SavedStateHandle]. A new reminder repeats every day until the days are changed.
 */
@HiltViewModel
class ReminderEditViewModel(
    private val handle: SavedStateHandle,
    private val tasks: TaskRepository,
    private val newId: () -> String
) : ViewModel() {
    @Inject
    constructor(handle: SavedStateHandle, tasks: TaskRepository) : this(handle, tasks, { UUID.randomUUID().toString() })

    private val taskId: String = requireNotNull(handle[KEY_TASK_ID]) { "a reminder needs a taskId" }
    private val reminderId: String? = handle[KEY_REMINDER_ID]
    private val state = MutableStateFlow(
        ReminderEditUiState(
            minuteOfDay = handle[KEY_MINUTE],
            days = handle[KEY_DAYS] ?: EVERY_DAY,
            errors = emptySet(),
            saved = false
        )
    )
    val uiState: StateFlow<ReminderEditUiState> = state.asStateFlow()

    init {
        if (reminderId != null && handle.get<Int>(KEY_MINUTE) == null) {
            viewModelScope.launch {
                tasks.remindersOf(taskId).first().firstOrNull { it.id == reminderId }?.let { r ->
                    state.update { it.copy(minuteOfDay = r.minuteOfDay, days = r.days) }
                }
            }
        }
    }

    fun setMinuteOfDay(minuteOfDay: Int) {
        handle[KEY_MINUTE] = minuteOfDay
        state.update { it.copy(minuteOfDay = minuteOfDay, errors = it.errors - ReminderError.TIME_MISSING) }
    }

    fun setDays(days: List<Int>) {
        handle[KEY_DAYS] = days
        state.update { it.copy(days = days, errors = it.errors - ReminderError.DAYS_INVALID) }
    }

    fun save() {
        val current = state.value
        val errors = buildSet {
            if (current.minuteOfDay == null ||
                current.minuteOfDay !in 0 until MINUTES_PER_DAY
            ) {
                add(ReminderError.TIME_MISSING)
            }
            if (current.days.isEmpty() || current.days.any { it !in 1..7 } || current.days.distinct() != current.days) {
                add(ReminderError.DAYS_INVALID)
            }
        }
        if (errors.isNotEmpty()) {
            state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            tasks.save(
                TaskReminder(reminderId ?: newId(), taskId, requireNotNull(current.minuteOfDay), current.days.sorted())
            )
            state.update { it.copy(errors = emptySet(), saved = true) }
        }
    }

    fun delete() {
        val id = reminderId ?: return
        viewModelScope.launch {
            tasks.deleteReminder(id)
            state.update { it.copy(saved = true) }
        }
    }
}

package com.shlomoa.myadhdpal.phone.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.shlomoa.myadhdpal.phone.ui.reminders.ReminderEditScreen
import com.shlomoa.myadhdpal.phone.ui.reminders.ReminderEditViewModel
import com.shlomoa.myadhdpal.phone.ui.reminders.RemindersSection
import com.shlomoa.myadhdpal.phone.ui.reminders.TaskRemindersViewModel

private const val NEW = ""

/** Where the user is inside the Tasks tab: the list, a task's form, or one of its reminders' form. */
@Composable
fun TasksTab(
    list: TaskListViewModel,
    editorFor: (session: Int, taskId: String?) -> TaskEditViewModel,
    remindersFor: (taskId: String) -> TaskRemindersViewModel,
    reminderEditorFor: (session: Int, taskId: String, reminderId: String?) -> ReminderEditViewModel
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var session by rememberSaveable { mutableIntStateOf(0) }
    var reminderEditing by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderSession by rememberSaveable { mutableIntStateOf(0) }
    val task = editing
    val reminder = reminderEditing
    when {
        task == null -> TaskListScreen(
            state = list.uiState.collectAsState().value,
            onAdd = {
                session++
                editing = NEW
            },
            onOpen = {
                session++
                editing = it.id
            },
            onDelete = { list.delete(it.id) },
            onRetry = list::retry
        )

        task != NEW && reminder != null -> ReminderFormRoute(
            editor = remember(reminderSession) {
                reminderEditorFor(reminderSession, task, reminder.takeIf { it != NEW })
            },
            isNew = reminder == NEW,
            onClose = { reminderEditing = null }
        )

        else -> TaskFormRoute(
            editor = remember(session) { editorFor(session, task.takeIf { it != NEW }) },
            isNew = task == NEW,
            reminders = if (task == NEW) null else remember(task) { remindersFor(task) },
            onClose = { editing = null },
            onSavedNew = { savedId ->
                // A new task opens again as an existing one, so reminders can be added to it at once.
                session++
                editing = savedId
            },
            onAddReminder = {
                reminderSession++
                reminderEditing = NEW
            },
            onOpenReminder = {
                reminderSession++
                reminderEditing = it
            }
        )
    }
}

@Composable
private fun ReminderFormRoute(editor: ReminderEditViewModel, isNew: Boolean, onClose: () -> Unit) {
    val state by editor.uiState.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    ReminderEditScreen(
        state,
        onTime = editor::setMinuteOfDay,
        onDays = editor::setDays,
        onSave = editor::save,
        onDelete = if (isNew) null else editor::delete,
        onCancel = onClose
    )
}

@Suppress("LongParameterList")
@Composable
private fun TaskFormRoute(
    editor: TaskEditViewModel,
    isNew: Boolean,
    reminders: TaskRemindersViewModel?,
    onClose: () -> Unit,
    onSavedNew: (String) -> Unit,
    onAddReminder: () -> Unit,
    onOpenReminder: (String) -> Unit
) {
    val state by editor.uiState.collectAsState()
    LaunchedEffect(state.saved) {
        if (!state.saved) return@LaunchedEffect
        val savedId = state.taskId
        if (isNew && savedId != null) onSavedNew(savedId) else onClose()
    }
    TaskEditScreen(
        state,
        onTitle = editor::setTitle,
        onSave = editor::save,
        onCancel = onClose,
        below = {
            if (reminders != null) {
                RemindersSection(
                    reminders.uiState.collectAsState().value,
                    onAdd = onAddReminder,
                    onOpen = { onOpenReminder(it.id) }
                )
            }
        }
    )
}

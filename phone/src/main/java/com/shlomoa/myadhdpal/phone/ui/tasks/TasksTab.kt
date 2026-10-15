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

private const val NEW_TASK = ""

/**
 * The Tasks tab: the list, and the edit form for one task or a new one. [editorFor] returns the edit ViewModel of an
 * edit session (a new session gets a fresh ViewModel; the same session after recreation gets the same one). The
 * list-or-form choice survives recreation.
 */
@Composable
fun TasksTab(list: TaskListViewModel, editorFor: (session: Int, taskId: String?) -> TaskEditViewModel) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var session by rememberSaveable { mutableIntStateOf(0) }
    val open = editing
    if (open == null) {
        TaskListScreen(
            state = list.uiState.collectAsState().value,
            onAdd = {
                session++
                editing = NEW_TASK
            },
            onOpen = {
                session++
                editing = it.id
            },
            onDelete = { list.delete(it.id) },
            onRetry = list::retry
        )
    } else {
        val editor = remember(session) { editorFor(session, open.takeIf { it != NEW_TASK }) }
        val state by editor.uiState.collectAsState()
        LaunchedEffect(state.saved) { if (state.saved) editing = null }
        TaskEditScreen(state, onTitle = editor::setTitle, onSave = editor::save, onCancel = { editing = null })
    }
}

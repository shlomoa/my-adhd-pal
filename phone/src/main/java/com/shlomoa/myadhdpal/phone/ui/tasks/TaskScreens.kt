package com.shlomoa.myadhdpal.phone.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.phone.data.Task
import com.shlomoa.myadhdpal.phone.ui.ListState

/** Stateless task list with its loading, empty and failed states; [top] is room for the schedule bar. */
@Composable
fun TaskListScreen(
    state: ListState<Task>,
    onAdd: () -> Unit,
    onOpen: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit = {}
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        top()
        Button(onAdd) { Text(stringResource(R.string.task_add)) }
        when (state) {
            ListState.Loading -> Text(stringResource(R.string.state_loading))

            ListState.Empty -> Text(stringResource(R.string.task_empty))

            is ListState.Failed -> {
                Text(stringResource(R.string.state_error, state.reason))
                Button(onRetry) { Text(stringResource(R.string.state_retry)) }
            }

            is ListState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.items, key = { it.id }) { task ->
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onOpen(task) }, modifier = Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton(onClick = { onDelete(task) }) { Text(stringResource(R.string.task_delete)) }
                    }
                }
            }
        }
    }
}

/** Stateless create or edit form; [below] is room for the task's reminders once the task exists. */
@Composable
fun TaskEditScreen(
    state: TaskEditUiState,
    onTitle: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    below: @Composable () -> Unit = {}
) {
    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitle,
            label = { Text(stringResource(R.string.task_title_label)) },
            isError = state.error != null,
            supportingText = {
                state.error?.let {
                    Text(
                        stringResource(
                            if (it == TitleError.BLANK) R.string.task_error_blank else R.string.task_error_too_long
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onSave) { Text(stringResource(R.string.task_save)) }
            TextButton(onCancel) { Text(stringResource(R.string.task_cancel)) }
        }
        below()
    }
}

package com.shlomoa.myadhdpal.phone.ui.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.phone.data.PromptError
import com.shlomoa.myadhdpal.protocol.Limits

private val MIN_TOUCH_TARGET = 48.dp

private val EXPIRY_CHOICES = listOf<Pair<Int?, Int>>(
    null to R.string.ask_expiry_none,
    5 to R.string.ask_expiry_5,
    15 to R.string.ask_expiry_15,
    60 to R.string.ask_expiry_60
)

/** Actions of the Ask form, so the screen stays stateless. */
data class PromptComposeActions(
    val onTitle: (String) -> Unit,
    val onOption: (Int, String) -> Unit,
    val onAddOption: () -> Unit,
    val onRemoveOption: (Int) -> Unit,
    val onExpiry: (Int?) -> Unit,
    val onSend: () -> Unit
)

/** Stateless Ask form: the question, 2 to 6 answers, how long it stays open, and Send. */
@Composable
fun PromptComposeScreen(state: PromptComposeUiState, actions: PromptComposeActions, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = actions.onTitle,
            label = { Text(stringResource(R.string.ask_title_label)) },
            isError = PromptError.BLANK_TITLE in state.errors || PromptError.TITLE_TOO_LONG in state.errors,
            supportingText = {
                if (PromptError.BLANK_TITLE in state.errors) Text(stringResource(R.string.ask_error_title))
                if (PromptError.TITLE_TOO_LONG in state.errors) Text(stringResource(R.string.task_error_too_long))
            },
            modifier = Modifier.fillMaxWidth()
        )
        OptionFields(state, actions)
        ExpiryChoices(state.expiryMinutes, actions.onExpiry)
        Button(actions.onSend, enabled = state.status != PromptStatus.Sending) {
            Text(stringResource(R.string.ask_send))
        }
        SendStatus(state.status)
    }
}

@Composable
private fun OptionFields(state: PromptComposeUiState, actions: PromptComposeActions) {
    state.options.forEachIndexed { index, label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = label,
                onValueChange = { actions.onOption(index, it) },
                label = { Text(stringResource(R.string.ask_option_label, index + 1)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            if (state.options.size > PromptComposeUiState.MIN_OPTIONS) {
                TextButton(onClick = { actions.onRemoveOption(index) }) {
                    Text(stringResource(R.string.ask_option_remove))
                }
            }
        }
    }
    if (PromptError.TOO_FEW_OPTIONS in state.errors) {
        Text(stringResource(R.string.ask_error_options), color = MaterialTheme.colorScheme.error)
    }
    if (state.options.size < Limits.MAX_OPTIONS) {
        TextButton(actions.onAddOption) { Text(stringResource(R.string.ask_option_add)) }
    }
}

@Composable
private fun ExpiryChoices(selected: Int?, onChange: (Int?) -> Unit) {
    Text(stringResource(R.string.ask_expiry_title), style = MaterialTheme.typography.titleMedium)
    EXPIRY_CHOICES.forEach { (minutes, label) ->
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .selectable(selected = selected == minutes, onClick = { onChange(minutes) }, role = Role.RadioButton),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected == minutes, onClick = null)
            Text(stringResource(label), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SendStatus(status: PromptStatus) {
    when (status) {
        PromptStatus.Idle -> Unit

        PromptStatus.Sending -> Text(stringResource(R.string.schedule_sending))

        PromptStatus.Sent -> Text(stringResource(R.string.ask_sent))

        is PromptStatus.Failed -> Text(
            stringResource(R.string.schedule_failed, status.reason),
            color = MaterialTheme.colorScheme.error
        )
    }
}

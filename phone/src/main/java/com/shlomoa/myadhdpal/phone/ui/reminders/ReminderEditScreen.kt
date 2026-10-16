package com.shlomoa.myadhdpal.phone.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R

private const val MINUTES_PER_HOUR = 60

private val DAY_LABELS = listOf(
    R.string.day_mon,
    R.string.day_tue,
    R.string.day_wed,
    R.string.day_thu,
    R.string.day_fri,
    R.string.day_sat,
    R.string.day_sun
)

/**
 * Stateless create or edit form for one reminder. [onSave] is called after [onTime] with the time shown, so a time the
 * user never touched still counts as chosen. [onDelete] is null while creating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditScreen(
    state: ReminderEditUiState,
    onTime: (minuteOfDay: Int) -> Unit,
    onDays: (List<Int>) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = state.minuteOfDay ?: DEFAULT_MINUTE
    val picker = rememberTimePickerState(initial / MINUTES_PER_HOUR, initial % MINUTES_PER_HOUR, is24Hour = true)
    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.reminder_time_label), style = MaterialTheme.typography.titleMedium)
        TimeInput(picker)
        if (ReminderError.TIME_MISSING in state.errors) Text(stringResource(R.string.reminder_error_time))
        Text(stringResource(R.string.reminder_days_label), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DAY_LABELS.forEachIndexed { index, label ->
                val day = index + 1
                val selected = day in state.days
                FilterChip(
                    selected = selected,
                    onClick = { onDays(if (selected) state.days - day else (state.days + day).sorted()) },
                    label = { Text(stringResource(label)) }
                )
            }
        }
        if (ReminderError.DAYS_INVALID in state.errors) Text(stringResource(R.string.reminder_error_days))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                onTime(picker.hour * MINUTES_PER_HOUR + picker.minute)
                onSave()
            }) { Text(stringResource(R.string.task_save)) }
            TextButton(onCancel) { Text(stringResource(R.string.task_cancel)) }
            if (onDelete != null) TextButton(onDelete) { Text(stringResource(R.string.task_delete)) }
        }
    }
}

private const val DEFAULT_MINUTE = 9 * MINUTES_PER_HOUR

package com.shlomoa.myadhdpal.phone.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.phone.data.TaskReminder
import com.shlomoa.myadhdpal.phone.ui.ListState

private val DAY_NAMES = listOf(
    R.string.day_mon,
    R.string.day_tue,
    R.string.day_wed,
    R.string.day_thu,
    R.string.day_fri,
    R.string.day_sat,
    R.string.day_sun
)

@Composable
private fun DaysSummary.text(): String = when (this) {
    DaysSummary.EveryDay -> stringResource(R.string.reminders_every_day)
    DaysSummary.Weekdays -> stringResource(R.string.reminders_weekdays)
    is DaysSummary.Some -> days.map { stringResource(DAY_NAMES[it - 1]) }.joinToString(", ")
}

/** The reminders of a task with their states, "Add a reminder", and a tap on one to change or delete it. */
@Composable
fun RemindersSection(
    state: ListState<TaskReminder>,
    onAdd: () -> Unit,
    onOpen: (TaskReminder) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleMedium)
        when (state) {
            ListState.Loading -> Text(stringResource(R.string.state_loading))

            ListState.Empty -> Text(stringResource(R.string.reminders_empty))

            is ListState.Failed -> Text(stringResource(R.string.state_error, state.reason))

            is ListState.Content -> state.items.forEach { reminder ->
                val summary = reminder.summary()
                TextButton(onClick = { onOpen(reminder) }) {
                    Text(stringResource(R.string.reminders_row, summary.time, summary.days.text()))
                }
            }
        }
        Button(onAdd) { Text(stringResource(R.string.reminders_add)) }
    }
}

package com.shlomoa.myadhdpal.phone.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import com.shlomoa.myadhdpal.phone.data.HistoryType
import com.shlomoa.myadhdpal.phone.ui.ListState
import com.shlomoa.myadhdpal.protocol.AckAction
import java.time.Instant

@StringRes
private fun AckAction.label(): Int = when (this) {
    AckAction.DONE -> R.string.history_ack_done
    AckAction.SNOOZE -> R.string.history_ack_snooze
    AckAction.START_2_MIN -> R.string.history_ack_start
    AckAction.DISMISS -> R.string.history_ack_dismiss
}

@Composable
private fun HistoryEntry.summary(): String = when (this) {
    is HistoryEntry.Acknowledged -> stringResource(R.string.history_acknowledged, stringResource(action.label()))
    is HistoryEntry.Selected -> stringResource(R.string.history_selected, promptTitle ?: "", key)
    is HistoryEntry.AlertRaised -> stringResource(R.string.history_alert, value, threshold)
}

/** Stateless. [onFilter] receives null for "all". */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onFilter: (HistoryType?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.filter == null, { onFilter(null) }, { Text(stringResource(R.string.history_filter_all)) })
            FilterChip(
                state.filter == HistoryType.ACKNOWLEDGEMENT,
                { onFilter(HistoryType.ACKNOWLEDGEMENT) },
                { Text(stringResource(R.string.history_filter_acks)) }
            )
            FilterChip(
                state.filter == HistoryType.SELECTION,
                { onFilter(HistoryType.SELECTION) },
                { Text(stringResource(R.string.history_filter_selections)) }
            )
            FilterChip(
                state.filter == HistoryType.ALERT,
                { onFilter(HistoryType.ALERT) },
                { Text(stringResource(R.string.history_filter_alerts)) }
            )
        }
        when (val entries = state.entries) {
            ListState.Loading -> Text(stringResource(R.string.state_loading))

            ListState.Empty -> Text(stringResource(R.string.history_empty))

            is ListState.Failed -> {
                Text(stringResource(R.string.state_error, entries.reason))
                Button(onRetry) { Text(stringResource(R.string.state_retry)) }
            }

            is ListState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries.items, key = { it.id }) { entry ->
                    Column {
                        Text(entry.summary(), style = MaterialTheme.typography.bodyLarge)
                        Text(Instant.ofEpochMilli(entry.at).toString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

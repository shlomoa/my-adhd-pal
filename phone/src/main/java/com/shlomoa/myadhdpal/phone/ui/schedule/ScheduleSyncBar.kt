package com.shlomoa.myadhdpal.phone.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R

/** Which schedule the watch has, a button to send it again, and what the last send did. */
@Composable
fun ScheduleSyncBar(state: ScheduleSyncUiState, onSend: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            when (val version = state.lastVersion) {
                null -> stringResource(R.string.schedule_never)
                else -> stringResource(R.string.schedule_version, version)
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onSend, enabled = state.status != SyncStatus.Sending) {
            Text(stringResource(R.string.schedule_send))
        }
        when (val status = state.status) {
            SyncStatus.Idle -> Unit

            SyncStatus.Sending -> Text(stringResource(R.string.schedule_sending))

            is SyncStatus.Sent -> Text(stringResource(R.string.schedule_sent, status.version))

            SyncStatus.UpToDate -> Text(stringResource(R.string.schedule_up_to_date))

            is SyncStatus.Failed -> Text(
                stringResource(R.string.schedule_failed, status.reason),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
